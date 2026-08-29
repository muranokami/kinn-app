package com.kinn.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理者が自社の従業員を新規登録するためのリクエスト(所属会社は選択不可。常に管理者自身の会社になる)。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCreateEmployeeRequestDto {

    @NotBlank(message = "ユーザーIDを入力してください")
    private String loginId;

    @NotBlank(message = "パスワードを入力してください")
    private String password;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;

    @NotBlank(message = "氏名を入力してください")
    private String fullName;

    /** 所属部署(任意。管理者自身の会社に存在する部署のみ指定可能) */
    private Long departmentId;
    private String position;

    /** メールアドレス(任意)。パスワードを忘れた際のセルフサービスリセットに使う */
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;

    /** "USER" または "ADMIN"。未指定時はUSER */
    private String role;
}
