package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ユーザー別(㉗)・部署別(㉘)進捗の1行。どちらも「名前+3ステータスの件数」という
 * 同じ形なので共通のDTOにまとめる(idはユーザー別ならuserId、部署別ならdepartmentId)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskProgressRowDto {
    private Long id;
    private String name;
    private int unresolvedCount;
    private int inProgressCount;
    private int completedCount;
}
