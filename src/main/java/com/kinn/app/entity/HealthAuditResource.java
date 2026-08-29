package com.kinn.app.entity;

/**
 * 健康管理監査ログの対象リソース種別。既存の /api/health/**, /api/admin/health/** の
 * エンドポイントに1対1で対応する(AUDIT_LOGのみ例外で、監査ログ自体の閲覧を指す)。
 */
public enum HealthAuditResource {
    /** 健康プロフィール(身長・体重・血圧などのベースライン情報) */
    PROFILE,
    /** 今日の体調チェック */
    DAILY_CHECK,
    /** 体調チェック履歴 */
    HISTORY,
    /** 健康スコア */
    SCORE,
    /** 健康スコア・体重・睡眠などの推移グラフ */
    TREND,
    /** 健康アラート */
    ALERT,
    /** 勤怠×健康分析 */
    ANALYSIS,
    /** 月次健康記録(従来画面。日次データの一括保存・1日分保存/削除を含む) */
    MONTHLY_RECORD,
    /** 管理者向け健康ダッシュボード(会社・部署単位の集計。個人非紐付け) */
    ADMIN_DASHBOARD,
    /** 監査ログそのものの閲覧(自分専用ログ画面・管理者検索画面) */
    AUDIT_LOG
}
