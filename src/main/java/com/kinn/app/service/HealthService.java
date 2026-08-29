package com.kinn.app.service;

import com.kinn.app.dto.HealthRecordDto;
import com.kinn.app.dto.HealthSummaryDto;
import com.kinn.app.dto.MonthHealthDto;
import com.kinn.app.entity.Condition;
import com.kinn.app.entity.HealthRecord;
import com.kinn.app.repository.HealthRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 健康記録の集計ロジックを一元管理するサービス。
 * 構成は AttendanceService に倣っている。
 */
@Service
public class HealthService {

    private final HealthRecordRepository repository;

    public HealthService(HealthRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MonthHealthDto getMonth(String employeeId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<HealthRecord> records =
                repository.findByEmployeeIdAndRecordDateBetweenOrderByRecordDateAsc(employeeId, from, to);

        List<HealthRecordDto> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            HealthRecord found = records.stream()
                    .filter(r -> r.getRecordDate().equals(date))
                    .findFirst()
                    .orElse(null);
            days.add(toDto(found, date));
        }

        HealthSummaryDto summary = summarize(year, month, records);

        return MonthHealthDto.builder()
                .year(year)
                .month(month)
                .days(days)
                .summary(summary)
                .build();
    }

    @Transactional
    public HealthRecordDto saveDay(String employeeId, HealthRecordDto dto) {
        HealthRecord entity = repository
                .findByEmployeeIdAndRecordDate(employeeId, dto.getRecordDate())
                .orElseGet(HealthRecord::new);

        entity.setEmployeeId(employeeId);
        entity.setRecordDate(dto.getRecordDate());
        entity.setWeightKg(dto.getWeightKg());
        entity.setSleepHours(dto.getSleepHours());
        entity.setSteps(dto.getSteps());
        entity.setExerciseMinutes(dto.getExerciseMinutes());
        entity.setSystolicBp(dto.getSystolicBp());
        entity.setDiastolicBp(dto.getDiastolicBp());
        entity.setCondition(dto.getCondition());
        entity.setMemo(dto.getMemo());

        HealthRecord saved = repository.save(entity);
        return toDto(saved, saved.getRecordDate());
    }

    @Transactional
    public MonthHealthDto saveMonth(String employeeId, int year, int month, List<HealthRecordDto> dtoList) {
        for (HealthRecordDto dto : dtoList) {
            // 何も入力されていない日は保存しない(不要な空レコードを作らない)
            if (isEmpty(dto)) continue;
            saveDay(employeeId, dto);
        }
        return getMonth(employeeId, year, month);
    }

    @Transactional
    public void deleteDay(String employeeId, LocalDate date) {
        repository.findByEmployeeIdAndRecordDate(employeeId, date)
                .ifPresent(repository::delete);
    }

    // ------------------------------------------------------------------
    // 集計ロジック
    // ------------------------------------------------------------------

    private HealthSummaryDto summarize(int year, int month, List<HealthRecord> records) {
        int recordedDays = 0;
        double weightSum = 0; int weightCount = 0;
        double sleepSum = 0; int sleepCount = 0;
        long stepsSum = 0; int stepsCount = 0;
        int exerciseDays = 0;
        int totalExerciseMinutes = 0;
        double systolicSum = 0; int systolicCount = 0;
        double diastolicSum = 0; int diastolicCount = 0;
        int goodConditionDays = 0;
        int badConditionDays = 0;

        for (HealthRecord r : records) {
            if (isRecorded(r)) recordedDays++;

            if (r.getWeightKg() != null) { weightSum += r.getWeightKg(); weightCount++; }
            if (r.getSleepHours() != null) { sleepSum += r.getSleepHours(); sleepCount++; }
            if (r.getSteps() != null) { stepsSum += r.getSteps(); stepsCount++; }
            if (r.getExerciseMinutes() != null && r.getExerciseMinutes() > 0) {
                exerciseDays++;
                totalExerciseMinutes += r.getExerciseMinutes();
            }
            if (r.getSystolicBp() != null) { systolicSum += r.getSystolicBp(); systolicCount++; }
            if (r.getDiastolicBp() != null) { diastolicSum += r.getDiastolicBp(); diastolicCount++; }

            if (r.getCondition() == Condition.GREAT || r.getCondition() == Condition.GOOD) {
                goodConditionDays++;
            } else if (r.getCondition() == Condition.BAD) {
                badConditionDays++;
            }
        }

        return HealthSummaryDto.builder()
                .year(year)
                .month(month)
                .recordedDays(recordedDays)
                .avgWeightKg(weightCount == 0 ? null : round1(weightSum / weightCount))
                .avgSleepHours(sleepCount == 0 ? null : round1(sleepSum / sleepCount))
                .totalSteps(stepsSum)
                .avgSteps(stepsCount == 0 ? 0 : Math.round((double) stepsSum / stepsCount))
                .exerciseDays(exerciseDays)
                .totalExerciseMinutes(totalExerciseMinutes)
                .avgSystolicBp(systolicCount == 0 ? null : round1(systolicSum / systolicCount))
                .avgDiastolicBp(diastolicCount == 0 ? null : round1(diastolicSum / diastolicCount))
                .goodConditionDays(goodConditionDays)
                .badConditionDays(badConditionDays)
                .build();
    }

    private boolean isRecorded(HealthRecord r) {
        return r.getWeightKg() != null || r.getSleepHours() != null || r.getSteps() != null
                || r.getExerciseMinutes() != null || r.getSystolicBp() != null
                || r.getDiastolicBp() != null || r.getCondition() != null
                || (r.getMemo() != null && !r.getMemo().isBlank());
    }

    private boolean isEmpty(HealthRecordDto dto) {
        return dto.getWeightKg() == null && dto.getSleepHours() == null && dto.getSteps() == null
                && dto.getExerciseMinutes() == null && dto.getSystolicBp() == null
                && dto.getDiastolicBp() == null && dto.getCondition() == null
                && (dto.getMemo() == null || dto.getMemo().isBlank());
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private HealthRecordDto toDto(HealthRecord r, LocalDate date) {
        if (r == null) {
            return HealthRecordDto.builder()
                    .recordDate(date)
                    .build();
        }
        return HealthRecordDto.builder()
                .id(r.getId())
                .recordDate(r.getRecordDate())
                .weightKg(r.getWeightKg())
                .sleepHours(r.getSleepHours())
                .steps(r.getSteps())
                .exerciseMinutes(r.getExerciseMinutes())
                .systolicBp(r.getSystolicBp())
                .diastolicBp(r.getDiastolicBp())
                .condition(r.getCondition())
                .memo(r.getMemo())
                .build();
    }
}
