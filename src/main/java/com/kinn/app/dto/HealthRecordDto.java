package com.kinn.app.dto;

import com.kinn.app.entity.Condition;
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
public class HealthRecordDto {
    private Long id;
    private LocalDate recordDate;
    private Double weightKg;
    private Double sleepHours;
    private Integer steps;
    private Integer exerciseMinutes;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Condition condition;
    private String memo;
}
