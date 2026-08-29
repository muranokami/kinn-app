package com.kinn.app.service;

import com.kinn.app.dto.*;
import com.kinn.app.entity.HealthCheck;
import com.kinn.app.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 勤怠データと健康データを組み合わせて分析するサービス。
 *
 * 現時点では基本的な集計・突き合わせのみを行う。将来的に「残業時間が増えると
 * 健康スコアがどう変化するか」のような統計分析(相関分析・予測モデルなど)を
 * 追加する場合は、Python側のバッチ処理(独立実行)で発展させる想定であり、
 * ここではその土台となる素データの結合・単純集計までを担当する。
 */
@Service
public class HealthAnalysisService {

    /** 「残業が多い日」とみなす1日あたりの残業時間のしきい値(時間) */
    private static final double HIGH_OVERTIME_DAY_THRESHOLD_HOURS = 2.0;
    /** 「睡眠が短い日」とみなすしきい値(時間) */
    private static final double SHORT_SLEEP_THRESHOLD_HOURS = 6.0;

    private final HealthCheckRepository healthCheckRepository;
    private final AttendanceService attendanceService;
    private final HealthScoreService healthScoreService;

    public HealthAnalysisService(HealthCheckRepository healthCheckRepository,
                                  AttendanceService attendanceService,
                                  HealthScoreService healthScoreService) {
        this.healthCheckRepository = healthCheckRepository;
        this.attendanceService = attendanceService;
        this.healthScoreService = healthScoreService;
    }

    @Transactional(readOnly = true)
    public HealthAnalysisDto getAnalysis(String employeeId, String period) {
        LocalDate to = LocalDate.now();
        LocalDate from = HealthTrendService.resolveFrom(to, period);

        List<AttendanceDailyStatDto> attendanceDaily = attendanceService.getDailyStats(employeeId, from, to);
        Map<LocalDate, AttendanceDailyStatDto> attendanceByDate = new HashMap<>();
        for (AttendanceDailyStatDto a : attendanceDaily) attendanceByDate.put(a.getDate(), a);

        List<HealthCheck> checks =
                healthCheckRepository.findByEmployeeIdAndCheckDateBetweenOrderByCheckDateAsc(employeeId, from, to);
        Map<LocalDate, HealthCheck> checkByDate = new HashMap<>();
        for (HealthCheck c : checks) checkByDate.put(c.getCheckDate(), c);

        List<HealthAnalysisPointDto> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            AttendanceDailyStatDto a = attendanceByDate.get(d);
            HealthCheck c = checkByDate.get(d);
            if (a == null && c == null) continue; // どちらのデータもない日は分析対象から除く

            Integer score = null;
            if (c != null) {
                HealthScoreDto scoreDto = healthScoreService.calculate(
                        d, c.getSleepHours(), c.getFatigueLevel(), c.getStressLevel(),
                        c.getExerciseMinutes(), c.getCondition());
                if (scoreDto.isHasData()) score = scoreDto.getTotalScore();
            }

            points.add(HealthAnalysisPointDto.builder()
                    .date(d)
                    .workHours(a != null ? round2(a.getWorkMinutes() / 60.0) : null)
                    .overtimeHours(a != null ? round2(a.getOvertimeMinutes() / 60.0) : null)
                    .sleepHours(c != null ? c.getSleepHours() : null)
                    .fatigueLevel(c != null ? c.getFatigueLevel() : null)
                    .stressLevel(c != null ? c.getStressLevel() : null)
                    .healthScore(score)
                    .build());
        }

        return HealthAnalysisDto.builder()
                .period(period)
                .from(from)
                .to(to)
                .points(points)
                .summary(summarize(points))
                .build();
    }

    private HealthAnalysisSummaryDto summarize(List<HealthAnalysisPointDto> points) {
        double lowOtScoreSum = 0, highOtScoreSum = 0;
        int lowOtScoreN = 0, highOtScoreN = 0;
        double shortSleepFatigueSum = 0, enoughSleepFatigueSum = 0;
        int shortSleepFatigueN = 0, enoughSleepFatigueN = 0;
        double highOtStressSum = 0, lowOtStressSum = 0;
        int highOtStressN = 0, lowOtStressN = 0;

        for (HealthAnalysisPointDto p : points) {
            boolean highOt = p.getOvertimeHours() != null && p.getOvertimeHours() >= HIGH_OVERTIME_DAY_THRESHOLD_HOURS;
            boolean lowOt = p.getOvertimeHours() != null && p.getOvertimeHours() < HIGH_OVERTIME_DAY_THRESHOLD_HOURS;

            if (p.getHealthScore() != null) {
                if (highOt) { highOtScoreSum += p.getHealthScore(); highOtScoreN++; }
                else if (lowOt) { lowOtScoreSum += p.getHealthScore(); lowOtScoreN++; }
            }
            if (p.getFatigueLevel() != null && p.getSleepHours() != null) {
                if (p.getSleepHours() < SHORT_SLEEP_THRESHOLD_HOURS) {
                    shortSleepFatigueSum += p.getFatigueLevel(); shortSleepFatigueN++;
                } else {
                    enoughSleepFatigueSum += p.getFatigueLevel(); enoughSleepFatigueN++;
                }
            }
            if (p.getStressLevel() != null) {
                if (highOt) { highOtStressSum += p.getStressLevel(); highOtStressN++; }
                else if (lowOt) { lowOtStressSum += p.getStressLevel(); lowOtStressN++; }
            }
        }

        return HealthAnalysisSummaryDto.builder()
                .avgHealthScoreLowOvertime(avgOrNull(lowOtScoreSum, lowOtScoreN))
                .avgHealthScoreHighOvertime(avgOrNull(highOtScoreSum, highOtScoreN))
                .avgFatigueShortSleep(avgOrNull(shortSleepFatigueSum, shortSleepFatigueN))
                .avgFatigueEnoughSleep(avgOrNull(enoughSleepFatigueSum, enoughSleepFatigueN))
                .avgStressHighOvertime(avgOrNull(highOtStressSum, highOtStressN))
                .avgStressLowOvertime(avgOrNull(lowOtStressSum, lowOtStressN))
                .build();
    }

    private Double avgOrNull(double sum, int n) {
        return n == 0 ? null : Math.round((sum / n) * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
