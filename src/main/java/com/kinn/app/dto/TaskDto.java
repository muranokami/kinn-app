package com.kinn.app.dto;

import com.kinn.app.entity.TaskPriority;
import com.kinn.app.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * タスク1件(㉙の登録項目 + 一覧・詳細表示に必要な項目)。登録・更新リクエストのボディにも
 * レスポンスにもこのDTOをそのまま使う(ScheduleEventDto/DepartmentScheduleEventDtoと同じ方針)。
 *
 * departmentId/assignedUserId は「誰が呼ぶか」でリクエスト時の扱いが変わる(㊳非常に重要):
 * <ul>
 *   <li>一般ユーザー自身のAPI(TaskController): サーバー側で常に自分の会社・部署・自分自身に
 *       上書きするため、この2項目に何を入れて送っても無視される(⑨⑮)。</li>
 *   <li>管理者API(AdminTaskController): この2項目を実際の割り当て先として読み取るが、
 *       必ずTaskService側で「自社の部署か」「その部署に本当に所属する担当者か」を確認してから
     *       使う(⑦⑳㊲「別部署のユーザーを指定」エラーの根拠)。</li>
 * </ul>
 * id/departmentName/assignedUserName/createdByUserId/createdByName/statusLabel/priorityLabel/
 * overdue/createdAt/updatedAt はレスポンス専用(リクエスト時は無視される)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDto {
    private Long id;

    private Long departmentId;
    private String departmentName;

    private Long assignedUserId;
    private String assignedUserName;

    private Long createdByUserId;
    private String createdByName;

    private String title;
    /** 仕事内容 */
    private String description;

    private TaskStatus status;
    private String statusLabel;

    private TaskPriority priority;
    private String priorityLabel;

    private LocalDate startDate;
    private LocalDate dueDate;

    /** 備考 */
    private String notes;

    /** 期限超過(⑮)。dueDateが今日より前、かつ未完了の場合にtrue。保存はせず取得のたびに計算する */
    private boolean overdue;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
