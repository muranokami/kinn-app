package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理者向けタスク一覧(⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳㉖)。
 * 部署・ユーザー・依頼者・ステータス・優先度・日付の絞り込み結果と集計をまとめて返す。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminTaskListDto {
    /** null = 全部署(会社全体)を表示中 */
    private Long departmentId;
    private String departmentName;
    /** 日付絞り込み時のみ設定(⑰⑱)。null = 日付絞り込みなし(全期間) */
    private LocalDate date;
    private TaskSummaryDto summary;
    private List<TaskDto> tasks;
}
