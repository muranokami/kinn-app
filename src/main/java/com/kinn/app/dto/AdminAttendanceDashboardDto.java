package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 管理者ダッシュボード(勤怠)。健康・食事の個人情報は含まない。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAttendanceDashboardDto {
    private String companyName;
    /** 部署で絞り込んだ場合のみ設定される(全部署対象の場合はnull) */
    private String departmentName;
    private int employeeCount;

    /** 本日出勤人数(出勤打刻あり) */
    private int presentTodayCount;
    /** 本日欠勤人数(勤務区分=通常勤務日だが出退勤の記録がない) */
    private int absentTodayCount;
    /** 本日休日勤務人数(所定休日または法定休日に出勤している) */
    private int holidayWorkTodayCount;
    /** 未打刻人数(出勤のみで退勤未打刻、または出退勤とも未打刻) */
    private int unclockedCount;

    /** 未退勤者の氏名一覧 */
    private List<String> missingClockOutNames;
    /** 未出勤(出勤打刻なし。かつ通常勤務日/休日出勤の意思があるはずの日を除く簡易判定)の氏名一覧 */
    private List<String> missingClockInNames;

    /** 今月の総実働時間(時間) */
    private double monthTotalWorkingHours;
    /** 今月の総残業時間(時間) */
    private double monthTotalOvertimeHours;
    /** 今月の通常残業時間(時間) */
    private double monthRegularOvertimeHours;
    /** 今月の所定休日残業時間(時間) */
    private double monthScheduledHolidayOvertimeHours;
    /** 今月の法定休日残業時間(時間) */
    private double monthStatutoryHolidayOvertimeHours;
}
