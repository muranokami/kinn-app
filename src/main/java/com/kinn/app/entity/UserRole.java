package com.kinn.app.entity;

/**
 * アプリ内権限。既存の管理者画面(admin-attendance.html / admin-employees.html等)を
 * 保護するために使う。
 *
 * 将来 ROLE_SUPER_ADMIN(会社をまたいだ全社管理者)などを追加する場合は、ここに列挙値を
 * 1件足すだけでよい(SecurityConfigのhasRole判定・AppUserPrincipal#getAuthoritiesは
 * このenumの値をそのまま "ROLE_"+name() として使うため、他のコード変更は不要)。
 */
public enum UserRole {
    USER("一般ユーザー"),
    ADMIN("管理者");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
