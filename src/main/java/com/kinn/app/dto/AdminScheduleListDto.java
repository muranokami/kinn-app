package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 管理者向け部署別スケジュール一覧(指定月、部署指定時はその部署のみ)。①②の一覧表示に使う。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminScheduleListDto {
    private int year;
    private int month;
    /** 絞り込んだ部署ID(全部署対象の場合はnull) */
    private Long departmentId;
    /** 絞り込んだ部署名(全部署対象の場合はnull) */
    private String departmentName;
    private List<AdminScheduleRowDto> events;
}
