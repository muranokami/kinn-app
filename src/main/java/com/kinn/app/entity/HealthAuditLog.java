package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 健康管理機能の監査ログ。AttendanceAudit(「修正前後の事実をそのまま保存する」という
 * 勤怠修正専用の個別実装)の設計思想を踏襲しつつ、対象操作が閲覧を含め多数にわたるため
 * Controllerごとの個別呼び出しではなく AOP({@link com.kinn.app.audit.HealthAuditAspect})
 * による横断的な記録方式にしている。1回のAPI呼び出しにつき1行、操作の「事実」だけを記録し、
 * 健康情報の値そのもの(体重・血圧・体調メモなど)は一切保存しない。
 *
 * マルチカンパニー対応のため companyId を必ず持ち、検索・閲覧は必ずこの値でスコープする
 * (他社の監査ログが混在してはならない)。
 *
 * 追加専用({@link com.kinn.app.repository.HealthAuditLogRepository}参照。更新・物理削除
 * メソッドを提供しない)。保持期間は application.properties の
 * app.audit.health.retention-days で管理し、削除バッチは将来追加できる構造にとどめている
 * (今回は未実装)。
 */
@Entity
@Table(name = "health_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ログを分離する会社ID。なりすまし防止のため、リクエストパラメータではなく
     * 必ず操作を行った本人(Authentication)のAppUser#companyIdから設定する。
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 操作を行ったユーザーの実効ID(= authentication.getName()、"companyId|loginId"形式) */
    @Column(name = "actor_employee_id", nullable = false, length = 64)
    private String actorEmployeeId;

    /** 操作を行ったユーザーの氏名(記録時点のスナップショット。後で氏名が変わっても事実を保持する) */
    @Column(name = "actor_name", length = 100)
    private String actorName;

    /**
     * 操作対象のユーザーの実効ID。自分自身のデータへの操作であれば actorEmployeeId と同じ値になる。
     * 管理者ダッシュボードのような個人に紐付かない集計閲覧は null(=個人非紐付け)。
     */
    @Column(name = "target_employee_id", length = 64)
    private String targetEmployeeId;

    /** 操作対象のユーザーの氏名(記録時点のスナップショット。targetEmployeeIdがnullの場合はnull) */
    @Column(name = "target_name", length = 100)
    private String targetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private HealthAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource", nullable = false, length = 32)
    private HealthAuditResource resource;

    /**
     * 対象データの識別情報(日付・レコードID・期間など)。健康情報の値そのもの
     * (体重・血圧・体調メモ等)は絶対に入れない。
     */
    @Column(name = "target_ref", length = 255)
    private String targetRef;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 16)
    private HealthAuditResult result;

    /** 失敗時の簡潔な理由(例外クラス名など)。スタックトレースや健康情報は含めない */
    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    private void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }
}
