package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 既読状況API(AnnouncementReadStatusDto#readUsers)の1件分。既読済み社員の氏名と既読日時 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementReadUserDto {
    private Long userId;
    private String fullName;
    private LocalDateTime readAt;
}
