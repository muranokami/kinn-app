package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * お知らせの既読管理Entity。announcementId + appUserId の組み合わせで1人1件のみ既読記録を持つ
 * (ユニーク制約。二重に既読登録しても実害はないが、無駄な行が増え続けないようにする)。
 * AppUser.id / Announcement.id への参照はTask/ScheduleEventと同じ設計方針でFKカラムのみ
 * (@ManyToOneは使わない)。
 */
@Entity
@Table(name = "announcement_read",
        uniqueConstraints = @UniqueConstraint(columnNames = {"announcement_id", "app_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @PrePersist
    private void onCreate() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
