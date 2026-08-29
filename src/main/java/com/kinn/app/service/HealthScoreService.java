package com.kinn.app.service;

import com.kinn.app.dto.HealthCheckDto;
import com.kinn.app.dto.HealthScoreDto;
import com.kinn.app.entity.HealthConditionLevel;
import com.kinn.app.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 健康スコア(0〜100)の算出ロジックを一元管理する独立したサービス。
 *
 * 「今日の体調チェック」の入力項目(睡眠・疲労・運動・体調)から
 * 各要素のスコア(0〜100)を出し、重み付け平均して総合スコアを算出する。
 * 計算式・重みは今後の改善を見込んでこのクラスだけに閉じている
 * (他のクラスから計算ロジックの詳細を参照しない)。
 *
 * 以前は「ストレス度」も構成要素の1つだったが、労働安全衛生法上のストレスチェック制度
 * (第66条の10)と紛らわしい外形(心理的な負担の程度を個別に測定・表示する機能)を
 * 作らないため、ストレスに関する測定・表示は本アプリから完全に削除した
 * (docs/health-audit-legal-checklist.md 参照)。撤廃した0.20の重みは、残りの4要素へ
 * 比率を保ったまま再配分している(合計は引き続き1.00)。
 */
@Service
public class HealthScoreService {

    // 重み(合計100%)。この配分だけを変えればスコアの傾向を調整できる。
    private static final double WEIGHT_SLEEP = 0.30;
    private static final double WEIGHT_FATIGUE = 0.25;
    private static final double WEIGHT_EXERCISE = 0.20;
    private static final double WEIGHT_CONDITION = 0.25;

    private static final int NEUTRAL_SCORE = 50;

    private final HealthCheckRepository healthCheckRepository;

    public HealthScoreService(HealthCheckRepository healthCheckRepository) {
        this.healthCheckRepository = healthCheckRepository;
    }

    /** 指定日の「今日の体調チェック」からその日の健康スコアを算出する。記録がなければ中立値を返す */
    @Transactional(readOnly = true)
    public HealthScoreDto getScoreForDate(String employeeId, LocalDate date) {
        return healthCheckRepository.findByEmployeeIdAndCheckDate(employeeId, date)
                .map(c -> calculate(
                        date,
                        c.getSleepHours(),
                        c.getFatigueLevel(),
                        c.getExerciseMinutes(),
                        c.getCondition()))
                .orElseGet(() -> neutralScore(date));
    }

    /** DTOから直接スコアを算出する(保存前のプレビュー表示などに利用) */
    public HealthScoreDto calculate(HealthCheckDto dto) {
        return calculate(
                dto.getCheckDate(),
                dto.getSleepHours(),
                dto.getFatigueLevel(),
                dto.getExerciseMinutes(),
                dto.getCondition());
    }

    public HealthScoreDto calculate(
            LocalDate date,
            Double sleepHours,
            Integer fatigueLevel,
            Integer exerciseMinutes,
            HealthConditionLevel condition) {

        boolean hasData = sleepHours != null || fatigueLevel != null
                || exerciseMinutes != null || condition != null;

        int sleepScore = sleepScore(sleepHours);
        int fatigueScore = levelScore(fatigueLevel);
        int exerciseScore = exerciseScore(exerciseMinutes);
        int conditionScore = conditionScore(condition);

        double weighted = sleepScore * WEIGHT_SLEEP
                + fatigueScore * WEIGHT_FATIGUE
                + exerciseScore * WEIGHT_EXERCISE
                + conditionScore * WEIGHT_CONDITION;

        int total = clamp((int) Math.round(weighted));

        return HealthScoreDto.builder()
                .date(date)
                .hasData(hasData)
                .totalScore(total)
                .level(levelLabel(total))
                .sleepScore(sleepScore)
                .fatigueScore(fatigueScore)
                .exerciseScore(exerciseScore)
                .conditionScore(conditionScore)
                .build();
    }

    private HealthScoreDto neutralScore(LocalDate date) {
        return HealthScoreDto.builder()
                .date(date)
                .hasData(false)
                .totalScore(NEUTRAL_SCORE)
                .level(levelLabel(NEUTRAL_SCORE))
                .sleepScore(NEUTRAL_SCORE)
                .fatigueScore(NEUTRAL_SCORE)
                .exerciseScore(NEUTRAL_SCORE)
                .conditionScore(NEUTRAL_SCORE)
                .build();
    }

    // ------------------------------------------------------------------
    // 要素ごとのスコア計算
    // ------------------------------------------------------------------

    /** 睡眠時間: 7〜8時間を満点とし、そこから離れるほど減点する */
    private int sleepScore(Double sleepHours) {
        if (sleepHours == null) return NEUTRAL_SCORE;
        double diff;
        if (sleepHours < 7.0) {
            diff = 7.0 - sleepHours;
        } else if (sleepHours > 8.0) {
            diff = sleepHours - 8.0;
        } else {
            diff = 0;
        }
        return clamp((int) Math.round(100 - diff * 18));
    }

    /** 疲労度(1〜5、5が最も悪い)を0〜100の高いほど良いスコアに変換する */
    private int levelScore(Integer level) {
        if (level == null) return NEUTRAL_SCORE;
        int lv = Math.max(1, Math.min(5, level));
        return clamp((5 - lv) * 25);
    }

    /** 運動時間(分): 30分以上でほぼ満点、0分でも極端な減点はしない */
    private int exerciseScore(Integer minutes) {
        if (minutes == null) return NEUTRAL_SCORE;
        int m = Math.max(0, minutes);
        return clamp(40 + m * 2);
    }

    private int conditionScore(HealthConditionLevel condition) {
        if (condition == null) return NEUTRAL_SCORE;
        return switch (condition) {
            case GREAT -> 100;
            case GOOD -> 80;
            case NORMAL -> 60;
            case SLIGHTLY_BAD -> 35;
            case BAD -> 10;
        };
    }

    private String levelLabel(int score) {
        if (score >= 85) return "とても良好";
        if (score >= 70) return "良好";
        if (score >= 50) return "普通";
        if (score >= 30) return "要注意";
        return "注意";
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
