package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ログインユーザー。会社(companyId)+ユーザーID(loginId)の組み合わせが一意になる
 * (別会社であれば同じloginIdを使い回せる)。
 *
 * 実効的な「employeeId」(既存の勤怠・健康・食事管理などすべてのテーブルが使っている
 * 単一のvarchar識別子)は、このEntityそのものではなく {@code companyId + "|" + loginId}
 * という合成文字列として払い出す(SecurityConfig / AppUserDetailsService 参照)。
 * これにより既存テーブルへの列追加なしで会社単位・ユーザー単位の分離を両立している。
 */
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "login_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** ログイン画面で入力する「ユーザーID」 */
    @Column(name = "login_id", nullable = false, length = 64)
    private String loginId;

    /** BCryptでハッシュ化したパスワード。平文は一切保持しない */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * メールアドレス。セルフサービス型パスワードリセット(ForgotPasswordService)・
     * 将来の通知機能で使う。新規登録画面では必須入力にするが、既存データとの互換性のため
     * JPA上はnullable(未入力の既存ユーザーは管理者が後から追加できる。
     * AdminEmployeeController#updateProfile参照)。
     */
    @Column(name = "email", length = 255)
    private String email;

    /**
     * 所属部署(Department.id への参照。@ManyToOneは使わず既存Entityの設計方針に合わせて
     * FKカラムのみで持つ)。組織情報であり健康情報ではないため、あえてHealthProfileとは
     * 独立してここに持つ(管理者機能は勤怠に必要な情報のみを扱い、健康・食事の個人情報とは
     * 明確に分離するため)。
     *
     * 以前は自由記述の部署名文字列(department列)を持っていたが、会社ごとに部署を
     * 正規化して管理できるよう Department Entity への参照に変更した。旧department列は
     * DB上に残存するが(既存データ保護のため削除していない)、アプリからは参照しない。
     * 起動時に DepartmentMigrationRunner が旧department文字列からこの departmentId へ
     * 自動的に移行する。
     */
    @Column(name = "department_id")
    private Long departmentId;

    /** 役職(任意) */
    @Column(name = "position", length = 100)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * アカウントの有効/無効フラグ(退職者アカウントを即座にログイン不可にするためのもの)。
     * columnDefinitionでDB側にもdefault trueを持たせ、既存行がこの列追加によって
     * 意図せずnull(=無効扱い)になってログインできなくなる事故を防ぐ。
     * さらにAppUserPrincipal#isEnabled()側でもnullをtrue扱いにするフェイルセーフを入れている
     * (二重の安全策。既存ログイン機能を壊さないことを最優先するため)。
     */
    @Column(name = "enabled", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 連続ログイン失敗回数。一定回数(app.security.login.max-failed-attempts)に達すると
     * lockedUntilが設定され、一時的にログインできなくなる(LoginAttemptListener参照)。
     * ログイン成功時に0へリセットされる。
     */
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    /**
     * この日時までログインをロックする(nullまたは過去日時ならロックされていない)。
     * 一定時間経過後は自動的にロックが解除される(バッチ不要のセルフクリア方式)。
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** 今回ログインした日時(トップページ表示用)。AuthService#recordLoginがログイン成功のたびに更新する */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * trueの間は、ログイン後にパスワード変更画面(change-password.html)以外の画面・APIへの
     * アクセスを{@code MustChangePasswordFilter}が強制的にブロックする。
     * 管理者による強制パスワードリセット直後にtrueとなり、本人がパスワードを変更すると
     * {@code PasswordService#changePassword}がfalseへ戻す。
     * columnDefinitionでDB側にもdefault falseを持たせ、既存行がこの列追加によって
     * 意図せずnullになり通常ログインができなくなる事故を防ぐ(enabled列と同じ二重の安全策)。
     */
    @Column(name = "must_change_password", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean mustChangePassword = false;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** 既存テーブル(meal_record, attendance_record 等)が employee_id として使う実効ID */
    @Transient
    public String effectiveEmployeeId() {
        return companyId + "|" + loginId;
    }
}
