package com.kinn.app.service;

import com.kinn.app.dto.AttendanceRangeStatsDto;
import com.kinn.app.dto.HealthAlertDto;
import com.kinn.app.entity.HealthAlert;
import com.kinn.app.entity.HealthAlertSeverity;
import com.kinn.app.entity.HealthAlertType;
import com.kinn.app.entity.HealthCheck;
import com.kinn.app.repository.HealthAlertRepository;
import com.kinn.app.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 健康アラート(一般的な注意喚起)を判定・記録するサービス。
 *
 * 医療診断や病気の判定は行わない。あくまで「最近こういう傾向が続いています」
 * という一般的な気づきを促すためのものであり、しきい値は将来的に調整可能な
 * 定数として定義している。
 */
@Service
public class HealthAlertService {

    /** 判定対象とする直近日数 */
    private static final int EVAL_WINDOW_DAYS = 7;
    /** 判定に必要な最低記録日数(データが少なすぎる状態での誤判定を防ぐ) */
    private static final int MIN_RECORDED_DAYS = 3;

    private static final double LOW_SLEEP_THRESHOLD_HOURS = 5.5;
    private static final double HIGH_FATIGUE_THRESHOLD = 4.0;
    private static final double HIGH_STRESS_THRESHOLD = 4.0;
    private static final double HIGH_OVERTIME_THRESHOLD_HOURS = 20.0;

    private final HealthCheckRepository healthCheckRepository;
    private final HealthAlertRepository healthAlertRepository;
    private final AttendanceService attendanceService;

    public HealthAlertService(HealthCheckRepository healthCheckRepository,
                               HealthAlertRepository healthAlertRepository,
                               AttendanceService attendanceService) {
        this.healthCheckRepository = healthCheckRepository;
        this.healthAlertRepository = healthAlertRepository;
        this.attendanceService = attendanceService;
    }

    /**
     * 直近{@value #EVAL_WINDOW_DAYS}日間の記録を評価し、条件を満たしていれば
     * 本日付でアラートを新規登録する(同日・同種別の重複登録はしない)。
     * その上で、指定期間のアラート履歴を返す。
     */
    @Transactional
    public List<HealthAlertDto> evaluateAndGetAlerts(String employeeId, int historyDays) {
        evaluate(employeeId);

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(historyDays, EVAL_WINDOW_DAYS) - 1L);
        return healthAlertRepository
                .findByEmployeeIdAndTriggeredDateBetweenOrderByTriggeredDateDesc(employeeId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private void evaluate(String employeeId) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(EVAL_WINDOW_DAYS - 1L);

        List<HealthCheck> checks = healthCheckRepository
                .findByEmployeeIdAndCheckDateBetweenOrderByCheckDateAsc(employeeId, windowStart, today);

        evaluateAverage(employeeId, today, checks, HealthCheck::getSleepHours,
                avg -> avg < LOW_SLEEP_THRESHOLD_HOURS,
                HealthAlertType.LOW_SLEEP, HealthAlertSeverity.WARNING,
                "最近、睡眠時間が短い状態が続いています。十分な休息を取ることをおすすめします。");

        evaluateAverage(employeeId, today, checks, c -> intToDouble(c.getFatigueLevel()),
                avg -> avg >= HIGH_FATIGUE_THRESHOLD,
                HealthAlertType.HIGH_FATIGUE, HealthAlertSeverity.WARNING,
                "最近、疲労度が高い状態が続いています。十分な休息を取ることをおすすめします。");

        evaluateAverage(employeeId, today, checks, c -> intToDouble(c.getStressLevel()),
                avg -> avg >= HIGH_STRESS_THRESHOLD,
                HealthAlertType.HIGH_STRESS, HealthAlertSeverity.WARNING,
                "最近、ストレスが高い状態が続いています。気分転換の時間を取ってみることをおすすめします。");

        AttendanceRangeStatsDto stats = attendanceService.getRangeStats(employeeId, windowStart, today);
        if (stats.getOvertimeHours() >= HIGH_OVERTIME_THRESHOLD_HOURS) {
            saveIfAbsent(employeeId, today, HealthAlertType.HIGH_OVERTIME, HealthAlertSeverity.WARNING,
                    "直近1週間の残業時間が多くなっています。無理のない働き方を心がけましょう。");
        }
    }

    private void evaluateAverage(
            String employeeId, LocalDate today, List<HealthCheck> checks,
            java.util.function.Function<HealthCheck, Double> extractor,
            java.util.function.Predicate<Double> condition,
            HealthAlertType type, HealthAlertSeverity severity, String message) {

        double sum = 0;
        int count = 0;
        for (HealthCheck c : checks) {
            Double v = extractor.apply(c);
            if (v != null) { sum += v; count++; }
        }
        if (count < MIN_RECORDED_DAYS) return;

        double avg = sum / count;
        if (condition.test(avg)) {
            saveIfAbsent(employeeId, today, type, severity, message);
        }
    }

    private void saveIfAbsent(String employeeId, LocalDate triggeredDate,
                               HealthAlertType type, HealthAlertSeverity severity, String message) {
        if (healthAlertRepository.existsByEmployeeIdAndAlertTypeAndTriggeredDate(employeeId, type, triggeredDate)) {
            return;
        }
        healthAlertRepository.save(HealthAlert.builder()
                .employeeId(employeeId)
                .alertType(type)
                .severity(severity)
                .message(message)
                .triggeredDate(triggeredDate)
                .build());
    }

    private Double intToDouble(Integer v) {
        return v == null ? null : v.doubleValue();
    }

    private HealthAlertDto toDto(HealthAlert a) {
        return HealthAlertDto.builder()
                .id(a.getId())
                .alertType(a.getAlertType())
                .alertTypeLabel(a.getAlertType().getLabel())
                .severity(a.getSeverity())
                .message(a.getMessage())
                .triggeredDate(a.getTriggeredDate())
                .build();
    }
}
