package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 既読状況API(AnnouncementReadStatusDto#unreadUsers)の1件分。まだ既読していない社員の氏名 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementUnreadUserDto {
    private Long userId;
    private String fullName;
}
