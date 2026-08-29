package com.kinn.app.controller;

import com.kinn.app.dto.*;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.AdminAttendanceService;
import com.kinn.app.service.AttendancePeriodResolver;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理者向け勤怠API。
 * 従業員・日付・勤務区分・出退勤・実働時間・残業内訳(通常残業/所定休日残業/法定休日残業/総残業)を
 * 確認できる。ログイン中の管理者と同じ会社の社員のみを対象とする(他社のデータは含めない)。
 * 「休日勤務状況(管理者)」という休日時間の合計だけを表示する専用APIは復活させていない。
 */
@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {

    private final AdminAttendanceService adminAttendanceService;

    public AdminAttendanceController(AdminAttendanceService adminAttendanceService) {
        this.adminAttendanceService = adminAttendanceService;
    }

    /**
     * 管理者ダッシュボード(本日の出勤/欠勤/休日勤務/未打刻人数、今月の勤務時間・残業時間)。
     * departmentIdを指定すると、その部署だけの集計になる(⑤部署別集計を兼ねる)。
     */
    @GetMapping("/dashboard")
    public AdminAttendanceDashboardDto getDashboard(
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminAttendanceService.getDashboard(principal.getAppUser().getCompanyId(), departmentId);
    }

    /** 全社員(またはdepartmentId指定時はその部署のみ)フラット一覧(指定月) */
    @GetMapping("/{year}/{month}")
    public AdminAttendanceListDto getMonthlyList(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminAttendanceService.getMonthlyList(principal.getAppUser().getCompanyId(), year, month, departmentId);
    }

    /** 従業員別の月間勤怠(カレンダー表示・詳細確認用) */
    @GetMapping("/employees/{userId}/{year}/{month}")
    public MonthAttendanceDto getEmployeeMonth(
            @PathVariable Long userId,
            @PathVariable int year,
            @PathVariable int month,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminAttendanceService.getEmployeeMonth(principal.getAppUser().getCompanyId(), userId, year, month);
    }

    // ------------------------------------------------------------------
    // 締め日(カレンダー開始日)を指定した任意期間での一覧・従業員別カレンダー(⑧⑨)。
    // 期間の解決はAttendancePeriodResolverに一元化し、個人向けAPI(AttendanceController)と
    // 同じロジックを使う(締め日の考え方を管理者・一般ユーザーで重複実装しない)。
    // ------------------------------------------------------------------

    /** 全社員(またはdepartmentId指定時はその部署のみ)フラット一覧(指定期間) */
    @GetMapping("/period")
    public AdminAttendanceListDto getPeriodList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer closingDay,
            @RequestParam(required = false) String baseDate,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        LocalDate[] range = AttendancePeriodResolver.resolveRange(startDate, endDate, closingDay, baseDate);
        return adminAttendanceService.getPeriodList(principal.getAppUser().getCompanyId(), range[0], range[1], departmentId);
    }

    /** 従業員別の指定期間の勤怠(カレンダー表示・詳細確認用) */
    @GetMapping("/employees/{userId}/period")
    public AttendancePeriodDto getEmployeePeriod(
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer closingDay,
            @RequestParam(required = false) String baseDate,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        LocalDate[] range = AttendancePeriodResolver.resolveRange(startDate, endDate, closingDay, baseDate);
        return adminAttendanceService.getEmployeePeriod(principal.getAppUser().getCompanyId(), userId, range[0], range[1]);
    }

    /** 従業員の勤怠を修正(監査ログに記録される) */
    @PutMapping("/employees/{userId}/{date}")
    public AttendanceRecordDto editAttendance(
            @PathVariable Long userId,
            @PathVariable String date,
            @Valid @RequestBody AdminAttendanceEditRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminAttendanceService.editAttendance(
                principal.getAppUser().getCompanyId(), userId, LocalDate.parse(date), dto,
                principal.getUsername(), principal.getFullName());
    }

    /** 従業員の勤怠修正履歴(監査ログ) */
    @GetMapping("/employees/{userId}/audit")
    public List<AttendanceAuditDto> getAuditHistory(
            @PathVariable Long userId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminAttendanceService.getAuditHistory(principal.getAppUser().getCompanyId(), userId);
    }
}
