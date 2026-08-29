package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * トップページの休憩ボタンの表示に必要な情報をまとめたレスポンス。
 * GET /api/attendance/break/status、POST .../break/start、POST .../break/end のいずれも
 * この形式を返す(状態遷移のたびに同じ形でフロントへ渡し、描画ロジックを共通化するため)。
 *
 * 休憩は1日に何回でも分けて取れる(刻んで取る運用に対応)。ただし合計は
 * breakDurationMinutes(既定60分)を超えない。usedMinutesToday/remainingBudgetMinutesで
 * 「今日はあと何分休憩を取れるか」を表す。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakStatusDto {
    private BreakStatus status;
    private LocalDate workDate;

    /** 直近(または進行中)の休憩セグメントの開始時刻 */
    private LocalTime breakStartTime;
    /** 進行中セグメントの終了予定時刻(本日の残り休憩予算ぶんだけ進んだ時刻。ON_BREAKのみ設定) */
    private LocalTime scheduledEndTime;
    /** 直近の休憩セグメントの実際の終了時刻 */
    private LocalTime breakEndTime;

    /** 休憩の1日あたりの上限(分)。本番は60、テスト環境ではapp.attendance.break-minutesで短縮できる */
    private int breakDurationMinutes;
    /** 進行中セグメントの残り秒数(ON_BREAK以外は0) */
    private int remainingSeconds;
    /** 本日ここまでに確定した休憩時間の合計(分)。何回に分けて取っても合計値 */
    private Integer actualBreakMinutes;

    /** 本日使用済みの休憩時間(分)。進行中セグメントの分は含まない(確定した分だけ) */
    private int usedMinutesToday;
    /** 本日あと何分休憩を取れるか(breakDurationMinutes - usedMinutesToday、0未満にはならない) */
    private int remainingBudgetMinutes;

    /** 画面にそのまま出せる案内文(任意) */
    private String message;
}
