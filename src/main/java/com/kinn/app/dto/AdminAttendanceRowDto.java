package com.kinn.app.dto;

import com.kinn.app.entity.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** 管理者向け勤怠一覧の1行(社員1人・1日分)。個人の詳細な勤怠(出退勤・残業内訳)を表示する。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAttendanceRowDto {
    private String employeeId;
    /** AppUser.id。従業員別カレンダー画面(admin-attendance.html?userId=)へのリンクに使う */
    private Long userId;
    /** 従業員の氏名。一覧に生のemployeeId("会社ID|ログインID")を出すと見づらいため、表示用に持たせる */
    private String fullName;
    private Long departmentId;
    private String departmentName;

    private LocalDate workDate;
    private String dayOfWeekLabel;
    private DayType dayType;
    private String dayTypeLabel;
    private String holidayName;

    private LocalTime startTime;
    private LocalTime endTime;

    /** 実働時間(分) */
    private Integer workMinutes;
    /** 通常残業(分。通常勤務日のみ) */
    private Integer overtimeMinutes;
    /** 所定休日残業(分) */
    private Integer scheduledHolidayOvertimeMinutes;
    /** 法定休日残業(分) */
    private Integer statutoryHolidayOvertimeMinutes;
    /** 総残業(分。上記3種の合計) */
    private Integer totalOvertimeMinutes;
}
