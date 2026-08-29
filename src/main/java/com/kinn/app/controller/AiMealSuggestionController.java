package com.kinn.app.controller;

import com.kinn.app.dto.AiMealSuggestionDto;
import com.kinn.app.dto.MealRecordDto;
import com.kinn.app.entity.MealType;
import com.kinn.app.service.MealRecommendationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * AI献立提案API。ログイン中のユーザーのデータのみを参照・更新する。
 */
@RestController
@RequestMapping("/api/meal/ai-suggestion")
public class AiMealSuggestionController {

    private final MealRecommendationService recommendationService;

    public AiMealSuggestionController(MealRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** 指定日(省略時は今日)の最新のAI献立提案を取得する。まだ無ければ新規生成する */
    @GetMapping
    public AiMealSuggestionDto getLatest(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return recommendationService.getOrCreateLatest(authentication.getName(), d);
    }

    /** 「🔄 別の献立を提案」。前回とは異なる献立を新しい試行として生成する */
    @PostMapping("/regenerate")
    public AiMealSuggestionDto regenerate(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return recommendationService.regenerate(authentication.getName(), d);
    }

    /** 「⭐ この献立を保存」。AI提案として保存済みであることを記録する(AI献立履歴で確認できる) */
    @PostMapping("/{id}/save")
    public AiMealSuggestionDto save(
            @PathVariable Long id,
            Authentication authentication) {
        return recommendationService.markSaved(authentication.getName(), id);
    }

    /**
     * 「この献立を◯食に登録」。ユーザーが明示的に押した場合のみ正式な食事記録(meal_record)へ保存する。
     * AIが提案しただけでは食事記録は確定しない。
     */
    @PostMapping("/{id}/register")
    public MealRecordDto register(
            @PathVariable Long id,
            @RequestParam MealType mealType,
            Authentication authentication) {
        return recommendationService.registerToMealRecord(authentication.getName(), id, mealType);
    }

    /** AI献立提案の履歴(指定期間、日付ごとに1件) */
    @GetMapping("/history")
    public List<AiMealSuggestionDto> getHistory(
            @RequestParam String from,
            @RequestParam String to,
            Authentication authentication) {
        return recommendationService.getHistory(authentication.getName(), LocalDate.parse(from), LocalDate.parse(to));
    }
}
