package com.kinn.app.entity;

/**
 * お知らせ監査ログ({@link AnnouncementAuditLog})の操作種別。
 * account_security_logのAccountSecurityActionとは別ドメイン(アカウント操作ではなく
 * お知らせというコンテンツの操作)のため専用のenumとして分離している。
 */
public enum AnnouncementAuditAction {
    /** 管理者が新規にお知らせを投稿した */
    CREATE,
    /** 管理者がお知らせを編集した */
    UPDATE,
    /** 管理者がお知らせを削除した */
    DELETE
}
