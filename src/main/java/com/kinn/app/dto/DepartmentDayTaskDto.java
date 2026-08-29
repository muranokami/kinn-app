package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 部署内共有・日別タスク表示(⑤⑥⑦⑨⑩⑪⑬⑭⑮)。
 * 「同じ部署のユーザーには部署のタスクが見える」+「日付単位で確認できる」を1つのレスポンスで満たす。
 * ステータス別(⑬未対応/対応中/完了)・ユーザー別(⑭誰が何を担当しているか)の両方の見せ方を
 * 同時に返すため、フロント側は画面の都合でどちらの表示形式を使ってもよい。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentDayTaskDto {
    private LocalDate date;
    private Long departmentId;
    /** 部署未所属ユーザーの場合はnull(⑤の対象外) */
    private String departmentName;

    private TaskSummaryDto summary;

    private List<TaskDto> unresolved;
    private List<TaskDto> inProgress;
    private List<TaskDto> completed;

    /** ユーザー別グルーピング(⑭)。部署所属メンバー全員を含む(その日タスクが無いメンバーも0件で含む) */
    private List<UserTaskGroupDto> byUser;
}
