package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * お知らせ(Announcement)の投稿・編集・削除操作の監査ログ。
 *
 * 【既存のaccount_security_logへ相乗りさせず専用テーブルにした理由】
 * account_security_log(認証・アカウント操作系)は target_employee_id / target_name という、
 * 「操作対象がアカウント(人)であること」を前提にした列構成になっている
 * (AccountSecurityLogのjavadoc参照)。一方お知らせ操作の「対象」はコンテンツ(お知らせ1件)
 * であって人ではないため、無理にaccount_security_logへ寄せると意味の異なる列
 * (targetEmployeeIdにお知らせIDを詰める、等)を使い回すことになり、可読性・整合性を損なう。
 * health_audit_logがaccount_security_logとは別テーブルに分離されているのと同じ考え方
 * (ドメインが違えば監査ログテーブルも分離する、という既存方針)を踏襲し、
 * announcement_audit_log を専用テーブルとして新設した。
 *
 * 「いつ・誰が・どの会社の・どのお知らせに対して・何をしたか」を記録する。
 * お知らせ本文までは保存せず、削除後も操作対象を追えるようタイトルのみスナップショットする
 * (AccountSecurityLogがtargetName等をスナップショットするのと同じ考え方)。
 */
@Entity
@Table(name = "announcement_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ログを分離する会社ID。必ず操作を行った管理者自身のcompanyIdから設定する */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 操作対象のお知らせID(削除後もこの値自体は残る。Announcementへの外部キー制約は張らない) */
    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    /** 操作対象のお知らせタイトル(記録時点のスナップショット。削除後も事実を追えるようにする) */
    @Column(name = "announcement_title", length = 200)
    private String announcementTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private AnnouncementAuditAction actionType;

    /** 操作を行った管理者の実効ID("companyId|loginId"形式) */
    @Column(name = "performed_by_employee_id", nullable = false, length = 64)
    private String performedByEmployeeId;

    /** 操作を行った管理者の氏名(記録時点のスナップショット) */
    @Column(name = "performed_by_name", length = 100)
    private String performedByName;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    private void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
