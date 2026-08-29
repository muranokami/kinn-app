package com.kinn.app.entity;

/** 健康管理監査ログの処理結果。 */
public enum HealthAuditResult {
    /** 正常終了 */
    SUCCESS,
    /** 認可エラー(権限不足)による失敗 */
    DENIED,
    /** その他の異常終了 */
    FAILURE
}
