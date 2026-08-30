package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * トップページの情報チップ表示用(管理者向け)。承認待ちの残業申請の件数のみを返す軽量な
 * レスポンス(AnnouncementUnreadCountDtoと同じ考え方)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequestPendingCountDto {
    private long pendingCount;
}
