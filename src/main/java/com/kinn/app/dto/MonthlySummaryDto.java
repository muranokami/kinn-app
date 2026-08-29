package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 月次の勤怠集計。要件にある12項目をすべて持つ。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySummaryDto {

    private int year;
    private int month;

    /** 勤務日数(日) */
    private double workingDays;
    /** 勤務時間(時間) */
    private double workingHours;
    /** 残業時間(時間) */
    private double overtimeHours;
    /** 深夜残業(時間) */
    private double nightOvertimeHours;
    /** 遅早回数(回) */
    private int lateOrEarlyCount;
    /** 欠勤日数(日) */
    private double absenceDays;
    /** 所定休日勤務(日) */
    private double scheduledHolidayWorkDays;
    /** 所定休日深夜(時間) */
    private double scheduledHolidayNightHours;
    /** 有給使用日数(日) */
    private double paidLeaveDays;
    /** 振休代休(日) */
    private double substituteOrCompensatoryDays;
    /** 法定休日勤務(日) */
    private double statutoryHolidayWorkDays;
    /** 法定休日深夜(時間) */
    private double statutoryHolidayNightHours;

    // ---- 以下、休日区分の明確化にともない追加した項目 ----

    /** 所定労働時間(時間)。その月のカレンダー上で通常勤務日となる日数 × 8時間(実績ではなく計画値) */
    private double scheduledWorkingHours;
    /** 通常勤務時間(時間)。通常勤務日の実働のうち残業を除いた部分(= workingHours - overtimeHours) */
    private double regularHours;
    /** 所定休日勤務時間(時間)。所定休日に働いた時間の合計(日数ではなく時間) */
    private double scheduledHolidayWorkHours;
    /** 法定休日勤務時間(時間)。法定休日に働いた時間の合計(日数ではなく時間) */
    private double statutoryHolidayWorkHours;
    /** 深夜勤務時間(時間)。通常/所定休日/法定休日を問わず深夜帯(22:00〜翌5:00)に働いた時間の合計 */
    private double totalNightHours;
    /** 総実働時間(時間)。通常勤務+残業+所定休日勤務+法定休日勤務の合計 */
    private double totalActualWorkingHours;

    // ---- 以下、所定休日・法定休日の残業時間の分離にともない追加した項目 ----

    /** 所定休日残業時間(時間)。所定休日勤務時間のうち所定労働時間=8時間を超えた部分の合計 */
    private double scheduledHolidayOvertimeHours;
    /** 法定休日残業時間(時間)。法定休日勤務時間のうち所定労働時間=8時間を超えた部分の合計 */
    private double statutoryHolidayOvertimeHours;
    /** 総残業時間(時間)。通常残業(overtimeHours)+所定休日残業+法定休日残業の合計 */
    private double totalOvertimeHours;
}
