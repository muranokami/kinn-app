package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 部署共有スケジュールの月間一覧(②③⑥⑱)。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentScheduleListDto {
    private int year;
    private int month;
    private Long departmentId;
    /** 未所属ユーザーなどdepartmentIdが無い場合はnull("未所属"扱いはフロント側で表示) */
    private String departmentName;
    private List<DepartmentScheduleEventDto> events;
}
