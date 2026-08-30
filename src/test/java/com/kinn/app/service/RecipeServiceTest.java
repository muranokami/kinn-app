package com.kinn.app.service;

import com.kinn.app.dto.AiRawRecipeDto;
import com.kinn.app.dto.RecipeDto;
import com.kinn.app.dto.RecipeGenerateRequestDto;
import com.kinn.app.entity.CookingMethod;
import com.kinn.app.entity.MealRecord;
import com.kinn.app.entity.Recipe;
import com.kinn.app.repository.MealRecordRepository;
import com.kinn.app.repository.RecipeIngredientRepository;
import com.kinn.app.repository.RecipeRepository;
import com.kinn.app.repository.RecipeStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RecipeService の「レシピを見る/レシピを作成」(⑥⑦⑧⑨⑫)を検証するユニットテスト。
 * DBは使わない。MealServiceは実体(MealRecordRepositoryをモック化)を使い、
 * linkRecipeが本当に呼ばれるところまで確認する(食事記録との連携㉒相当)。
 */
class RecipeServiceTest {

    private RecipeRepository recipeRepository;
    private RecipeIngredientRepository ingredientRepository;
    private RecipeStepRepository stepRepository;
    private AiMealClient aiMealClient;
    private MealRecordRepository mealRecordRepository;
    private RecipeService service;

    private static final String EMPLOYEE_ID = "1|yamada";

    @BeforeEach
    void setUp() {
        recipeRepository = mock(RecipeRepository.class);
        ingredientRepository = mock(RecipeIngredientRepository.class);
        stepRepository = mock(RecipeStepRepository.class);
        aiMealClient = mock(AiMealClient.class);
        mealRecordRepository = mock(MealRecordRepository.class);
        MealService mealService = new MealService(mealRecordRepository);
        service = new RecipeService(recipeRepository, ingredientRepository, stepRepository, aiMealClient, mealService);

        when(ingredientRepository.findByRecipeIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());
        when(stepRepository.findByRecipeIdOrderByStepNoAsc(any())).thenReturn(List.of());
    }

    private AiRawRecipeDto rawRecipe() {
        AiRawRecipeDto raw = new AiRawRecipeDto();
        raw.setName("鶏肉の照り焼き");
        raw.setCookingMethod("GRILL");
        raw.setPrepMinutes(10);
        raw.setCookMinutes(20);
        raw.setDifficulty(2);
        raw.setCalories(450);
        raw.setProteinG(28.0);
        raw.setFatG(18.0);
        raw.setCarbsG(20.0);
        raw.setSaltG(2.1);
        raw.setIngredients(List.of(
                ingredient("鶏もも肉", 150.0, "g"),
                ingredient("醤油", 1.0, "大さじ")));
        raw.setSteps(List.of("鶏肉の余分な脂を取り除く。", "フライパンで焼く。", "調味料を絡める。"));
        return raw;
    }

    private AiRawRecipeDto.Ingredient ingredient(String name, Double qty, String unit) {
        AiRawRecipeDto.Ingredient i = new AiRawRecipeDto.Ingredient();
        i.setName(name);
        i.setQuantity(qty);
        i.setUnit(unit);
        return i;
    }

    @Test
    void 同名の既存レシピがあればAIを呼ばずにそれを返す() {
        Recipe existing = Recipe.builder().id(10L).employeeId(EMPLOYEE_ID).name("鶏肉の照り焼き").build();
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of(existing));

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("鶏肉の照り焼き");

        RecipeDto result = service.generateForDish(EMPLOYEE_ID, req);

        assertEquals(10L, result.getId());
        verify(aiMealClient, never()).generateRecipe(any());
        verify(recipeRepository, never()).save(any());
    }

    @Test
    void 既存レシピが無くAIが利用可能な場合は生成して保存する() {
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(aiMealClient.isConfigured()).thenReturn(true);
        when(aiMealClient.generateRecipe(any())).thenReturn(Optional.of(rawRecipe()));
        when(recipeRepository.save(any())).thenAnswer(inv -> {
            Recipe r = inv.getArgument(0);
            r.setId(20L);
            return r;
        });

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("鶏肉の照り焼き");

        RecipeDto result = service.generateForDish(EMPLOYEE_ID, req);

        assertEquals(20L, result.getId());
        assertEquals(CookingMethod.GRILL, result.getCookingMethod());
        assertEquals(2, result.getDifficulty());
        assertEquals(450, result.getCalories());
        verify(ingredientRepository).saveAll(argThat(list -> ((java.util.Collection<?>) list).size() == 2));
        verify(stepRepository).saveAll(argThat(list -> ((java.util.Collection<?>) list).size() == 3));
    }

    @Test
    void AIが未設定の場合は503エラーになりレシピは保存されない() {
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(aiMealClient.isConfigured()).thenReturn(false);

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("未登録の料理");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateForDish(EMPLOYEE_ID, req));
        assertEquals(503, ex.getStatusCode().value());
        verify(recipeRepository, never()).save(any());
    }

    @Test
    void AI呼び出しが失敗した場合はエラーになりレシピは保存されない() {
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(aiMealClient.isConfigured()).thenReturn(true);
        when(aiMealClient.generateRecipe(any())).thenReturn(Optional.empty());

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("未登録の料理");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateForDish(EMPLOYEE_ID, req));
        assertEquals(502, ex.getStatusCode().value());
        verify(recipeRepository, never()).save(any());
    }

    @Test
    void AIが材料も手順も空で返した場合はエラーになる() {
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(aiMealClient.isConfigured()).thenReturn(true);
        AiRawRecipeDto empty = new AiRawRecipeDto();
        empty.setIngredients(List.of());
        empty.setSteps(List.of());
        when(aiMealClient.generateRecipe(any())).thenReturn(Optional.of(empty));

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("未登録の料理");

        assertThrows(ResponseStatusException.class, () -> service.generateForDish(EMPLOYEE_ID, req));
    }

    @Test
    void AIが不正な調理方法を返しても落ちずOTHER扱いになる() {
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(aiMealClient.isConfigured()).thenReturn(true);
        AiRawRecipeDto raw = rawRecipe();
        raw.setCookingMethod("そんな調理法はない");
        raw.setDifficulty(99); // 範囲外
        when(aiMealClient.generateRecipe(any())).thenReturn(Optional.of(raw));
        when(recipeRepository.save(any())).thenAnswer(inv -> {
            Recipe r = inv.getArgument(0);
            r.setId(21L);
            return r;
        });

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("鶏肉の照り焼き");

        RecipeDto result = service.generateForDish(EMPLOYEE_ID, req);

        assertEquals(CookingMethod.OTHER, result.getCookingMethod());
        assertNull(result.getDifficulty());
    }

    @Test
    void AI取得後のDB保存で例外が発生すると500エラーになりAI呼び出し失敗とは区別される() {
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of());
        when(aiMealClient.isConfigured()).thenReturn(true);
        when(aiMealClient.generateRecipe(any())).thenReturn(Optional.of(rawRecipe()));
        when(recipeRepository.save(any())).thenThrow(new RuntimeException("DB接続エラー(テスト用)"));

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("鶏肉の照り焼き");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateForDish(EMPLOYEE_ID, req));
        // AI呼び出し自体は成功していたので、AI失敗時(502)とは異なる500(サーバー内部エラー)になる
        assertEquals(500, ex.getStatusCode().value());
    }

    @Test
    void 空の料理名を指定すると400エラーになる() {
        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generateForDish(EMPLOYEE_ID, req));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void mealRecordIdを指定すると解決したレシピが本人の食事記録に紐付けられる() {
        Recipe existing = Recipe.builder().id(10L).employeeId(EMPLOYEE_ID).name("鶏肉の照り焼き").build();
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of(existing));
        MealRecord record = MealRecord.builder().id(5L).employeeId(EMPLOYEE_ID).build();
        when(mealRecordRepository.findByIdAndEmployeeId(5L, EMPLOYEE_ID)).thenReturn(Optional.of(record));
        when(mealRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("鶏肉の照り焼き");
        req.setMealRecordId(5L);

        service.generateForDish(EMPLOYEE_ID, req);

        verify(mealRecordRepository).save(argThat(r -> ((MealRecord) r).getRecipeId().equals(10L)));
    }

    @Test
    void 他人の食事記録IDを指定してもレシピは紐付けられない() {
        Recipe existing = Recipe.builder().id(10L).employeeId(EMPLOYEE_ID).name("鶏肉の照り焼き").build();
        when(recipeRepository.findByEmployeeIdOrderByIdDesc(EMPLOYEE_ID))
                .thenReturn(List.of(existing));
        // 他人の食事記録IDのため、本人のemployeeIdでは見つからない
        when(mealRecordRepository.findByIdAndEmployeeId(999L, EMPLOYEE_ID)).thenReturn(Optional.empty());

        RecipeGenerateRequestDto req = new RecipeGenerateRequestDto();
        req.setDishName("鶏肉の照り焼き");
        req.setMealRecordId(999L);

        service.generateForDish(EMPLOYEE_ID, req);

        verify(mealRecordRepository, never()).save(any());
    }
}
