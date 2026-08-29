package com.kinn.app.service;

import com.kinn.app.dto.HealthProfileDto;
import com.kinn.app.entity.HealthProfile;
import com.kinn.app.repository.HealthProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 健康プロフィール(身長・体重・血圧などのベースライン情報)を管理するサービス。
 * 構成は HealthService / AttendanceService に倣っている。
 */
@Service
public class HealthProfileService {

    private final HealthProfileRepository repository;

    public HealthProfileService(HealthProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public HealthProfileDto getProfile(String employeeId) {
        return repository.findByEmployeeId(employeeId)
                .map(this::toDto)
                .orElseGet(() -> HealthProfileDto.builder().build());
    }

    @Transactional
    public HealthProfileDto saveProfile(String employeeId, HealthProfileDto dto) {
        HealthProfile entity = repository.findByEmployeeId(employeeId)
                .orElseGet(HealthProfile::new);

        entity.setEmployeeId(employeeId);
        entity.setDepartment(dto.getDepartment());
        entity.setHeightCm(dto.getHeightCm());
        entity.setWeightKg(dto.getWeightKg());
        entity.setSystolicBp(dto.getSystolicBp());
        entity.setDiastolicBp(dto.getDiastolicBp());
        entity.setBodyTemperature(dto.getBodyTemperature());
        entity.setExerciseMinutes(dto.getExerciseMinutes());
        entity.setAvgSleepHours(dto.getAvgSleepHours());
        entity.setSmokingStatus(dto.getSmokingStatus());
        entity.setDrinkingStatus(dto.getDrinkingStatus());
        entity.setHealthMemo(dto.getHealthMemo());

        HealthProfile saved = repository.save(entity);
        return toDto(saved);
    }

    /**
     * 身長(cm)・体重(kg)からBMIを計算する。どちらか欠けている、または
     * 身長が0以下の場合はnullを返す。
     */
    public static Double calcBmi(Double heightCm, Double weightKg) {
        if (heightCm == null || weightKg == null || heightCm <= 0) return null;
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        return Math.round(bmi * 10.0) / 10.0;
    }

    private HealthProfileDto toDto(HealthProfile p) {
        return HealthProfileDto.builder()
                .id(p.getId())
                .department(p.getDepartment())
                .heightCm(p.getHeightCm())
                .weightKg(p.getWeightKg())
                .bmi(calcBmi(p.getHeightCm(), p.getWeightKg()))
                .systolicBp(p.getSystolicBp())
                .diastolicBp(p.getDiastolicBp())
                .bodyTemperature(p.getBodyTemperature())
                .exerciseMinutes(p.getExerciseMinutes())
                .avgSleepHours(p.getAvgSleepHours())
                .smokingStatus(p.getSmokingStatus())
                .drinkingStatus(p.getDrinkingStatus())
                .healthMemo(p.getHealthMemo())
                .build();
    }
}
