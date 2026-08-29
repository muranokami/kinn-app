package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** 1日分の食事記録をまとめたもの(食事管理画面・履歴画面で共通利用) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayMealsDto {
    private LocalDate date;
    private List<MealRecordDto> breakfast;
    private List<MealRecordDto> lunch;
    private List<MealRecordDto> dinner;
    private List<MealRecordDto> snacks;
    private MealNutritionSummaryDto nutrition;
    private boolean breakfastRecorded;
    private boolean lunchRecorded;
    private boolean dinnerRecorded;
}
