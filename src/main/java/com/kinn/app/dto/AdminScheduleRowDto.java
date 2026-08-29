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
 * 管理者向け部署別スケジュール一覧の1件。ScheduleEventDtoに「誰の予定か」(氏名・部署)を
 * 加えたもの。健康・食事などの個人情報は含まない(業務スケジュールのみ)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminScheduleRowDto {
    private Long id;
    /** 予定の持ち主(AppUser.id)。管理者が編集・削除する際のURL指定に使う */
    private Long userId;
    private String fullName;
    private Long departmentId;
    private String departmentName;

    private LocalDate eventDate;
    private String dayOfWeekLabel;
    private LocalTime startTime;
    private LocalTime endTime;
    private String title;
    private ScheduleCategory category;
    private String categoryLabel;
    private String memo;
    /**
     * 場所(任意)。個人スケジュールにも場所を持たせたため(②)、管理者側の編集フォームでも
     * 表示・保存できるようにする(そうしないと、管理者が保存するたびに一般ユーザーが入力した
     * 場所が空で上書きされてしまう)。
     */
    private String location;
}
