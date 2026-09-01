package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * トップページの情報チップ表示用(申請者本人向け)。本日中に承認/却下されて動きがあった
 * 自分の残業申請の件数のみを返す軽量なレスポンス(AnnouncementUnreadCountDtoと同じ考え方)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequestRecentDecisionCountDto {
    private long recentDecisionCount;
}
