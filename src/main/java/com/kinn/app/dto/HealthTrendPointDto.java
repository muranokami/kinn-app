package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 健康状態の推移グラフ用の1日分のデータポイント。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthTrendPointDto {
    private LocalDate date;
    private Integer healthScore;
    private Double weightKg;
    private Double sleepHours;
    private Integer fatigueLevel;
    private Integer stressLevel;
    private Integer exerciseMinutes;
}
