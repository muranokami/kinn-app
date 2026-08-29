package com.kinn.app.entity;

/**
 * 健康管理監査ログの操作種別。
 * AttendanceAudit(勤怠修正専用の個別実装)とは異なり、健康管理は閲覧を含む
 * 多数の操作を横断的に記録するため、まず「何をしたか」を4種類に正規化する。
 */
public enum HealthAuditAction {
    VIEW,
    CREATE,
    UPDATE,
    DELETE
}
