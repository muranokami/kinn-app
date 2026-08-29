package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 一般ユーザーの「マイタスク」画面(③㉞)。未対応/対応中/完了の3列で返す */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyTaskBoardDto {
    private List<TaskDto> unresolved;
    private List<TaskDto> inProgress;
    private List<TaskDto> completed;
    private TaskSummaryDto summary;
}
