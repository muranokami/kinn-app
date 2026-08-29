package com.kinn.app.audit;

/** {@link Audited#target()} が取り得る値。操作対象ユーザーの決め方を表す。 */
public enum AuditTarget {
    /** 操作対象 = 操作を行った本人(既存の健康管理APIはほぼ全てこれ)。 */
    SELF,
    /** 個人に紐付かない集計閲覧(管理者ダッシュボードなど)。target_employee_id は null になる。 */
    NONE
}
