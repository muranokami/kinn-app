package com.kinn.app.dto;

import com.kinn.app.entity.ScheduleCategory;
import com.kinn.app.entity.ScheduleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 「個人」「部署」「すべて」を切り替えるカレンダー(④⑬)向けの統一フォーマット。
 * scheduleType で [個人]/[部署] を区別して表示する(⑫)。
 * GET /api/schedule/department/{year}/{month} と GET /api/schedule/all/{year}/{month} の
 * どちらもこのDTOのリストを返す。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleFeedEventDto {
    private Long id;
    private ScheduleType scheduleType;

    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;

    private String title;
    private String content;
    private String location;

    private ScheduleCategory category;
    private String categoryLabel;

    /** DEPARTMENT種別の場合のみ設定 */
    private String departmentName;

    /**
     * 登録者(⑬⑭⑯)。DEPARTMENT種別の場合のみ設定(PERSONALは常にnull。個人予定の所有者は
     * ログインユーザー自身と一致することが前提のAPI設計のため、別途保持する必要がない)。
     * 「すべて」タブでも、自分が登録した部署共有予定かどうかを画面側で判定できるようにする。
     */
    private Long createdByUserId;
    private String createdByName;
}
