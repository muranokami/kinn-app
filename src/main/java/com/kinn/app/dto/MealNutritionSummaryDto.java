package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 1日分(または期間)の栄養素合計。
 * 該当する栄養素を1件も入力していない場合は null のままにし、
 * 「0」(入力されていて実質0)と「未入力」を区別できるようにする。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealNutritionSummaryDto {
    private Integer totalCalories;
    private Double totalProteinG;
    private Double totalFatG;
    private Double totalCarbsG;
    private Double totalFiberG;
    private Double totalSaltG;
}
