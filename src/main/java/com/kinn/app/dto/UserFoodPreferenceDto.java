package com.kinn.app.dto;

import com.kinn.app.entity.CookingLevel;
import com.kinn.app.entity.SelfCookedPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ユーザーの食事の好み・制約。AI献立提案(MealRecommendationService)で考慮する。
 * アレルギー(allergies)は献立から確実に除外するための必須情報として扱う。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFoodPreferenceDto {
    private String favoriteFoods;
    private String dislikedFoods;
    private String allergies;
    private String dietaryRestrictions;
    private Integer budgetYen;
    private Integer cookingMinutes;
    private SelfCookedPreference selfCooked;
    private CookingLevel cookingLevel;
}
