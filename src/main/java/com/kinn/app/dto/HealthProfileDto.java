package com.kinn.app.dto;

import com.kinn.app.entity.DrinkingStatus;
import com.kinn.app.entity.SmokingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthProfileDto {
    private Long id;
    private String department;
    private Double heightCm;
    private Double weightKg;
    /** 身長・体重から自動計算するBMI(表示専用、算出不能な場合はnull) */
    private Double bmi;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Double bodyTemperature;
    private Integer exerciseMinutes;
    private Double avgSleepHours;
    private SmokingStatus smokingStatus;
    private DrinkingStatus drinkingStatus;
    private String healthMemo;
}
