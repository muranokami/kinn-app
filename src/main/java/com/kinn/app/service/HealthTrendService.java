package com.kinn.app.service;

import com.kinn.app.dto.HealthScoreDto;
import com.kinn.app.dto.HealthTrendDto;
import com.kinn.app.dto.HealthTrendPointDto;
import com.kinn.app.entity.HealthCheck;
import com.kinn.app.entity.HealthRecord;
import com.kinn.app.repository.HealthCheckRepository;
import com.kinn.app.repository.HealthRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康状態の推移(健康スコア・体重・睡眠時間・疲労度・ストレス度・運動時間)を
 * 期間指定で取得するサービス。
 *
 * 体重は「今日の体調チェック」では入力しないため、既存の月次健康記録
 * ({@link HealthRecord}, 従来の健康管理画面で入力される体重)を流用する。
 * これにより既存機能に変更を加えることなく、体重の推移もグラフに載せられる。
 */
@Service
public class HealthTrendService {

    private final HealthCheckRepository healthCheckRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final HealthScoreService healthScoreService;

    public HealthTrendService(HealthCheckRepository healthCheckRepository,
                               HealthRecordRepository healthRecordRepository,
                               HealthScoreService healthScoreService) {
        this.healthCheckRepository = healthCheckRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.healthScoreService = healthScoreService;
    }

    @Transactional(readOnly = true)
    public HealthTrendDto getTrend(String employeeId, String period) {
        LocalDate to = LocalDate.now();
        LocalDate from = resolveFrom(to, period);

        List<HealthCheck> checks =
                healthCheckRepository.findByEmployeeIdAndCheckDateBetweenOrderByCheckDateAsc(employeeId, from, to);
        Map<LocalDate, HealthCheck> checkByDate = new HashMap<>();
        for (HealthCheck c : checks) checkByDate.put(c.getCheckDate(), c);

        List<HealthRecord> records =
                healthRecordRepository.findByEmployeeIdAndRecordDateBetweenOrderByRecordDateAsc(employeeId, from, to);
        Map<LocalDate, Double> weightByDate = new HashMap<>();
        for (HealthRecord r : records) {
            if (r.getWeightKg() != null) weightByDate.put(r.getRecordDate(), r.getWeightKg());
        }

        List<HealthTrendPointDto> points = new java.util.ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            HealthCheck c = checkByDate.get(d);
            Integer score = null;
            if (c != null) {
                HealthScoreDto scoreDto = healthScoreService.calculate(
                        d, c.getSleepHours(), c.getFatigueLevel(), c.getStressLevel(),
                        c.getExerciseMinutes(), c.getCondition());
                if (scoreDto.isHasData()) score = scoreDto.getTotalScore();
            }
            points.add(HealthTrendPointDto.builder()
                    .date(d)
                    .healthScore(score)
                    .weightKg(weightByDate.get(d))
                    .sleepHours(c != null ? c.getSleepHours() : null)
                    .fatigueLevel(c != null ? c.getFatigueLevel() : null)
                    .stressLevel(c != null ? c.getStressLevel() : null)
                    .exerciseMinutes(c != null ? c.getExerciseMinutes() : null)
                    .build());
        }

        return HealthTrendDto.builder()
                .period(period)
                .from(from)
                .to(to)
                .points(points)
                .build();
    }

    /** period(1w/1m/3m/6m)を開始日に変換する。未知の値は1mとして扱う */
    static LocalDate resolveFrom(LocalDate to, String period) {
        return switch (period == null ? "1m" : period) {
            case "1w" -> to.minusWeeks(1).plusDays(1);
            case "3m" -> to.minusMonths(3).plusDays(1);
            case "6m" -> to.minusMonths(6).plusDays(1);
            default -> to.minusMonths(1).plusDays(1);
        };
    }
}
