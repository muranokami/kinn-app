package com.kinn.app.dto;

import com.kinn.app.entity.TaskPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 締め切りアラート1件分(本日締め切り or 期限切れの、ログインユーザー本人が担当するタスク)。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAlertDto {
    private Long id;
    private String title;
    private LocalDate dueDate;
    private TaskPriority priority;
    private String priorityLabel;
    private TaskAlertKind kind;
    private String kindLabel;
}
