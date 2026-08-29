package com.kinn.app.entity;

/**
 * 認証・アカウント操作系監査ログ({@link AccountSecurityLog})の操作種別。
 * health_audit_logのHealthAuditActionとは別ドメイン(健康情報ではなく認証情報)のため
 * 専用のenumとして分離している。
 */
public enum AccountSecurityAction {
    /** 管理者が対象社員のパスワードを強制的にリセットした */
    PASSWORD_RESET_BY_ADMIN,
    /** 本人が自分のパスワードを変更した(強制変更・任意変更のどちらも含む) */
    PASSWORD_CHANGED_BY_USER,
    /** 本人がセルフサービス型パスワードリセットを申請し、登録メールアドレス宛にリンクを送信した */
    PASSWORD_RESET_REQUESTED,
    /** 本人がメールのリンク(トークン)経由でパスワードリセットを完了した */
    PASSWORD_RESET_COMPLETED_VIA_EMAIL,
    /** 管理者本人がMFA(TOTP)を有効化した */
    MFA_ENABLED,
    /** 管理者本人がMFA(TOTP)を無効化した */
    MFA_DISABLED,
    /** ログイン時のMFAコード検証に成功し、ログインが完了した */
    MFA_LOGIN_SUCCESS,
    /** ログイン時のMFAコード検証に失敗した */
    MFA_LOGIN_FAILED
}
