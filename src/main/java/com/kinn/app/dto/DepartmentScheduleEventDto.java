package com.kinn.app.dto;

import com.kinn.app.entity.ScheduleCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 部署共有スケジュール1件(⑦の登録項目 + ⑥⑱の一覧表示に必要な項目)。
 * 登録・更新リクエストのボディにも、レスポンスにもこのDTOをそのまま使う
 * (id/departmentId/departmentName/dayOfWeekLabelはレスポンス専用で、リクエスト時は無視される)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentScheduleEventDto {
    private Long id;
    private Long departmentId;
    private String departmentName;

    private LocalDate eventDate;
    private String dayOfWeekLabel;
    private LocalTime startTime;
    private LocalTime endTime;

    private String title;
    /** 内容(⑦の「内容」欄。既存ScheduleEventのmemo列を流用) */
    private String content;
    /** 場所(⑦の「場所」欄) */
    private String location;

    private ScheduleCategory category;
    private String categoryLabel;

    /**
     * 登録者(⑬)。レスポンス専用(リクエスト時は無視される。id/departmentIdなどと同じ扱い)。
     * 一般ユーザーも部署共有予定を登録できるようになったため(⑥)、「誰が登録したか」を
     * 画面側で編集・削除ボタンの表示可否判定(⑭⑯)と詳細表示の両方に使う。
     */
    private Long createdByUserId;
    private String createdByName;
}
