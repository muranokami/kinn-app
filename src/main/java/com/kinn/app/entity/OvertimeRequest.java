package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 残業申請(事前申請制)のEntity。
 *
 * Company → Department → AppUser という既存の階層にそのまま従い、Task/Announcementと同じ
 * 設計方針で @ManyToOne は使わずFKカラム(companyId/departmentId/applicantUserId/
 * approverUserId)のみで参照する。companyId は必ず申請者自身の会社から解決し、
 * departmentId は申請者自身の所属部署をサーバー側で自動設定する(リクエストから
 * departmentIdを受け取らないため、他社・他部署のdepartmentIdを紛れ込ませる余地がない)。
 *
 * 「◯月◯日に◯時間の残業を予定している」という事前申請を承認するだけの機能であり、
 * AttendanceRecordの実績(実際の打刻から計算されるovertimeMinutes)は一切書き換えない。
 * 実績と申請の食い違いはそれ自体を許容し、両方を並べて確認できれば十分とする(要件どおり)。
 *
 * 取り下げ(WITHDRAW)は専用のstatusを持たず、申請そのものを削除して表現する
 * (OvertimeRequestService#withdraw参照)。
 */
@Entity
@Table(name = "overtime_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 申請者の所属部署(集計・絞り込み用)。申請者が部署未所属の場合はnull */
    @Column(name = "department_id")
    private Long departmentId;

    /** 申請者のAppUser.id */
    @Column(name = "applicant_user_id", nullable = false)
    private Long applicantUserId;

    /** 対象日(残業を予定している日) */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    /** 予定残業時間(分) */
    @Column(name = "planned_minutes", nullable = false)
    private Integer plannedMinutes;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private OvertimeRequestStatus status = OvertimeRequestStatus.PENDING;

    /** 承認/却下を行った管理者のAppUser.id。未処理(PENDING)の間はnull */
    @Column(name = "approver_user_id")
    private Long approverUserId;

    /** 承認日時。承認された場合のみ設定する(却下の場合はnullのまま。却下日時はupdatedAtで代替する) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 却下理由。却下された場合のみ設定する */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = OvertimeRequestStatus.PENDING;
        }
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
