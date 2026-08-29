package com.kinn.app.service;

import com.kinn.app.dto.*;
import com.kinn.app.entity.*;
import com.kinn.app.repository.AiMealSuggestionItemRepository;
import com.kinn.app.repository.AiMealSuggestionRepository;
import com.kinn.app.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 「前日の食事記録」+「健康情報」+「食の好み」から、翌日(=提案対象日)の朝食・昼食・夕食を
 * AIが提案するためのService(Phase 3)。
 *
 * ・AI API(Anthropic Messages API)が利用可能な場合はそちらを優先する({@link AiMealClient}参照)。
 * ・AI未設定/失敗時は本クラス内のルールベース提案ロジックに自動フォールバックするため、
 *   AI APIキーが無い環境でも本機能自体は必ず動作する(健康管理アプリ全体を壊さない)。
 * ・既存の {@link MealService}(食事記録)・{@link HealthScoreService}を
 *   読み取り専用で利用するだけで、既存機能には一切変更を加えない。
 * ・健康情報(健康プロフィール・体調チェック・健康スコア)は、外部AI APIへは一切送信しない
 *   (セキュリティレビュー対応。buildPrompt javadoc参照)。あくまでサーバー内部の
 *   ルールベース提案(analyzeGaps)でのみ利用し、外部送信するのは前日の食事記録と
 *   ユーザーが登録した食の好み(好き嫌い・アレルギー・食事制限・予算・調理時間)のみ。
 */
@Service
public class MealRecommendationService {

    // ------------------------------------------------------------------
    // 献立候補カタログ(ルールベース提案用)。
    // 実運用ではレシピDBや外部サービスに差し替え可能なよう、本クラス内に閉じている。
    // ------------------------------------------------------------------

    private record MenuCandidate(
            String name, String ingredients, int calories, double proteinG, double fatG, double carbsG,
            int cookingMinutes, Set<String> tags) {
    }

    private static final List<MenuCandidate> BREAKFAST_CATALOG = List.of(
            new MenuCandidate("鮭おにぎりと具だくさん味噌汁", "鮭, 白米, 味噌, 豆腐, わかめ, 野菜", 420, 20, 8, 60, 15,
                    Set.of("protein", "vegetable", "light")),
            new MenuCandidate("全粒粉トーストとゆで卵、野菜スープ", "全粒粉パン, 卵, 野菜, コンソメ", 380, 18, 14, 42, 12,
                    Set.of("protein", "vegetable", "fiber")),
            new MenuCandidate("ヨーグルトとバナナ、ミックスナッツ", "ヨーグルト, バナナ, ナッツ", 320, 12, 14, 38, 5,
                    Set.of("light", "fiber")),
            new MenuCandidate("納豆ご飯と焼き海苔、ほうれん草のお浸し", "納豆, 白米, ほうれん草, 海苔", 450, 18, 10, 68, 10,
                    Set.of("protein", "vegetable", "fiber")),
            new MenuCandidate("鶏むね肉と野菜のスープ、雑穀パン", "鶏むね肉, 野菜, 雑穀パン", 400, 24, 9, 48, 15,
                    Set.of("protein", "vegetable", "light")),
            new MenuCandidate("白米と味噌汁、青菜のお浸し", "白米, 味噌汁, 青菜", 360, 10, 6, 66, 10,
                    Set.of("balanced", "light"))
    );

    private static final List<MenuCandidate> LUNCH_CATALOG = List.of(
            new MenuCandidate("鶏むね肉のサラダと玄米、ゆで卵、野菜スープ", "鶏むね肉, レタス, トマト, 玄米, 卵, 野菜", 620, 38, 16, 70, 20,
                    Set.of("protein", "vegetable", "fiber")),
            new MenuCandidate("豚肉と野菜の炒め物、雑穀ご飯", "豚肉, キャベツ, ピーマン, にんじん, 雑穀米", 680, 30, 20, 78, 20,
                    Set.of("protein", "vegetable", "hearty")),
            new MenuCandidate("サバの塩焼き定食(ご飯・味噌汁・小鉢)", "サバ, 白米, 味噌, 野菜", 650, 32, 22, 68, 20,
                    Set.of("protein", "balanced")),
            new MenuCandidate("豆腐と野菜のあんかけ丼", "豆腐, 野菜, 白米, 生姜", 580, 22, 12, 82, 18,
                    Set.of("vegetable", "fiber", "light")),
            new MenuCandidate("牛肉と野菜のプレート(玄米)", "牛赤身肉, ブロッコリー, パプリカ, 玄米", 700, 36, 22, 72, 22,
                    Set.of("protein", "vegetable", "hearty")),
            new MenuCandidate("白米とみそ汁、鶏そぼろ丼", "鶏ひき肉, 白米, みそ汁, 野菜", 640, 28, 15, 80, 18,
                    Set.of("balanced", "protein"))
    );

    private static final List<MenuCandidate> DINNER_CATALOG = List.of(
            new MenuCandidate("白身魚の野菜蒸し、ご飯、豆腐とわかめの味噌汁、ほうれん草のお浸し",
                    "白身魚, キャベツ, にんじん, ご飯, 豆腐, わかめ, ほうれん草", 620, 34, 14, 78, 25,
                    Set.of("protein", "vegetable", "fiber", "light")),
            new MenuCandidate("鶏むね肉の野菜炒め定食", "鶏むね肉, ピーマン, もやし, にんじん, ご飯", 640, 36, 16, 74, 20,
                    Set.of("protein", "vegetable")),
            new MenuCandidate("豆腐ハンバーグと温野菜、ご飯少なめ", "豆腐, ひき肉, 玉ねぎ, ブロッコリー, にんじん, ご飯", 600, 28, 18, 64, 25,
                    Set.of("protein", "vegetable", "light")),
            new MenuCandidate("鮭の塩焼きと根菜の煮物、ご飯", "鮭, 大根, にんじん, ごぼう, ご飯", 630, 30, 16, 80, 25,
                    Set.of("protein", "vegetable", "fiber")),
            new MenuCandidate("豚しゃぶサラダと雑穀ご飯", "豚肉, レタス, 水菜, ポン酢, 雑穀ご飯", 590, 32, 14, 68, 20,
                    Set.of("protein", "vegetable", "light")),
            new MenuCandidate("ご飯とみそ汁、青菜と厚揚げの炒め物", "白米, みそ汁, 青菜, 厚揚げ", 610, 24, 16, 76, 20,
                    Set.of("balanced", "vegetable"))
    );

    // 栄養素の目安しきい値(1日の目標に対する簡易判定。医学的診断目的ではなく、
    // あくまで一般的な栄養バランスの目安として利用する)
    private static final double PROTEIN_LOW_THRESHOLD_G = 60;
    private static final double FIBER_LOW_THRESHOLD_G = 15;
    private static final int CALORIES_HIGH_THRESHOLD_KCAL = 2200;
    private static final int CALORIES_LOW_THRESHOLD_KCAL = 1200;

    private final MealService mealService;
    private final HealthScoreService healthScoreService;
    private final HealthCheckRepository healthCheckRepository;
    private final UserFoodPreferenceService preferenceService;
    private final AiMealSuggestionRepository suggestionRepository;
    private final AiMealSuggestionItemRepository itemRepository;
    private final AiMealClient aiMealClient;

    public MealRecommendationService(
            MealService mealService,
            HealthScoreService healthScoreService,
            HealthCheckRepository healthCheckRepository,
            UserFoodPreferenceService preferenceService,
            AiMealSuggestionRepository suggestionRepository,
            AiMealSuggestionItemRepository itemRepository,
            AiMealClient aiMealClient) {
        this.mealService = mealService;
        this.healthScoreService = healthScoreService;
        this.healthCheckRepository = healthCheckRepository;
        this.preferenceService = preferenceService;
        this.suggestionRepository = suggestionRepository;
        this.itemRepository = itemRepository;
        this.aiMealClient = aiMealClient;
    }

    // ------------------------------------------------------------------
    // 公開API
    // ------------------------------------------------------------------

    /** 指定日の最新の提案を取得する。まだ1件も無ければ新規生成する(画面初回表示用) */
    @Transactional
    public AiMealSuggestionDto getOrCreateLatest(String employeeId, LocalDate date) {
        return suggestionRepository.findFirstByEmployeeIdAndSuggestionDateOrderByAttemptNoDesc(employeeId, date)
                .map(this::toDto)
                .orElseGet(() -> generateAndPersist(employeeId, date, 1));
    }

    /** 「🔄 別の献立を提案」。同じ献立を繰り返さないよう、新しい試行として生成する */
    @Transactional
    public AiMealSuggestionDto regenerate(String employeeId, LocalDate date) {
        int nextAttempt = (int) suggestionRepository.countByEmployeeIdAndSuggestionDate(employeeId, date) + 1;
        return generateAndPersist(employeeId, date, nextAttempt);
    }

    /** 「⭐ この献立を保存」。AI提案履歴上でお気に入りとして分かるようにする */
    @Transactional
    public AiMealSuggestionDto markSaved(String employeeId, Long suggestionId) {
        AiMealSuggestion entity = suggestionRepository.findByIdAndEmployeeId(suggestionId, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の献立提案が見つかりません"));
        entity.setSaved(true);
        return toDto(suggestionRepository.save(entity));
    }

    /**
     * AIが提案した1食分を、正式な食事記録として登録する(ユーザーが明示的に押した場合のみ)。
     * 既存の{@link MealService}の上書き保存ロジックをそのまま利用するため、
     * 食事記録テーブル(meal_record)の扱いは既存機能と完全に共通。
     */
    @Transactional
    public MealRecordDto registerToMealRecord(String employeeId, Long suggestionId, MealType mealType) {
        AiMealSuggestion suggestion = suggestionRepository.findByIdAndEmployeeId(suggestionId, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の献立提案が見つかりません"));
        AiMealSuggestionItem item = itemRepository.findBySuggestionId(suggestionId).stream()
                .filter(i -> i.getMealType() == mealType)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の食事区分の提案が見つかりません"));

        MealRecordDto dto = MealRecordDto.builder()
                .mealDate(suggestion.getSuggestionDate())
                .mealType(item.getMealType())
                .dishName(item.getDishName())
                .items(item.getIngredients())
                .calories(item.getCalories())
                .proteinG(item.getProteinG())
                .fatG(item.getFatG())
                .carbsG(item.getCarbsG())
                .memo("AI献立提案より登録")
                .build();
        return mealService.save(employeeId, dto);
    }

    /** AI献立履歴(指定期間、日付ごとに1件: 保存済みがあればそれ、無ければ最新の提案) */
    @Transactional(readOnly = true)
    public List<AiMealSuggestionDto> getHistory(String employeeId, LocalDate from, LocalDate to) {
        List<AiMealSuggestion> all = suggestionRepository
                .findByEmployeeIdAndSuggestionDateBetweenOrderBySuggestionDateAscAttemptNoAsc(employeeId, from, to);

        Map<LocalDate, AiMealSuggestion> byDate = new LinkedHashMap<>();
        for (AiMealSuggestion s : all) {
            AiMealSuggestion current = byDate.get(s.getSuggestionDate());
            if (current == null
                    || (s.isSaved() && !current.isSaved())
                    || (s.isSaved() == current.isSaved() && s.getAttemptNo() >= current.getAttemptNo())) {
                byDate.put(s.getSuggestionDate(), s);
            }
        }

        List<Long> ids = byDate.values().stream().map(AiMealSuggestion::getId).toList();
        Map<Long, List<AiMealSuggestionItem>> itemsBySuggestion = itemRepository.findBySuggestionIdIn(ids).stream()
                .collect(Collectors.groupingBy(AiMealSuggestionItem::getSuggestionId));

        return byDate.values().stream()
                .map(s -> toDto(s, itemsBySuggestion.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    // ------------------------------------------------------------------
    // 生成本体
    // ------------------------------------------------------------------

    private AiMealSuggestionDto generateAndPersist(String employeeId, LocalDate date, int attemptNo) {
        LocalDate prevDate = date.minusDays(1);
        DayMealsDto prevDay = mealService.getDay(employeeId, prevDate);
        boolean hasPrevData = !prevDay.getBreakfast().isEmpty() || !prevDay.getLunch().isEmpty()
                || !prevDay.getDinner().isEmpty() || !prevDay.getSnacks().isEmpty();

        // 健康プロフィール(身長・体重・BMI等)は、以前はAIへの提案プロンプトにも含めていたが、
        // 外部AI API(Anthropic)への要配慮個人情報の送信を避けるためプロンプトからは除外した
        // (buildPrompt参照。docs/health-audit-legal-checklist.md 9.のセキュリティレビュー対応)。
        // 健康スコア(score)・体調チェック(todaysCheck)は、AIへは送らずルールベース側の
        // タグ選定(analyzeGaps、サーバー内部で完結し外部送信しない)にのみ引き続き利用する。
        HealthScoreDto score = healthScoreService.getScoreForDate(employeeId, date);
        UserFoodPreferenceDto preference = preferenceService.getPreference(employeeId);
        HealthCheck todaysCheck = healthCheckRepository.findByEmployeeIdAndCheckDate(employeeId, date)
                .or(() -> healthCheckRepository.findByEmployeeIdAndCheckDate(employeeId, prevDate))
                .orElse(null);

        Set<String> wantedTags = analyzeGaps(prevDay, hasPrevData, todaysCheck, score);
        boolean isTired = todaysCheck != null
                && todaysCheck.getFatigueLevel() != null && todaysCheck.getFatigueLevel() >= 4;

        List<String> allergens = splitList(preference.getAllergies());
        List<String> dislikes = splitList(preference.getDislikedFoods());

        AiSuggestionSource source;
        AiMealSuggestionItemDto breakfast;
        AiMealSuggestionItemDto lunch;
        AiMealSuggestionItemDto dinner;
        String summaryNote;

        Optional<AiRawSuggestionDto> aiResult = Optional.empty();
        if (aiMealClient.isConfigured()) {
            String prompt = buildPrompt(prevDay, hasPrevData, preference, date);
            aiResult = aiMealClient.generate(prompt);
        }

        if (aiResult.isPresent() && isUsableAiResult(aiResult.get())) {
            AiRawSuggestionDto raw = aiResult.get();
            source = AiSuggestionSource.AI;
            summaryNote = (raw.getSummary() == null || raw.getSummary().isBlank())
                    ? defaultSummaryNote(hasPrevData) : raw.getSummary();
            // アレルギー食材が含まれていた食事だけは安全のためルールベースの提案に差し替える(多重防御)
            breakfast = safeAiOrFallback(raw.getBreakfast(), MealType.BREAKFAST, allergens,
                    () -> pickRuleBased(BREAKFAST_CATALOG, wantedTags, allergens, dislikes, attemptNo,
                            MealType.BREAKFAST, hasPrevData, isTired));
            lunch = safeAiOrFallback(raw.getLunch(), MealType.LUNCH, allergens,
                    () -> pickRuleBased(LUNCH_CATALOG, wantedTags, allergens, dislikes, attemptNo,
                            MealType.LUNCH, hasPrevData, isTired));
            dinner = safeAiOrFallback(raw.getDinner(), MealType.DINNER, allergens,
                    () -> pickRuleBased(DINNER_CATALOG, wantedTags, allergens, dislikes, attemptNo,
                            MealType.DINNER, hasPrevData, isTired));
        } else {
            source = AiSuggestionSource.RULE_BASED;
            summaryNote = defaultSummaryNote(hasPrevData);
            breakfast = pickRuleBased(BREAKFAST_CATALOG, wantedTags, allergens, dislikes, attemptNo,
                    MealType.BREAKFAST, hasPrevData, isTired);
            lunch = pickRuleBased(LUNCH_CATALOG, wantedTags, allergens, dislikes, attemptNo,
                    MealType.LUNCH, hasPrevData, isTired);
            dinner = pickRuleBased(DINNER_CATALOG, wantedTags, allergens, dislikes, attemptNo,
                    MealType.DINNER, hasPrevData, isTired);
        }

        AiMealSuggestion entity = AiMealSuggestion.builder()
                .employeeId(employeeId)
                .suggestionDate(date)
                .basedOnDate(prevDate)
                .attemptNo(attemptNo)
                .source(source)
                .summaryNote(summaryNote)
                .saved(false)
                .build();
        entity = suggestionRepository.save(entity);

        List<AiMealSuggestionItem> items = List.of(
                toItemEntity(entity.getId(), breakfast),
                toItemEntity(entity.getId(), lunch),
                toItemEntity(entity.getId(), dinner)
        );
        itemRepository.saveAll(items);

        return AiMealSuggestionDto.builder()
                .id(entity.getId())
                .suggestionDate(date)
                .basedOnDate(prevDate)
                .attemptNo(attemptNo)
                .source(source.name())
                .aiAvailable(aiMealClient.isConfigured())
                .summaryNote(summaryNote)
                .saved(false)
                .generatedAt(entity.getCreatedAt())
                .breakfast(breakfast)
                .lunch(lunch)
                .dinner(dinner)
                .build();
    }

    private String defaultSummaryNote(boolean hasPrevData) {
        return hasPrevData
                ? "昨日の食事内容と栄養バランスを分析し、今日のおすすめメニューを提案します。"
                : "食事記録が少ないため、一般的なバランスを考慮して献立を提案します。";
    }

    // ------------------------------------------------------------------
    // 栄養ギャップ分析(ルールベース提案・AIプロンプトの両方で利用)
    // ------------------------------------------------------------------

    private Set<String> analyzeGaps(DayMealsDto prevDay, boolean hasPrevData, HealthCheck check, HealthScoreDto score) {
        Set<String> wanted = new LinkedHashSet<>();
        if (!hasPrevData) {
            wanted.add("balanced");
            return wanted;
        }
        MealNutritionSummaryDto n = prevDay.getNutrition();
        Double protein = n != null ? n.getTotalProteinG() : null;
        Double fiber = n != null ? n.getTotalFiberG() : null;
        Integer calories = n != null ? n.getTotalCalories() : null;

        if (protein == null || protein < PROTEIN_LOW_THRESHOLD_G) wanted.add("protein");
        if (fiber == null || fiber < FIBER_LOW_THRESHOLD_G) {
            wanted.add("vegetable");
            wanted.add("fiber");
        }
        if (calories != null && calories > CALORIES_HIGH_THRESHOLD_KCAL) wanted.add("light");
        if (calories != null && calories > 0 && calories < CALORIES_LOW_THRESHOLD_KCAL) wanted.add("hearty");

        if (check != null && check.getFatigueLevel() != null && check.getFatigueLevel() >= 4) {
            wanted.add("light");
        }
        if (score != null && score.isHasData() && score.getTotalScore() < 40) {
            wanted.add("light");
        }
        if (wanted.isEmpty()) wanted.add("balanced");
        return wanted;
    }

    // ------------------------------------------------------------------
    // ルールベース提案
    // ------------------------------------------------------------------

    private AiMealSuggestionItemDto pickRuleBased(
            List<MenuCandidate> catalog, Set<String> wantedTags, List<String> allergens, List<String> dislikes,
            int attemptNo, MealType mealType, boolean hasPrevData, boolean isTired) {

        List<MenuCandidate> candidates = filterCandidates(catalog, allergens, dislikes);

        List<MenuCandidate> sorted = candidates.stream()
                .sorted(Comparator.<MenuCandidate>comparingInt(
                        c -> -countMatches(c.tags(), wantedTags)))
                .toList();

        // 「別の献立を提案」のたびに候補を巡回させることで、同じ献立を繰り返さないようにする
        MenuCandidate chosen = sorted.get((attemptNo - 1) % sorted.size());

        String reason = buildReason(chosen, wantedTags, hasPrevData, mealType, isTired);

        return AiMealSuggestionItemDto.builder()
                .mealType(mealType)
                .dishName(chosen.name())
                .ingredients(chosen.ingredients())
                .calories(chosen.calories())
                .proteinG(chosen.proteinG())
                .fatG(chosen.fatG())
                .carbsG(chosen.carbsG())
                .cookingMinutes(chosen.cookingMinutes())
                .reason(reason)
                .build();
    }

    private List<MenuCandidate> filterCandidates(List<MenuCandidate> catalog, List<String> allergens, List<String> dislikes) {
        List<MenuCandidate> withoutAllergensAndDislikes = catalog.stream()
                .filter(c -> !containsAny(c.name() + " " + c.ingredients(), allergens))
                .filter(c -> !containsAny(c.name() + " " + c.ingredients(), dislikes))
                .toList();
        if (!withoutAllergensAndDislikes.isEmpty()) return withoutAllergensAndDislikes;

        // 苦手な食材まで除外すると候補が無くなる場合は、アレルギーだけは必ず守った上で苦手食材は妥協する
        List<MenuCandidate> withoutAllergensOnly = catalog.stream()
                .filter(c -> !containsAny(c.name() + " " + c.ingredients(), allergens))
                .toList();
        if (!withoutAllergensOnly.isEmpty()) return withoutAllergensOnly;

        // ここまでで候補が空になるのは、カタログ内の一般的な食材(米・味噌など)までアレルギー指定された
        // 極端なケースのみ。安全側に倒し、空リストで落ちるよりは全候補を返す(手動での最終確認を促す)。
        return catalog;
    }

    private int countMatches(Set<String> tags, Set<String> wanted) {
        int count = 0;
        for (String t : wanted) if (tags.contains(t)) count++;
        return count;
    }

    private String buildReason(MenuCandidate chosen, Set<String> wantedTags, boolean hasPrevData,
                                MealType mealType, boolean isTired) {
        List<String> reasons = new ArrayList<>();
        if (!hasPrevData) {
            reasons.add("食事記録が少ないため、一般的な栄養バランスを考慮した提案です。");
        } else {
            if (wantedTags.contains("protein") && chosen.tags().contains("protein")) {
                reasons.add("昨日はたんぱく質が不足気味だったため、たんぱく質をしっかり補える構成にしています。");
            }
            if ((wantedTags.contains("vegetable") || wantedTags.contains("fiber"))
                    && (chosen.tags().contains("vegetable") || chosen.tags().contains("fiber"))) {
                reasons.add("昨日は野菜や食物繊維が少なかったため、野菜を多く使ったメニューにしています。");
            }
            if (wantedTags.contains("light") && chosen.tags().contains("light")) {
                reasons.add("昨日はカロリーが多め、または疲労・ストレスが高めのようなので、軽めで消化にやさしい内容にしています。");
            }
            if (wantedTags.contains("hearty") && chosen.tags().contains("hearty")) {
                reasons.add("昨日は食事量が少なめだったため、しっかり食べられる構成にしています。");
            }
            if (reasons.isEmpty()) {
                reasons.add("昨日の食事内容も踏まえ、栄養バランスを整えるメニューにしています。");
            }
        }
        if (isTired && mealType == MealType.BREAKFAST) {
            reasons.add("疲労やストレスが高めのようなので、無理なく食べられる内容を意識しています。");
        }
        if (mealType == MealType.DINNER) {
            reasons.add("1日の栄養バランス全体を考慮した夕食です。");
        }
        return String.join(" ", reasons);
    }

    // ------------------------------------------------------------------
    // AI連携(プロンプト構築・レスポンス検証)
    // ------------------------------------------------------------------

    /**
     * 外部AI API(Anthropic)へ送るプロンプトを組み立てる。
     *
     * 身長・体重・BMI・睡眠時間・疲労度・ストレス度・健康スコアといった健康情報は、
     * 以前はここに含めていたが、要配慮個人情報を外部APIへ送信することになるため
     * セキュリティレビューを受けて除外した(docs/health-audit-legal-checklist.md 9.参照)。
     * これらの値は引き続きサーバー内部の analyzeGaps(ルールベースのタグ選定)でのみ使う。
     *
     * アレルギー・食事制限・好き嫌い等の「食の好み」は、レビュー結果として送信を継続する
     * (アレルギー食材を誤って提案しないための安全上の理由。実際に提案結果へ含まれてしまった
     * 場合も、safeAiOrFallbackがサーバー側で二重にチェックして安全な代替案へ差し替える)。
     */
    private String buildPrompt(DayMealsDto prevDay, boolean hasPrevData, UserFoodPreferenceDto preference,
                                LocalDate targetDate) {
        MealNutritionSummaryDto n = prevDay.getNutrition();
        StringBuilder sb = new StringBuilder();
        sb.append("あなたは栄養バランスに配慮した家庭向けの献立を提案する管理栄養士アシスタントです。\n");
        sb.append("病気の診断や治療は絶対に行わず、一般的な健康維持・栄養バランスの観点のみで献立を提案してください。\n\n");

        sb.append("【前日の食事】\n");
        sb.append("朝食: ").append(mealSummaryText(prevDay.getBreakfast())).append("\n");
        sb.append("昼食: ").append(mealSummaryText(prevDay.getLunch())).append("\n");
        sb.append("夕食: ").append(mealSummaryText(prevDay.getDinner())).append("\n");
        if (!prevDay.getSnacks().isEmpty()) {
            sb.append("間食: ").append(mealSummaryText(prevDay.getSnacks())).append("\n");
        }
        if (!hasPrevData) {
            sb.append("(前日の食事記録はありません。一般的な栄養バランスを考慮して提案してください)\n");
        }
        sb.append("\n【前日の栄養情報(入力があった分のみの合計。未入力の項目は不明)】\n");
        sb.append("カロリー: ").append(valueOrUnknown(n != null ? n.getTotalCalories() : null, "kcal")).append("\n");
        sb.append("たんぱく質: ").append(valueOrUnknown(n != null ? n.getTotalProteinG() : null, "g")).append("\n");
        sb.append("脂質: ").append(valueOrUnknown(n != null ? n.getTotalFatG() : null, "g")).append("\n");
        sb.append("炭水化物: ").append(valueOrUnknown(n != null ? n.getTotalCarbsG() : null, "g")).append("\n");
        sb.append("食物繊維: ").append(valueOrUnknown(n != null ? n.getTotalFiberG() : null, "g")).append("\n");
        sb.append("塩分: ").append(valueOrUnknown(n != null ? n.getTotalSaltG() : null, "g")).append("\n");

        sb.append("\n【ユーザー設定】\n");
        sb.append("好きな食材: ").append(valueOrUnknown(preference.getFavoriteFoods(), "")).append("\n");
        sb.append("苦手な食材: ").append(valueOrUnknown(preference.getDislikedFoods(), "")).append("\n");
        sb.append("アレルギー(必ず除外すること): ").append(valueOrUnknown(preference.getAllergies(), "")).append("\n");
        sb.append("食事制限: ").append(valueOrUnknown(preference.getDietaryRestrictions(), "")).append("\n");
        sb.append("予算: ").append(valueOrUnknown(preference.getBudgetYen(), "円")).append("\n");
        sb.append("調理時間: ").append(valueOrUnknown(preference.getCookingMinutes(), "分")).append("\n");

        sb.append("\n以上を踏まえ、").append(targetDate).append("の朝食・昼食・夕食を提案してください。\n");
        sb.append("前日と同じ料理を単純に繰り返すのではなく、前日の栄養バランスの偏りを補い、");
        sb.append("3食全体としてバランスが取れるように考えてください(例: 朝は軽め、昼はしっかり、夜は野菜・たんぱく質中心、など)。\n");
        sb.append("アレルギー食材は絶対に使用しないでください。\n\n");
        sb.append("回答は必ず、次のJSON形式のみで返してください(説明文やコードフェンスは不要です):\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"提案全体の前提・総評\",\n");
        sb.append("  \"breakfast\": {\"name\": \"料理名\", \"ingredients\": \"使用食材(カンマ区切り)\", \"reason\": \"おすすめ理由\", ");
        sb.append("\"calories\": 数値, \"proteinG\": 数値, \"fatG\": 数値, \"carbsG\": 数値, \"cookingMinutes\": 数値},\n");
        sb.append("  \"lunch\": {同じ形式},\n");
        sb.append("  \"dinner\": {同じ形式}\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String mealSummaryText(List<MealRecordDto> records) {
        if (records == null || records.isEmpty()) return "記録なし";
        return records.stream()
                .map(r -> {
                    String label = (r.getDishName() != null && !r.getDishName().isBlank()) ? r.getDishName() : r.getItems();
                    return label == null || label.isBlank() ? "(内容未記入)" : label;
                })
                .collect(Collectors.joining(" / "));
    }

    private String valueOrUnknown(Object value, String unit) {
        if (value == null || "".equals(value)) return "不明";
        return value + unit;
    }

    private boolean isUsableAiResult(AiRawSuggestionDto raw) {
        return isUsableMeal(raw.getBreakfast()) && isUsableMeal(raw.getLunch()) && isUsableMeal(raw.getDinner());
    }

    private boolean isUsableMeal(AiRawSuggestionDto.Meal meal) {
        return meal != null && meal.getName() != null && !meal.getName().isBlank();
    }

    /** AIの提案がアレルギー食材を含んでいないか最終チェックし、含んでいればルールベース提案に差し替える(多重防御) */
    private AiMealSuggestionItemDto safeAiOrFallback(AiRawSuggestionDto.Meal meal, MealType mealType,
                                                       List<String> allergens,
                                                       java.util.function.Supplier<AiMealSuggestionItemDto> fallback) {
        String text = (meal.getName() == null ? "" : meal.getName())
                + " " + (meal.getIngredients() == null ? "" : meal.getIngredients());
        if (containsAny(text, allergens)) {
            return fallback.get();
        }
        return AiMealSuggestionItemDto.builder()
                .mealType(mealType)
                .dishName(meal.getName())
                .ingredients(meal.getIngredients())
                .calories(meal.getCalories())
                .proteinG(meal.getProteinG())
                .fatG(meal.getFatG())
                .carbsG(meal.getCarbsG())
                .cookingMinutes(meal.getCookingMinutes())
                .reason(meal.getReason())
                .build();
    }

    // ------------------------------------------------------------------
    // 小さなユーティリティ
    // ------------------------------------------------------------------

    private List<String> splitList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split("[,、,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (keywords.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.JAPAN);
        return keywords.stream().anyMatch(k -> !k.isBlank() && lower.contains(k.toLowerCase(Locale.JAPAN)));
    }

    private AiMealSuggestionItem toItemEntity(Long suggestionId, AiMealSuggestionItemDto dto) {
        return AiMealSuggestionItem.builder()
                .suggestionId(suggestionId)
                .mealType(dto.getMealType())
                .dishName(dto.getDishName())
                .ingredients(dto.getIngredients())
                .calories(dto.getCalories())
                .proteinG(dto.getProteinG())
                .fatG(dto.getFatG())
                .carbsG(dto.getCarbsG())
                .cookingMinutes(dto.getCookingMinutes())
                .reason(dto.getReason())
                .build();
    }

    private AiMealSuggestionDto toDto(AiMealSuggestion entity) {
        List<AiMealSuggestionItem> items = itemRepository.findBySuggestionId(entity.getId());
        return toDto(entity, items);
    }

    private AiMealSuggestionDto toDto(AiMealSuggestion entity, List<AiMealSuggestionItem> items) {
        AiMealSuggestionItemDto breakfast = toItemDto(items, MealType.BREAKFAST);
        AiMealSuggestionItemDto lunch = toItemDto(items, MealType.LUNCH);
        AiMealSuggestionItemDto dinner = toItemDto(items, MealType.DINNER);

        return AiMealSuggestionDto.builder()
                .id(entity.getId())
                .suggestionDate(entity.getSuggestionDate())
                .basedOnDate(entity.getBasedOnDate())
                .attemptNo(entity.getAttemptNo())
                .source(entity.getSource().name())
                .aiAvailable(aiMealClient.isConfigured())
                .summaryNote(entity.getSummaryNote())
                .saved(entity.isSaved())
                .generatedAt(entity.getCreatedAt())
                .breakfast(breakfast)
                .lunch(lunch)
                .dinner(dinner)
                .build();
    }

    private AiMealSuggestionItemDto toItemDto(List<AiMealSuggestionItem> items, MealType type) {
        return items.stream()
                .filter(i -> i.getMealType() == type)
                .findFirst()
                .map(i -> AiMealSuggestionItemDto.builder()
                        .mealType(i.getMealType())
                        .dishName(i.getDishName())
                        .ingredients(i.getIngredients())
                        .calories(i.getCalories())
                        .proteinG(i.getProteinG())
                        .fatG(i.getFatG())
                        .carbsG(i.getCarbsG())
                        .cookingMinutes(i.getCookingMinutes())
                        .reason(i.getReason())
                        .build())
                .orElse(null);
    }
}
