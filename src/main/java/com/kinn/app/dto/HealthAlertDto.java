package com.kinn.app.dto;

import com.kinn.app.entity.HealthAlertSeverity;
import com.kinn.app.entity.HealthAlertType;
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
public class HealthAlertDto {
    private Long id;
    private HealthAlertType alertType;
    private String alertTypeLabel;
    private HealthAlertSeverity severity;
    private String message;
    private LocalDate triggeredDate;
}
