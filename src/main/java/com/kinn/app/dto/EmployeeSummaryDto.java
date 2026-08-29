package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 管理者向け従業員一覧の1行。健康・食事の個人情報は含まない(勤怠管理に必要な情報のみ)。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSummaryDto {
    private Long userId;
    private String loginId;
    private String fullName;
    private Long departmentId;
    private String departmentName;
    private String position;
    private String role;
    private LocalDateTime createdAt;
    /** 退職者アカウント等を無効化しているとfalse(ログイン不可) */
    private boolean enabled;

    /** 本日の勤務区分ラベル(例: "通常勤務", "所定休日") */
    private String todayDayTypeLabel;
    /** 本日の勤務状況(例: "出勤中", "退勤済み", "未出勤", "休日") */
    private String todayStatus;
}
