package com.kinn.app.dto;

import com.kinn.app.entity.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAuditDto {
    private LocalDate workDate;
    private String editedByName;
    private LocalDateTime editedAt;

    private DayType previousDayType;
    private LocalTime previousStartTime;
    private LocalTime previousEndTime;
    private Integer previousBreakMinutes;

    private DayType newDayType;
    private LocalTime newStartTime;
    private LocalTime newEndTime;
    private Integer newBreakMinutes;
}
