package com.kinn.app.service;

import com.kinn.app.dto.HealthCheckDto;
import com.kinn.app.entity.HealthCheck;
import com.kinn.app.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 「今日の体調チェック」の登録・履歴取得を担当するサービス。
 */
@Service
public class HealthCheckService {

    private final HealthCheckRepository repository;

    public HealthCheckService(HealthCheckRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public HealthCheckDto getByDate(String employeeId, LocalDate date) {
        return repository.findByEmployeeIdAndCheckDate(employeeId, date)
                .map(this::toDto)
                .orElseGet(() -> HealthCheckDto.builder().checkDate(date).build());
    }

    @Transactional(readOnly = true)
    public List<HealthCheckDto> getHistory(String employeeId, LocalDate from, LocalDate to) {
        return repository.findByEmployeeIdAndCheckDateBetweenOrderByCheckDateAsc(employeeId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public HealthCheckDto save(String employeeId, HealthCheckDto dto) {
        HealthCheck entity = repository.findByEmployeeIdAndCheckDate(employeeId, dto.getCheckDate())
                .orElseGet(HealthCheck::new);

        entity.setEmployeeId(employeeId);
        entity.setCheckDate(dto.getCheckDate());
        entity.setCondition(dto.getCondition());
        entity.setSleepHours(dto.getSleepHours());
        entity.setFatigueLevel(dto.getFatigueLevel());
        entity.setExerciseMinutes(dto.getExerciseMinutes());
        entity.setBodyTemperature(dto.getBodyTemperature());
        entity.setMemo(dto.getMemo());

        HealthCheck saved = repository.save(entity);
        return toDto(saved);
    }

    private HealthCheckDto toDto(HealthCheck c) {
        return HealthCheckDto.builder()
                .id(c.getId())
                .checkDate(c.getCheckDate())
                .condition(c.getCondition())
                .sleepHours(c.getSleepHours())
                .fatigueLevel(c.getFatigueLevel())
                .exerciseMinutes(c.getExerciseMinutes())
                .bodyTemperature(c.getBodyTemperature())
                .memo(c.getMemo())
                .build();
    }
}
