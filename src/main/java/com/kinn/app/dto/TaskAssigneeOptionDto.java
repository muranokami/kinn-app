package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * タスク登録時の「担当者」選択肢(②同じ部署のユーザーへ依頼できる)。
 * 一般ユーザーにも安全に公開できるよう、氏名以外の個人情報は一切含まない
 * (管理者専用のEmployeeSummaryDtoとは別に、あえて最小限の項目だけを持つDTOにしている)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAssigneeOptionDto {
    private Long userId;
    private String fullName;
}
