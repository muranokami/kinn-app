package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 月次の健康集計。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthSummaryDto {

    private int year;
    private int month;

    /** 記録日数(日) */
    private int recordedDays;
    /** 平均体重(kg) */
    private Double avgWeightKg;
    /** 平均睡眠時間(時間) */
    private Double avgSleepHours;
    /** 合計歩数(歩) */
    private long totalSteps;
    /** 平均歩数(歩/日) */
    private long avgSteps;
    /** 運動した日数(日) */
    private int exerciseDays;
    /** 合計運動時間(分) */
    private int totalExerciseMinutes;
    /** 平均収縮期血圧(上) */
    private Double avgSystolicBp;
    /** 平均拡張期血圧(下) */
    private Double avgDiastolicBp;
    /** 体調が良かった日数(絶好調+良い) */
    private int goodConditionDays;
    /** 体調不良だった日数 */
    private int badConditionDays;
}
