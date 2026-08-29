package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 健康スコア(0〜100)とその内訳。算出ロジックは HealthScoreService に集約されている。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthScoreDto {
    private LocalDate date;
    /** その日の記録が1件もない場合はfalse(スコアは中立値になる) */
    private boolean hasData;
    /** 総合健康スコア(0〜100) */
    private int totalScore;
    /** スコアの評価ラベル(例: 良好) */
    private String level;
    private int sleepScore;
    private int fatigueScore;
    private int stressScore;
    private int exerciseScore;
    private int conditionScore;
}
