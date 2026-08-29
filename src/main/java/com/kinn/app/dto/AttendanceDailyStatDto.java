package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 1日分の勤怠実働・残業時間(分)。健康×勤怠の連携分析で日付をキーに突き合わせる用。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDailyStatDto {
    private LocalDate date;
    private int workMinutes;
    private int overtimeMinutes;
}
