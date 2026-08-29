package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 勤怠×健康の基本的な集計。将来的な統計分析(Python側)の土台となる
 * ごく単純な集計値のみをまず持つ。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAnalysisSummaryDto {
    /** 残業が少ない日(2時間未満)の平均健康スコア */
    private Double avgHealthScoreLowOvertime;
    /** 残業が多い日(2時間以上)の平均健康スコア */
    private Double avgHealthScoreHighOvertime;
    /** 睡眠が短い日(6時間未満)の平均疲労度 */
    private Double avgFatigueShortSleep;
    /** 睡眠が十分な日(6時間以上)の平均疲労度 */
    private Double avgFatigueEnoughSleep;
}
