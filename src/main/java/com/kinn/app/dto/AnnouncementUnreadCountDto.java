package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * トップページの情報チップ表示用。未読お知らせの件数のみを返す軽量なレスポンス
 * (一覧全体を取得しないための専用DTO。TaskAlertsDtoのdueTodayCount/overdueCountと同じ考え方)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementUnreadCountDto {
    private long unreadCount;
}
