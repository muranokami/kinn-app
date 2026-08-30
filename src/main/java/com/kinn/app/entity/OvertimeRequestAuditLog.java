package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 残業申請(OvertimeRequest)の申請・承認・却下・取り下げ操作の監査ログ。
 * AnnouncementAuditLogと同じ考え方(ドメインが違えば監査ログテーブルも分離する)で
 * 専用テーブルとして新設した。
 *
 * 「いつ・誰が・どの残業申請に対して・何をしたか」だけを記録するシンプルな構造
 * (companyId・氏名スナップショット等は持たない。会社・操作者の特定はovertimeRequestId /
 * actorUserIdから辿って確認する)。
 */
@Entity
@Table(name = "overtime_request_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequestAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作対象の残業申請ID(削除後もこの値自体は残る。OvertimeRequestへの外部キー制約は張らない) */
    @Column(name = "overtime_request_id", nullable = false)
    private Long overtimeRequestId;

    /** 操作を行った本人(申請者)または管理者のAppUser.id */
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 16)
    private OvertimeRequestAuditAction action;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    @PrePersist
    private void onCreate() {
        if (this.actedAt == null) {
            this.actedAt = LocalDateTime.now();
        }
    }
}
