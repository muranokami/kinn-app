package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 認証・アカウント操作系の監査ログ(パスワードリセット・パスワード変更など)。
 * 既存の health_audit_log(健康情報の閲覧・更新を対象とするドメイン別の監査ログ)とは
 * 目的が異なるため、専用のテーブルとして分離している。
 *
 * HealthAuditLogと同じ考え方で、秘密情報(パスワードそのもの・トークン等)は一切保存せず、
 * 「いつ・誰が・誰に対して・何をしたか」という事実だけを記録する(PasswordServiceのjavadoc参照)。
 */
@Entity
@Table(name = "account_security_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSecurityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作対象ユーザーの実効ID("companyId|loginId"形式) */
    @Column(name = "target_employee_id", nullable = false, length = 64)
    private String targetEmployeeId;

    /** 操作対象ユーザーの氏名(記録時点のスナップショット) */
    @Column(name = "target_name", length = 100)
    private String targetName;

    /**
     * 操作を行った人の実効ID。管理者操作(PASSWORD_RESET_BY_ADMIN)の場合のみ設定される。
     * 本人操作(PASSWORD_CHANGED_BY_USER)は対象=行為者が自明なためnullのまま
     * (targetEmployeeIdと重複させない)。
     */
    @Column(name = "performed_by_employee_id", length = 64)
    private String performedByEmployeeId;

    /** 操作を行った人の氏名(記録時点のスナップショット。管理者操作の場合のみ設定) */
    @Column(name = "performed_by_name", length = 100)
    private String performedByName;

    @Enumerated(EnumType.STRING)
    // length=64: PASSWORD_RESET_COMPLETED_VIA_EMAIL(34文字)のような長い値を見込んで余裕を持たせる
    // (spring.jpa.hibernate.ddl-auto=updateは既存列の型変更を確実には行わないため、
    // AccountSecurityActionMigrationRunnerで明示的にALTER COLUMNしている)
    @Column(name = "action_type", nullable = false, length = 64)
    private AccountSecurityAction actionType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    private void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
