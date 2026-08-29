package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 1日分の勤怠実績データ。
 * 集計(勤務時間・残業時間など)はここでは持たず、AttendanceService 側で
 * 都度計算する(集計ロジックを1箇所にまとめるため)。
 */
@Entity
@Table(
        name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 32)
    @Builder.Default
    private DayType dayType = DayType.NORMAL;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** 休憩時間(分)。トップページの自動休憩機能・勤怠画面の手動入力のどちらも、最終的にはこの値を書き換える */
    @Column(name = "break_minutes")
    @Builder.Default
    private Integer breakMinutes = 0;

    /**
     * トップページの自動休憩機能(休憩開始ボタン)が記録する休憩開始時刻。
     * 手動入力の休憩(分)とは別の実測値として、その日1回分の休憩セッションを表す
     * (breakEndTimeがnullの間は「休憩中」、両方埋まっていれば「その日の休憩は終了済み」)。
     * 既存のbreak_minutes列(合計分数)は壊さず、休憩終了時にこの実測時間から算出して上書きする。
     */
    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    /** 自動休憩機能が記録する休憩終了時刻(60分経過による自動終了、または手動での早期終了) */
    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    /**
     * 自動休憩機能(休憩開始ボタン)だけで本日ここまでに消費した休憩時間(分。上限=既定60分)。
     * break_minutes(勤怠画面での手動入力も含む合計。実働時間計算が参照する値)とは意図的に
     * 分離している: これが無いと、勤怠画面で昼休憩などをあらかじめ手入力していた日に、
     * 自動休憩機能側が「本日の予算を使い切った」と誤認してボタンを表示できなくなってしまう
     * (手動入力の休憩時間と、このボタンの1日60分という予算はまったく別物として扱う)。
     * 休憩終了のたびにこの列とbreak_minutesの両方へ同じ分数を加算する。
     */
    @Column(name = "auto_break_minutes")
    @Builder.Default
    private Integer autoBreakMinutes = 0;

    /** 遅刻または早退があったか */
    @Column(name = "late_or_early")
    @Builder.Default
    private Boolean lateOrEarly = false;

    /**
     * 有給を使用した単位(1.0=全休, 0.5=半休)。
     * dayType が PAID_LEAVE のときのみ意味を持つ。
     */
    @Column(name = "paid_leave_unit")
    @Builder.Default
    private Double paidLeaveUnit = 1.0;

    @Column(name = "remarks", length = 255)
    private String remarks;
}
