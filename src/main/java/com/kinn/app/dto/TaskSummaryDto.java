package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** タスクの状態別集計(㉖)。管理者画面上部のダッシュボードに使う */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskSummaryDto {
    private int totalCount;
    private int unresolvedCount;
    private int inProgressCount;
    private int completedCount;
    /** 期限超過(⑮)。未完了かつ期限が今日より前のタスク件数 */
    private int overdueCount;
}
