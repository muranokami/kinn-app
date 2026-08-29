package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * ログインユーザー本人の締め切りアラートまとめ。
 * トップページは件数(dueTodayCount/overdueCount)だけを軽量に表示し、
 * タスク管理画面は items を使って該当タスクを一覧の先頭に表示する。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAlertsDto {
    private int dueTodayCount;
    private int overdueCount;
    private List<TaskAlertDto> items;
}
