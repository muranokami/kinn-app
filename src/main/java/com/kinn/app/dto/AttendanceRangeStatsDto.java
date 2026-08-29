package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 期間を指定した勤怠の簡易集計。健康×勤怠の連携分析で使う。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRangeStatsDto {
    private double workingDays;
    private double workingHours;
    private double overtimeHours;
}
