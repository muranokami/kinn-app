package com.kinn.app.dto;

import com.kinn.app.entity.DayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecordDto {
    private Long id;
    private LocalDate workDate;
    private DayType dayType;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer breakMinutes;
    /** トップページの自動休憩機能が記録した休憩開始時刻(手動入力のみの日はnull) */
    private LocalTime breakStartTime;
    /** トップページの自動休憩機能が記録した休憩終了時刻(手動入力のみの日、または休憩中はnull) */
    private LocalTime breakEndTime;
    private Boolean lateOrEarly;
    private Double paidLeaveUnit;
    private String remarks;

    /** この日の実働時間(分)。表示用に計算済みの値を返す */
    private Integer workMinutes;
    /** この日の通常残業時間(分。通常勤務日のみ。所定労働時間=8時間を超えた部分) */
    private Integer overtimeMinutes;
    /** この日の深夜労働時間(分) */
    private Integer nightMinutes;

    /** 通常勤務時間(分。通常勤務日の実働のうち所定労働時間=8時間までの部分。残業は含まない) */
    private Integer regularWorkMinutes;
    /** 所定休日勤務時間(分。dayType=SCHEDULED_HOLIDAYの日に働いた時間の合計。残業分を含む) */
    private Integer companyHolidayWorkMinutes;
    /** 法定休日勤務時間(分。dayType=STATUTORY_HOLIDAYの日に働いた時間の合計。残業分を含む) */
    private Integer statutoryHolidayWorkMinutes;

    /** 所定休日残業時間(分。所定休日勤務時間のうち所定労働時間=8時間を超えた部分) */
    private Integer scheduledHolidayOvertimeMinutes;
    /** 法定休日残業時間(分。法定休日勤務時間のうち所定労働時間=8時間を超えた部分) */
    private Integer statutoryHolidayOvertimeMinutes;
    /** 総残業時間(分。通常残業+所定休日残業+法定休日残業の合計) */
    private Integer totalOvertimeMinutes;

    /** 日本の祝日かどうか(勤務区分=dayTypeとは独立したカレンダー上の事実) */
    private Boolean isPublicHoliday;
    /** 祝日名(祝日でない場合はnull。例: "山の日") */
    private String holidayName;
    /** 曜日ラベル(例: "月")。画面表示の便宜のためサーバー側でも計算して返す */
    private String dayOfWeekLabel;
}
