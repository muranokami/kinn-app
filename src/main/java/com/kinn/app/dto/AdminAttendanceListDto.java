package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** 管理者向け勤怠一覧(指定月、全社員分)。個人の出退勤・残業内訳を確認できる。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAttendanceListDto {
    private int year;
    private int month;
    /** 締め日を指定した期間で取得した場合のみ設定される(暦月指定時はnull) */
    private LocalDate startDate;
    /** 締め日を指定した期間で取得した場合のみ設定される(暦月指定時はnull) */
    private LocalDate endDate;
    private List<AdminAttendanceRowDto> rows;
}
