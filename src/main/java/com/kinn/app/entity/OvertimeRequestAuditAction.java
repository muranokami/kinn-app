package com.kinn.app.entity;

/**
 * 残業申請監査ログ({@link OvertimeRequestAuditLog})の操作種別。
 * AnnouncementAuditActionと同じ考え方で、ドメインごとに専用のenumとして分離している。
 */
public enum OvertimeRequestAuditAction {
    /** 本人が新規に残業申請を行った */
    CREATE,
    /** 管理者が承認した */
    APPROVE,
    /** 管理者が却下した */
    REJECT,
    /** 本人が(承認待ちの間に)取り下げた */
    WITHDRAW
}
