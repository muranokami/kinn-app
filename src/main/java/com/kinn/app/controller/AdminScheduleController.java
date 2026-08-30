package com.kinn.app.controller;

import com.kinn.app.dto.AdminScheduleListDto;
import com.kinn.app.dto.DepartmentScheduleEventDto;
import com.kinn.app.dto.DepartmentScheduleListDto;
import com.kinn.app.dto.ScheduleEventDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.AdminScheduleService;
import com.kinn.app.service.DepartmentScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理者向け部署別スケジュールAPI(①②③)。
 * ログイン中の管理者と同じ会社の部署・従業員のみを対象とする(他社のデータは含めない。⑨⑩)。
 * /api/admin/** は SecurityConfig で hasRole("ADMIN") に限定済みのため、一般ユーザーは到達できない。
 * クラスレベルの@PreAuthorizeも多層防御として付与している(SecurityConfigのjavadoc参照)。
 */
@RestController
@RequestMapping("/api/admin/schedule")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScheduleController {

    private final AdminScheduleService adminScheduleService;
    private final DepartmentScheduleService departmentScheduleService;

    public AdminScheduleController(AdminScheduleService adminScheduleService,
                                    DepartmentScheduleService departmentScheduleService) {
        this.adminScheduleService = adminScheduleService;
        this.departmentScheduleService = departmentScheduleService;
    }

    /** 指定した年月のスケジュール一覧(departmentIdを指定するとその部署のみ。指定しなければ全部署=会社全体) */
    @GetMapping("/{year}/{month}")
    public AdminScheduleListDto getMonth(
            @PathVariable int year,
            @PathVariable int month,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminScheduleService.getMonth(principal.getAppUser().getCompanyId(), year, month, departmentId);
    }

    /** 従業員1人にスケジュールを登録する */
    @PostMapping("/employees/{userId}")
    public ScheduleEventDto createForEmployee(
            @PathVariable Long userId,
            @RequestBody ScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminScheduleService.createForEmployee(principal.getAppUser().getCompanyId(), userId, dto);
    }

    /** 部署全員に同じ内容のスケジュールを一括登録する(⑬複数人のスケジュール) */
    @PostMapping("/departments/{departmentId}")
    public List<ScheduleEventDto> createForDepartment(
            @PathVariable Long departmentId,
            @RequestBody ScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminScheduleService.createForDepartment(principal.getAppUser().getCompanyId(), departmentId, dto);
    }

    /** 従業員のスケジュールを編集する */
    @PutMapping("/employees/{userId}/{eventId}")
    public ScheduleEventDto updateForEmployee(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @RequestBody ScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminScheduleService.updateForEmployee(principal.getAppUser().getCompanyId(), userId, eventId, dto);
    }

    /** 従業員のスケジュールを削除する */
    @DeleteMapping("/employees/{userId}/{eventId}")
    public void deleteForEmployee(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        adminScheduleService.deleteForEmployee(principal.getAppUser().getCompanyId(), userId, eventId);
    }

    // ------------------------------------------------------------------
    // 部署共有スケジュール(⑦⑯⑰⑳㉑)。上のcreateForDepartment等(部署全員へ個人予定を
    // 複製登録する既存機能)とは異なり、こちらは1件のレコードを部署全体で共有する
    // 「本当の部署共有スケジュール」を扱う。管理者のみ登録・編集・削除できる(⑧㉓)。
    // ------------------------------------------------------------------

    /** 指定部署の共有スケジュール一覧(月間) */
    @GetMapping("/departments/{departmentId}/shared/{year}/{month}")
    public DepartmentScheduleListDto getSharedMonth(
            @PathVariable Long departmentId,
            @PathVariable int year,
            @PathVariable int month,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.getMonthForAdmin(principal.getAppUser().getCompanyId(), departmentId, year, month);
    }

    /**
     * 自社の部署共有スケジュールを、部署を指定せず全部署まとめて取得する。
     * 一般ユーザーが登録した部署共有スケジュールを、管理者が部署ごとに見て回らなくても
     * 一度に確認できるようにするためのAPI。
     */
    @GetMapping("/shared/{year}/{month}")
    public DepartmentScheduleListDto getSharedMonthAllDepartments(
            @PathVariable int year,
            @PathVariable int month,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.getMonthForAdminAllDepartments(principal.getAppUser().getCompanyId(), year, month);
    }

    /** 部署共有スケジュールを新規登録する */
    @PostMapping("/departments/{departmentId}/shared")
    public DepartmentScheduleEventDto createShared(
            @PathVariable Long departmentId,
            @RequestBody DepartmentScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.create(
                principal.getAppUser().getCompanyId(), departmentId, principal.getAppUser().getId(), dto);
    }

    /** 部署共有スケジュールを編集する */
    @PutMapping("/departments/{departmentId}/shared/{eventId}")
    public DepartmentScheduleEventDto updateShared(
            @PathVariable Long departmentId,
            @PathVariable Long eventId,
            @RequestBody DepartmentScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.update(principal.getAppUser().getCompanyId(), departmentId, eventId, dto);
    }

    /** 部署共有スケジュールを削除する */
    @DeleteMapping("/departments/{departmentId}/shared/{eventId}")
    public void deleteShared(
            @PathVariable Long departmentId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        departmentScheduleService.delete(principal.getAppUser().getCompanyId(), departmentId, eventId);
    }
}
