package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理者向けダッシュボード。個人の健康情報は含まず、会社・部署単位の集計のみを持つ。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminHealthDashboardDto {
    private LocalDate from;
    private LocalDate to;
    private int employeeCount;
    private Double avgHealthScore;
    private Double avgSleepHours;
    private Double avgFatigueLevel;
    private Double avgStressLevel;
    private Double avgOvertimeHours;
    private long alertCount;
    private List<DepartmentHealthSummaryDto> departments;
}
