package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 勤怠×健康の突き合わせ1日分。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAnalysisPointDto {
    private LocalDate date;
    private Double workHours;
    private Double overtimeHours;
    private Double sleepHours;
    private Integer fatigueLevel;
    private Integer stressLevel;
    private Integer healthScore;
}
