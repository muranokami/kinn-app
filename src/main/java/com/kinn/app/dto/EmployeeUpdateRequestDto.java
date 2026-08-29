package com.kinn.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理者による従業員の基本情報編集(氏名・役職)。
 * 権限(role)・所属部署(departmentId)は既存の専用エンドポイント
 * (PUT /{id}/role, PUT /{id}/department)で扱うため、ここには含めない。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeUpdateRequestDto {

    @NotBlank(message = "氏名を入力してください")
    private String fullName;

    /** 任意。空文字はnull扱いにする */
    private String position;

    /**
     * メールアドレス(任意、空文字はnull扱い)。セルフサービス型パスワードリセットに使う。
     * 新規登録では必須だが、既存ユーザーの後埋め・修正のためここでは任意項目とする。
     */
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;
}
