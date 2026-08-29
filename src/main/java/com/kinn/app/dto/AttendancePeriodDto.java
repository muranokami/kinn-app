package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 締め日(任意の開始日)を起点とした1か月分の勤怠(⑦の期間指定に対応)。
 * 暦月固定の{@link MonthAttendanceDto}と違い、開始日・終了日が任意の日をまたぐ期間に対応する。
 * 集計ロジックは{@link MonthAttendanceDto}と共通(AttendanceServiceの同じ集計処理を再利用)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendancePeriodDto {
    /** 期間開始日(例: 2026-08-20) */
    private LocalDate startDate;
    /** 期間終了日(例: 2026-09-19) */
    private LocalDate endDate;
    /** この期間の計算に使われた締め日(1〜31)。startDate/endDateを直接指定した場合はnullのことがある */
    private Integer closingDay;
    private List<AttendanceRecordDto> days;
    private MonthlySummaryDto summary;
}
