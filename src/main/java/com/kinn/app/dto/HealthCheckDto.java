package com.kinn.app.dto;

import com.kinn.app.entity.HealthConditionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheckDto {
    private Long id;
    private LocalDate checkDate;
    private HealthConditionLevel condition;
    private Double sleepHours;
    private Integer fatigueLevel;
    private Integer exerciseMinutes;
    private Double bodyTemperature;
    private String memo;
}
