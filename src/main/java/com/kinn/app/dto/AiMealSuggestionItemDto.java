package com.kinn.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kinn.app.entity.MealType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** AI献立提案1食分(朝食/昼食/夕食のいずれか)。AIレスポンスのJSONパース先も兼ねる */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiMealSuggestionItemDto {
    private MealType mealType;
    /** 料理名 */
    private String dishName;
    /** 使用食材(カンマ区切りの自由記述) */
    private String ingredients;
    /** おおよそのカロリー(kcal) */
    private Integer calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    /** おおよその調理時間(分) */
    private Integer cookingMinutes;
    /** おすすめ理由 */
    private String reason;
}
