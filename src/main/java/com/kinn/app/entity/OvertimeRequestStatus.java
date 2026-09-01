package com.kinn.app.entity;

/**
 * 残業申請({@link OvertimeRequest})のステータス。TaskStatus/AnnouncementImportanceと
 * 同じ考え方で、状態をEnumで表現する。
 *
 * 「取り下げ」はここに状態を持たず、申請そのものを削除することで表現する
 * (OvertimeRequestService#withdraw参照。AnnouncementService#deleteByAdminと同じ考え方)。
 */
public enum OvertimeRequestStatus {
    /** 申請中(管理者の承認待ち) */
    PENDING("承認待ち"),
    /** 管理者が承認済み */
    APPROVED("承認済み"),
    /** 管理者が却下済み */
    REJECTED("却下");

    private final String label;

    OvertimeRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
