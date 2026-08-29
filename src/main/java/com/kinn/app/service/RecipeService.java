package com.kinn.app.service;

import com.kinn.app.dto.AiRawRecipeDto;
import com.kinn.app.dto.RecipeDto;
import com.kinn.app.dto.RecipeGenerateRequestDto;
import com.kinn.app.dto.RecipeIngredientDto;
import com.kinn.app.dto.RecipeSummaryDto;
import com.kinn.app.entity.CookingMethod;
import com.kinn.app.entity.Recipe;
import com.kinn.app.entity.RecipeIngredient;
import com.kinn.app.entity.RecipeStep;
import com.kinn.app.repository.RecipeIngredientRepository;
import com.kinn.app.repository.RecipeRepository;
import com.kinn.app.repository.RecipeStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * レシピ・調理方法・調理手順の管理(Phase 4)+ AI献立提案からのレシピ生成・食事記録との連携(Phase 5)。
 * すべての取得・更新・削除で employeeId による所有者チェックを行い、
 * 他ユーザーのレシピには一切到達できないようにする(既存の食事管理APIと同じ方式)。
 */
@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final AiMealClient aiMealClient;
    private final MealService mealService;

    public RecipeService(RecipeRepository recipeRepository,
                          RecipeIngredientRepository ingredientRepository,
                          RecipeStepRepository stepRepository,
                          AiMealClient aiMealClient,
                          MealService mealService) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.aiMealClient = aiMealClient;
        this.mealService = mealService;
    }

    @Transactional(readOnly = true)
    public List<RecipeSummaryDto> getAll(String employeeId) {
        return recipeRepository.findByEmployeeIdOrderByIdDesc(employeeId).stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeDto getById(String employeeId, Long id) {
        Recipe recipe = findOwned(employeeId, id);
        List<RecipeIngredient> ingredients = ingredientRepository.findByRecipeIdOrderByDisplayOrderAsc(id);
        List<RecipeStep> steps = stepRepository.findByRecipeIdOrderByStepNoAsc(id);
        return toDetailDto(recipe, ingredients, steps);
    }

    /** 新規登録・更新の両方をこのメソッドで行う(dto.idの有無で判定。既存の食事記録・AI献立と同じ方式) */
    @Transactional
    public RecipeDto save(String employeeId, RecipeDto dto) {
        Recipe entity = (dto.getId() != null)
                ? findOwned(employeeId, dto.getId())
                : new Recipe();

        entity.setEmployeeId(employeeId);
        entity.setName(dto.getName());
        entity.setCookingMethod(dto.getCookingMethod());
        entity.setPrepMinutes(dto.getPrepMinutes());
        entity.setCookMinutes(dto.getCookMinutes());
        entity.setDifficulty(dto.getDifficulty());
        entity.setEquipment(dto.getEquipment());
        entity.setMemo(dto.getMemo());
        entity.setCalories(dto.getCalories());
        entity.setProteinG(dto.getProteinG());
        entity.setFatG(dto.getFatG());
        entity.setCarbsG(dto.getCarbsG());
        entity.setSaltG(dto.getSaltG());
        entity.setStorageFridge(dto.getStorageFridge());
        entity.setStorageFreezer(dto.getStorageFreezer());
        entity.setStorageDays(dto.getStorageDays());
        entity.setReheatMethod(dto.getReheatMethod());

        Recipe saved = recipeRepository.save(entity);

        // 材料・手順は毎回「全削除→再登録」で置き換える(件数・順序が変わっても整合性が崩れないシンプルな方式)
        ingredientRepository.deleteByRecipeId(saved.getId());
        stepRepository.deleteByRecipeId(saved.getId());

        List<RecipeIngredient> ingredients = new ArrayList<>();
        List<RecipeIngredientDto> ingredientDtos = dto.getIngredients() == null ? List.of() : dto.getIngredients();
        int order = 0;
        for (RecipeIngredientDto i : ingredientDtos) {
            if (i.getName() == null || i.getName().isBlank()) continue;
            ingredients.add(RecipeIngredient.builder()
                    .recipeId(saved.getId())
                    .name(i.getName())
                    .quantity(i.getQuantity())
                    .unit(i.getUnit())
                    .displayOrder(order++)
                    .build());
        }
        ingredientRepository.saveAll(ingredients);

        List<RecipeStep> steps = new ArrayList<>();
        List<String> stepTexts = dto.getSteps() == null ? List.of() : dto.getSteps();
        int stepNo = 1;
        for (String s : stepTexts) {
            if (s == null || s.isBlank()) continue;
            steps.add(RecipeStep.builder().recipeId(saved.getId()).stepNo(stepNo++).description(s).build());
        }
        stepRepository.saveAll(steps);

        return toDetailDto(saved, ingredients, steps);
    }

    @Transactional
    public void delete(String employeeId, Long id) {
        Recipe recipe = findOwned(employeeId, id);
        ingredientRepository.deleteByRecipeId(recipe.getId());
        stepRepository.deleteByRecipeId(recipe.getId());
        recipeRepository.delete(recipe);
        // 過去の食事記録(meal_record.recipe_id)はあえて追随更新しない。削除後にその記録の
        // 「レシピを見る」を押した際は、同名レシピが無ければ「未作成」扱いに戻り、
        // ⑧の案内(レシピを作成)が再度出るだけで、画面がエラーになることはない。
    }

    // ------------------------------------------------------------------
    // AI献立提案・食事記録との連携(⑥⑦⑧⑨⑫。Phase 5)
    // ------------------------------------------------------------------

    /**
     * 「レシピを見る/レシピを作成」の実体。
     * <ol>
     *   <li>まず自分の既存レシピに同名(大文字小文字区別なし)のものがあれば、それをそのまま使う
     *       (⑨AIへ毎回問い合わせない・重複レシピを作らない)。</li>
     *   <li>無ければAIにレシピ生成を依頼し、成功すれば新規レシピとして保存する(⑥⑧⑨)。
     *       AI未設定・生成失敗・不正な形式のレスポンスはいずれもユーザーに分かるエラーとして返す
     *       (㉒㉓。画面を無反応にしない)。</li>
     *   <li>mealRecordIdが指定されていれば、解決/生成したレシピをその食事記録に紐付ける(⑫)。
     *       他ユーザーの食事記録IDが渡されても、MealServiceの所有者チェックにより無視される(㉔)。</li>
     * </ol>
     */
    @Transactional
    public RecipeDto generateForDish(String employeeId, RecipeGenerateRequestDto request) {
        String dishName = request.getDishName() == null ? "" : request.getDishName().trim();
        if (dishName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "料理名は必須です");
        }

        Optional<Recipe> existing = recipeRepository.findFirstByEmployeeIdAndNameIgnoreCaseOrderByIdDesc(employeeId, dishName);
        Recipe recipe;
        if (existing.isPresent()) {
            recipe = existing.get();
        } else {
            recipe = generateAndSaveViaAi(employeeId, dishName, request.getIngredientsHint());
        }

        if (request.getMealRecordId() != null) {
            mealService.linkRecipe(employeeId, request.getMealRecordId(), recipe.getId());
        }

        List<RecipeIngredient> ingredients = ingredientRepository.findByRecipeIdOrderByDisplayOrderAsc(recipe.getId());
        List<RecipeStep> steps = stepRepository.findByRecipeIdOrderByStepNoAsc(recipe.getId());
        return toDetailDto(recipe, ingredients, steps);
    }

    private Recipe generateAndSaveViaAi(String employeeId, String dishName, String ingredientsHint) {
        if (!aiMealClient.isConfigured()) {
            // ⑰ APIキー未設定の原因をサーバーログに残す(app.ai.meal.api-key ⇐ 環境変数
            // ANTHROPIC_API_KEY が空のまま。AnthropicMealClientのコンストラクタ参照)。
            // アプリの起動自体は妨げない(@Valueのデフォルト値が空文字のため必須プロパティにしていない)。
            log.warn("AIレシピ生成: AI APIの設定が確認できません(APIキー未設定)。employeeId={}, dishName={}。"
                            + "環境変数 ANTHROPIC_API_KEY を設定してください。",
                    employeeId, dishName);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "現在AIによるレシピ生成はご利用いただけません。手動でレシピを登録してください。");
        }

        String prompt = buildRecipePrompt(dishName, ingredientsHint);
        Optional<AiRawRecipeDto> result = aiMealClient.generateRecipe(prompt);
        if (result.isEmpty()) {
            // 具体的な失敗理由(タイムアウト・認証エラー・通信エラー・JSON解析失敗等)は
            // AnthropicMealClient側で既にログ済み(⑯)。ここでは「AI呼び出し自体が失敗した」ことだけ記録する。
            log.warn("AIレシピ生成: AI APIからレシピを取得できませんでした。employeeId={}, dishName={}", employeeId, dishName);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "レシピの自動生成に失敗しました。しばらくしてからもう一度お試しください。");
        }
        if (!isUsable(result.get())) {
            log.warn("AIレシピ生成: AIのレスポンスに材料・手順が含まれていませんでした(不完全なレスポンス)。"
                    + "employeeId={}, dishName={}", employeeId, dishName);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "レシピの自動生成に失敗しました。しばらくしてからもう一度お試しください。");
        }
        AiRawRecipeDto raw = result.get();

        try {
            Recipe entity = Recipe.builder()
                    .employeeId(employeeId)
                    .name(dishName)
                    .cookingMethod(parseCookingMethod(raw.getCookingMethod()))
                    .prepMinutes(raw.getPrepMinutes())
                    .cookMinutes(raw.getCookMinutes())
                    .difficulty(clampDifficulty(raw.getDifficulty()))
                    .calories(raw.getCalories())
                    .proteinG(raw.getProteinG())
                    .fatG(raw.getFatG())
                    .carbsG(raw.getCarbsG())
                    .saltG(raw.getSaltG())
                    .build();
            Recipe saved = recipeRepository.save(entity);

            List<RecipeIngredient> ingredients = new ArrayList<>();
            int order = 0;
            for (AiRawRecipeDto.Ingredient i : raw.getIngredients()) {
                if (i == null || i.getName() == null || i.getName().isBlank()) continue;
                ingredients.add(RecipeIngredient.builder()
                        .recipeId(saved.getId())
                        .name(i.getName())
                        .quantity(i.getQuantity())
                        .unit(i.getUnit())
                        .displayOrder(order++)
                        .build());
            }
            ingredientRepository.saveAll(ingredients);

            List<RecipeStep> steps = new ArrayList<>();
            int stepNo = 1;
            for (String s : raw.getSteps()) {
                if (s == null || s.isBlank()) continue;
                steps.add(RecipeStep.builder().recipeId(saved.getId()).stepNo(stepNo++).description(s).build());
            }
            stepRepository.saveAll(steps);

            log.info("AIレシピ生成に成功しました: employeeId={}, recipeId={}, dishName={}", employeeId, saved.getId(), dishName);
            return saved;
        } catch (Exception e) {
            // ⑯ DB保存エラーを他の失敗要因と区別してログに残す(AI呼び出し自体は成功していたケース)
            log.error("AIレシピ生成: AIからの取得には成功しましたが、PostgreSQLへの保存に失敗しました。"
                    + "employeeId={}, dishName={}", employeeId, dishName, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "レシピの保存に失敗しました。時間をおいて再度お試しください。");
        }
    }

    /** AIレスポンスの最低限の妥当性チェック(㉓)。材料か手順のどちらかが1件も無ければ使い物にならない */
    private boolean isUsable(AiRawRecipeDto raw) {
        boolean hasIngredients = raw.getIngredients() != null && raw.getIngredients().stream()
                .anyMatch(i -> i != null && i.getName() != null && !i.getName().isBlank());
        boolean hasSteps = raw.getSteps() != null && raw.getSteps().stream()
                .anyMatch(s -> s != null && !s.isBlank());
        return hasIngredients && hasSteps;
    }

    /** AIが想定外の文字列を返しても落ちないよう、対応するenumが無ければOTHERにする(㉓) */
    private CookingMethod parseCookingMethod(String raw) {
        if (raw == null || raw.isBlank()) return CookingMethod.OTHER;
        try {
            return CookingMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CookingMethod.OTHER;
        }
    }

    /** 1〜3の範囲外(nullや異常値)ならnull(未設定)にする(㉓) */
    private Integer clampDifficulty(Integer raw) {
        if (raw == null || raw < 1 || raw > 3) return null;
        return raw;
    }

    private String buildRecipePrompt(String dishName, String ingredientsHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("あなたは家庭料理のレシピを作成する管理栄養士アシスタントです。\n");
        sb.append("次の料理の家庭向けレシピを作成してください。\n\n");
        sb.append("料理名: ").append(dishName).append("\n");
        if (ingredientsHint != null && !ingredientsHint.isBlank()) {
            sb.append("使用したい食材の例(参考。これ以外の食材を使ってもよい): ").append(ingredientsHint).append("\n");
        }
        sb.append("\n回答は必ず、次のJSON形式のみで返してください(説明文やコードフェンスは不要です):\n");
        sb.append("{\n");
        sb.append("  \"name\": \"料理名\",\n");
        sb.append("  \"cookingMethod\": \"次のいずれか1つを英語表記で: GRILL, STIR_FRY, SIMMER, STEAM, BOIL, DEEP_FRY, MICROWAVE, RICE_COOKER, TOSS, RAW, OTHER\",\n");
        sb.append("  \"prepMinutes\": 準備時間(分, 数値),\n");
        sb.append("  \"cookMinutes\": 調理時間(分, 数値),\n");
        sb.append("  \"difficulty\": 難易度(1〜3の整数。1=簡単, 3=難しい),\n");
        sb.append("  \"calories\": 1人前のおおよそのカロリー(kcal, 数値),\n");
        sb.append("  \"proteinG\": たんぱく質(g, 数値),\n");
        sb.append("  \"fatG\": 脂質(g, 数値),\n");
        sb.append("  \"carbsG\": 炭水化物(g, 数値),\n");
        sb.append("  \"saltG\": 食塩相当量(g, 数値),\n");
        sb.append("  \"ingredients\": [{\"name\": \"材料名\", \"quantity\": 数量(数値。目安が無ければ null), \"unit\": \"単位(g,個,大さじ 等)\"}, ...],\n");
        sb.append("  \"steps\": [\"手順1の説明\", \"手順2の説明\", ...]\n");
        sb.append("}\n");
        sb.append("材料は1人前の分量で、調理手順は番号を振らず本文だけを配列の順序で返してください。\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------

    private Recipe findOwned(String employeeId, Long id) {
        return recipeRepository.findByIdAndEmployeeId(id, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のレシピが見つかりません"));
    }

    private Integer totalMinutes(Integer prep, Integer cook) {
        if (prep == null && cook == null) return null;
        return (prep == null ? 0 : prep) + (cook == null ? 0 : cook);
    }

    private RecipeSummaryDto toSummaryDto(Recipe r) {
        return RecipeSummaryDto.builder()
                .id(r.getId())
                .name(r.getName())
                .cookingMethodLabel(r.getCookingMethod() == null ? null : r.getCookingMethod().getLabel())
                .prepMinutes(r.getPrepMinutes())
                .cookMinutes(r.getCookMinutes())
                .totalMinutes(totalMinutes(r.getPrepMinutes(), r.getCookMinutes()))
                .difficulty(r.getDifficulty())
                .equipment(r.getEquipment())
                .calories(r.getCalories())
                .build();
    }

    private RecipeDto toDetailDto(Recipe r, List<RecipeIngredient> ingredients, List<RecipeStep> steps) {
        return RecipeDto.builder()
                .id(r.getId())
                .name(r.getName())
                .cookingMethod(r.getCookingMethod())
                .cookingMethodLabel(r.getCookingMethod() == null ? null : r.getCookingMethod().getLabel())
                .prepMinutes(r.getPrepMinutes())
                .cookMinutes(r.getCookMinutes())
                .totalMinutes(totalMinutes(r.getPrepMinutes(), r.getCookMinutes()))
                .difficulty(r.getDifficulty())
                .equipment(r.getEquipment())
                .memo(r.getMemo())
                .calories(r.getCalories())
                .proteinG(r.getProteinG())
                .fatG(r.getFatG())
                .carbsG(r.getCarbsG())
                .saltG(r.getSaltG())
                .storageFridge(r.getStorageFridge())
                .storageFreezer(r.getStorageFreezer())
                .storageDays(r.getStorageDays())
                .reheatMethod(r.getReheatMethod())
                .ingredients(ingredients.stream()
                        .map(i -> RecipeIngredientDto.builder().name(i.getName()).quantity(i.getQuantity()).unit(i.getUnit()).build())
                        .toList())
                .steps(steps.stream().map(RecipeStep::getDescription).toList())
                .build();
    }
}
