package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAnalysisDto {
    private String period;
    private LocalDate from;
    private LocalDate to;
    private List<HealthAnalysisPointDto> points;
    private HealthAnalysisSummaryDto summary;
}
