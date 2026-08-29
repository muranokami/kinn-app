package com.kinn.app.service;

import com.kinn.app.dto.AdminHealthDashboardDto;
import com.kinn.app.dto.AttendanceRangeStatsDto;
import com.kinn.app.dto.DepartmentHealthSummaryDto;
import com.kinn.app.dto.HealthScoreDto;
import com.kinn.app.entity.HealthCheck;
import com.kinn.app.entity.HealthProfile;
import com.kinn.app.repository.HealthAlertRepository;
import com.kinn.app.repository.HealthCheckRepository;
import com.kinn.app.repository.HealthProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 管理者向けの健康ダッシュボード集計サービス。
 *
 * 個人の健康情報(体重・血圧・メモなど)は一切返さず、社員数・平均値・
 * アラート件数といった会社/部署単位の集計値のみを提供する。
 * 部署は {@link HealthProfile#getDepartment()} を使った簡易グルーピングであり、
 * 部署マスタが整備された際の拡張を見込んだ暫定実装。
 *
 * 部署単位の集計は、対象人数が minEmployeeCountThreshold 人未満の場合、個人が特定されるリスクを
 * 考慮して集計値そのものを返さない(summarizeDepartment参照)。人数が少ないほど「平均」が
 * 実質的に個人の値そのものに近づくため、会社全体の集計(getDashboard直下の値)とは別に
 * 部署単位でこのガードを設けている。
 */
@Service
public class AdminHealthService {

    private static final String UNASSIGNED_DEPARTMENT = "未設定";

    private final HealthCheckRepository healthCheckRepository;
    private final HealthProfileRepository healthProfileRepository;
    private final HealthAlertRepository healthAlertRepository;
    private final AttendanceService attendanceService;
    private final HealthScoreService healthScoreService;
    private final int minEmployeeCountThreshold;

    public AdminHealthService(HealthCheckRepository healthCheckRepository,
                               HealthProfileRepository healthProfileRepository,
                               HealthAlertRepository healthAlertRepository,
                               AttendanceService attendanceService,
                               HealthScoreService healthScoreService,
                               @Value("${app.health.department-summary.min-employee-count:5}") int minEmployeeCountThreshold) {
        this.healthCheckRepository = healthCheckRepository;
        this.healthProfileRepository = healthProfileRepository;
        this.healthAlertRepository = healthAlertRepository;
        this.attendanceService = attendanceService;
        this.healthScoreService = healthScoreService;
        this.minEmployeeCountThreshold = minEmployeeCountThreshold;
    }

    /**
     * @param companyPrefix 管理者自身の会社スコープ("companyId|"形式)。他社の社員データが
     *                      混ざらないよう、この接頭辞に一致する employeeId のみを対象にする。
     */
    @Transactional(readOnly = true)
    public AdminHealthDashboardDto getDashboard(int days, String companyPrefix) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(days, 1) - 1L);

        Set<String> employeeIds = new TreeSet<>();
        employeeIds.addAll(healthCheckRepository.findDistinctEmployeeIds());
        employeeIds.addAll(healthProfileRepository.findDistinctEmployeeIds());
        employeeIds.removeIf(id -> !id.startsWith(companyPrefix));

        Map<String, String> departmentByEmployee = new HashMap<>();
        for (HealthProfile p : healthProfileRepository.findByEmployeeIdIn(new ArrayList<>(employeeIds))) {
            departmentByEmployee.put(p.getEmployeeId(),
                    (p.getDepartment() == null || p.getDepartment().isBlank())
                            ? UNASSIGNED_DEPARTMENT : p.getDepartment());
        }

        List<EmployeeHealthAggregate> aggregates = new ArrayList<>();
        for (String employeeId : employeeIds) {
            aggregates.add(aggregateEmployee(employeeId, from, to,
                    departmentByEmployee.getOrDefault(employeeId, UNASSIGNED_DEPARTMENT)));
        }

        double scoreSum = 0; int scoreN = 0;
        double sleepSum = 0; int sleepN = 0;
        double fatigueSum = 0; int fatigueN = 0;
        double stressSum = 0; int stressN = 0;
        double overtimeSum = 0; int overtimeN = 0;
        long alertTotal = 0;

        Map<String, List<EmployeeHealthAggregate>> byDepartment = new TreeMap<>();

        for (EmployeeHealthAggregate agg : aggregates) {
            if (agg.avgHealthScore != null) { scoreSum += agg.avgHealthScore; scoreN++; }
            if (agg.avgSleepHours != null) { sleepSum += agg.avgSleepHours; sleepN++; }
            if (agg.avgFatigueLevel != null) { fatigueSum += agg.avgFatigueLevel; fatigueN++; }
            if (agg.avgStressLevel != null) { stressSum += agg.avgStressLevel; stressN++; }
            overtimeSum += agg.overtimeHours; overtimeN++;
            alertTotal += agg.alertCount;

            byDepartment.computeIfAbsent(agg.department, d -> new ArrayList<>()).add(agg);
        }

        List<DepartmentHealthSummaryDto> departments = new ArrayList<>();
        for (Map.Entry<String, List<EmployeeHealthAggregate>> e : byDepartment.entrySet()) {
            departments.add(summarizeDepartment(e.getKey(), e.getValue()));
        }

        return AdminHealthDashboardDto.builder()
                .from(from)
                .to(to)
                .employeeCount(employeeIds.size())
                .avgHealthScore(avgOrNull(scoreSum, scoreN))
                .avgSleepHours(avgOrNull(sleepSum, sleepN))
                .avgFatigueLevel(avgOrNull(fatigueSum, fatigueN))
                .avgStressLevel(avgOrNull(stressSum, stressN))
                .avgOvertimeHours(avgOrNull(overtimeSum, overtimeN))
                .alertCount(alertTotal)
                .departments(departments)
                .build();
    }

    private DepartmentHealthSummaryDto summarizeDepartment(String department, List<EmployeeHealthAggregate> group) {
        int employeeCount = group.size();

        // 対象人数が少ない部署は、平均値・アラート件数を返すこと自体が実質的に個人の健康状態を
        // 特定させてしまうため、部署名・人数以外は意図的に返さない(insufficientData=true)。
        if (employeeCount < minEmployeeCountThreshold) {
            return DepartmentHealthSummaryDto.builder()
                    .department(department)
                    .employeeCount(employeeCount)
                    .insufficientData(true)
                    .minEmployeeCountThreshold(minEmployeeCountThreshold)
                    .build();
        }

        double scoreSum = 0; int scoreN = 0;
        double sleepSum = 0; int sleepN = 0;
        double fatigueSum = 0; int fatigueN = 0;
        double stressSum = 0; int stressN = 0;
        double overtimeSum = 0; int overtimeN = 0;
        long alertTotal = 0;

        for (EmployeeHealthAggregate agg : group) {
            if (agg.avgHealthScore != null) { scoreSum += agg.avgHealthScore; scoreN++; }
            if (agg.avgSleepHours != null) { sleepSum += agg.avgSleepHours; sleepN++; }
            if (agg.avgFatigueLevel != null) { fatigueSum += agg.avgFatigueLevel; fatigueN++; }
            if (agg.avgStressLevel != null) { stressSum += agg.avgStressLevel; stressN++; }
            overtimeSum += agg.overtimeHours; overtimeN++;
            alertTotal += agg.alertCount;
        }

        return DepartmentHealthSummaryDto.builder()
                .department(department)
                .employeeCount(employeeCount)
                .insufficientData(false)
                .minEmployeeCountThreshold(minEmployeeCountThreshold)
                .avgHealthScore(avgOrNull(scoreSum, scoreN))
                .avgSleepHours(avgOrNull(sleepSum, sleepN))
                .avgFatigueLevel(avgOrNull(fatigueSum, fatigueN))
                .avgStressLevel(avgOrNull(stressSum, stressN))
                .avgOvertimeHours(avgOrNull(overtimeSum, overtimeN))
                .alertCount(alertTotal)
                .build();
    }

    private EmployeeHealthAggregate aggregateEmployee(String employeeId, LocalDate from, LocalDate to, String department) {
        List<HealthCheck> checks =
                healthCheckRepository.findByEmployeeIdAndCheckDateBetweenOrderByCheckDateAsc(employeeId, from, to);

        double scoreSum = 0; int scoreN = 0;
        double sleepSum = 0; int sleepN = 0;
        double fatigueSum = 0; int fatigueN = 0;
        double stressSum = 0; int stressN = 0;

        for (HealthCheck c : checks) {
            HealthScoreDto score = healthScoreService.calculate(
                    c.getCheckDate(), c.getSleepHours(), c.getFatigueLevel(), c.getStressLevel(),
                    c.getExerciseMinutes(), c.getCondition());
            if (score.isHasData()) { scoreSum += score.getTotalScore(); scoreN++; }
            if (c.getSleepHours() != null) { sleepSum += c.getSleepHours(); sleepN++; }
            if (c.getFatigueLevel() != null) { fatigueSum += c.getFatigueLevel(); fatigueN++; }
            if (c.getStressLevel() != null) { stressSum += c.getStressLevel(); stressN++; }
        }

        AttendanceRangeStatsDto attendance = attendanceService.getRangeStats(employeeId, from, to);
        long alertCount = healthAlertRepository.countByEmployeeIdAndTriggeredDateBetween(employeeId, from, to);

        return new EmployeeHealthAggregate(
                department,
                avgOrNull(scoreSum, scoreN),
                avgOrNull(sleepSum, sleepN),
                avgOrNull(fatigueSum, fatigueN),
                avgOrNull(stressSum, stressN),
                attendance.getOvertimeHours(),
                alertCount);
    }

    private Double avgOrNull(double sum, int n) {
        return n == 0 ? null : Math.round((sum / n) * 10.0) / 10.0;
    }

    /** 社員1人分の集計を保持するだけの単純な内部クラス */
    private static class EmployeeHealthAggregate {
        final String department;
        final Double avgHealthScore;
        final Double avgSleepHours;
        final Double avgFatigueLevel;
        final Double avgStressLevel;
        final double overtimeHours;
        final long alertCount;

        EmployeeHealthAggregate(String department, Double avgHealthScore, Double avgSleepHours,
                                 Double avgFatigueLevel, Double avgStressLevel,
                                 double overtimeHours, long alertCount) {
            this.department = department;
            this.avgHealthScore = avgHealthScore;
            this.avgSleepHours = avgSleepHours;
            this.avgFatigueLevel = avgFatigueLevel;
            this.avgStressLevel = avgStressLevel;
            this.overtimeHours = overtimeHours;
            this.alertCount = alertCount;
        }
    }
}
