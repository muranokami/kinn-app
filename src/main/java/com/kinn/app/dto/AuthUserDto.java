package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** ログイン中ユーザーの表示用情報(パスワード等は一切含まない) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthUserDto {
    /**
     * AppUser.id。部署共有スケジュールの登録者本人かどうか(⑭⑯)を画面側で判定するために公開する。
     * パスワード等の機微情報ではなく、社内向けの内部IDに過ぎないため公開して問題ない。
     */
    private Long userId;
    private String loginId;
    private String fullName;
    private String companyName;
    /**
     * 自社の会社コード。同僚の入社登録に必要なため管理者(ADMIN)にのみ返す
     * (一般ユーザーは自分で新規登録することも他人を招待することも無いため、
     * 不必要な露出を避ける。AuthService#toDto参照)。新しく会社を登録した直後の
     * レスポンスでは、その場でADMIN権限が付与されるため自動的にここに含まれる。
     */
    private String companyCode;
    private String departmentName;
    private String role;
    /** 今回ログインした日時(トップページ表示用)。/api/auth/me でも同じ値を返し、他画面遷移後も表示できる */
    private LocalDateTime lastLoginAt;
    /**
     * trueの間はパスワード変更が完了するまで他画面へ進めない(MustChangePasswordFilter参照)。
     * ログインAPI・change-passwordAPIのレスポンスがこれを返すことで、フロントエンドは
     * index.htmlを経由せず直接change-password.htmlへ遷移させることができる。
     */
    private boolean mustChangePassword;
}
