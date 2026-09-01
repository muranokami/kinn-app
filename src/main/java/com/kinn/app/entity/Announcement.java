package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * お知らせ機能のEntity。
 *
 * Company → Department → AppUser という既存の階層にそのまま従い、Task/ScheduleEventと同じ
 * 設計方針で @ManyToOne は使わずFKカラム(companyId/departmentId/createdByUserId)のみで
 * 参照する。companyId は必ず投稿した管理者自身の会社から解決し、リクエストの数値IDを
 * 書き換えても他社への投稿・他社の投稿の閲覧はできない(㊳既存のマルチテナント設計方針)。
 *
 * departmentId は nullable で、null の場合は「全社向け」を表す(㊸)。null許容を敢えて選び、
 * 「全社向け」を表す専用のダミー部署IDを作らない(既存Departmentテーブルを汚さないため)。
 *
 * publishedAt(公開日時)は未来日時も設定できる(予約投稿)。一般ユーザー向けAPIは
 * publishedAt &lt;= 現在時刻 のものだけを返すため、予約投稿はその時刻が来るまで表示されない。
 * expiresAt(表示終了日時)はnullable。設定されていれば、その時刻を過ぎたお知らせは
 * 一般ユーザーには表示されなくなる(AnnouncementRepository#findVisibleForUser参照)。
 */
@Entity
@Table(name = "announcement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** 対象部署。nullなら全社向け(㊸) */
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 32)
    @Builder.Default
    private AnnouncementImportance importance = AnnouncementImportance.NORMAL;

    /** 投稿した管理者のAppUser.id */
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 公開日時。未来日時を設定すると予約投稿になる(その時刻までは一般ユーザーに表示されない) */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    /** 表示終了日時。nullなら無期限に表示され続ける */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
