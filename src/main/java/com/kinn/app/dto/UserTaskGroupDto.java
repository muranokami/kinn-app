package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** ユーザー別グルーピング(⑭部署全体の日別表示)。指定日にそのユーザーが担当するタスク一覧 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTaskGroupDto {
    private Long userId;
    private String userName;
    private List<TaskDto> tasks;
}
