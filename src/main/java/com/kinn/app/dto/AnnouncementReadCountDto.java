package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理者の投稿一覧に既読/未読人数を軽く表示するための一覧用DTO(例:「既読 8/12人」)。
 * AnnouncementUnreadCountDtoと同じ考え方で、既読者・未読者の氏名までは含めない
 * (氏名の内訳が必要な場合はAnnouncementReadStatusDto/GET .../{id}/read-statusを使う)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementReadCountDto {
    private Long announcementId;
    private int totalCount;
    private int readCount;
    private int unreadCount;
}
