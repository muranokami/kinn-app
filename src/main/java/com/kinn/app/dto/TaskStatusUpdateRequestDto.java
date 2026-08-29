package com.kinn.app.dto;

import com.kinn.app.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ステータス変更専用リクエスト(⑤)。プルダウン・「対応開始」「完了」ボタンの両方から使う */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusUpdateRequestDto {
    private TaskStatus status;
}
