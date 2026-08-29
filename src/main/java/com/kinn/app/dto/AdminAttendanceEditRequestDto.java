package com.kinn.app.dto;

import com.kinn.app.entity.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** 管理者による勤怠修正リクエスト。既存AttendanceRecordDtoのうち修正可能な項目のみを持つ。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAttendanceEditRequestDto {
    private DayType dayType;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer breakMinutes;
    private Boolean lateOrEarly;
    private String remarks;
}
