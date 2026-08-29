package com.kinn.app.dto;

import com.kinn.app.entity.ScheduleCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEventDto {
    private Long id;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String title;
    private ScheduleCategory category;
    private String memo;
    /**
     * 場所(任意)。既存Entityの{@code location}列(部署共有スケジュールが先に使っていたもの)を
     * 個人スケジュールでも利用できるように公開する(②の登録項目「場所」)。新規カラムは追加しない。
     */
    private String location;
}
