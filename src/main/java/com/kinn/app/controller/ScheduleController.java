package com.kinn.app.controller;

import com.kinn.app.dto.DepartmentScheduleEventDto;
import com.kinn.app.dto.DepartmentScheduleListDto;
import com.kinn.app.dto.MonthScheduleDto;
import com.kinn.app.dto.ScheduleEventDto;
import com.kinn.app.dto.ScheduleFeedEventDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.DepartmentScheduleService;
import com.kinn.app.service.ScheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final DepartmentScheduleService departmentScheduleService;

    public ScheduleController(ScheduleService scheduleService, DepartmentScheduleService departmentScheduleService) {
        this.scheduleService = scheduleService;
        this.departmentScheduleService = departmentScheduleService;
    }

    /** 指定した年月の予定一覧+集計を取得(個人スケジュール。従来どおり) */
    @GetMapping("/{year}/{month}")
    public MonthScheduleDto getMonth(
            @PathVariable int year,
            @PathVariable int month,
            Authentication authentication) {
        return scheduleService.getMonth(authentication.getName(), year, month);
    }

    /**
     * 自分が所属する部署の共有スケジュールを取得する(⑥⑨⑩⑪⑱⑲⑳㉒)。
     * departmentIdをリクエストから一切受け取らず、ログイン中ユーザー(principal)から
     * 会社・部署を解決するため、URLを書き換えて他部署・他社のIDを指定する余地がない。
     * ROLE_USER/ROLE_ADMINどちらでも呼べる(㉓。自分の部署の閲覧は一般ユーザーにも必要)。
     */
    @GetMapping("/department/{year}/{month}")
    public DepartmentScheduleListDto getDepartmentMonth(
            @PathVariable int year,
            @PathVariable int month,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.getMonthForUser(principal.getAppUser(), year, month);
    }

    /**
     * 個人スケジュール＋自分の部署の共有スケジュールを合成したフィード(④⑬「すべて」表示)。
     * カレンダー・今日の予定パネルで [個人]/[部署] を区別して表示する(⑫)ために使う。
     */
    @GetMapping("/all/{year}/{month}")
    public List<ScheduleFeedEventDto> getAllMonth(
            @PathVariable int year,
            @PathVariable int month,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.getAllFeed(principal.getAppUser(), principal.getUsername(), year, month);
    }

    /** 予定を新規登録 */
    @PostMapping
    public ScheduleEventDto create(
            @RequestBody ScheduleEventDto dto,
            Authentication authentication) {
        return scheduleService.create(authentication.getName(), dto);
    }

    /** 予定を更新 */
    @PutMapping("/{id}")
    public ScheduleEventDto update(
            @PathVariable Long id,
            @RequestBody ScheduleEventDto dto,
            Authentication authentication) {
        return scheduleService.update(authentication.getName(), id, dto);
    }

    /** 予定を削除 */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            Authentication authentication) {
        scheduleService.delete(authentication.getName(), id);
    }

    // ------------------------------------------------------------------
    // 一般ユーザー自身による部署共有スケジュールの登録・編集・削除(⑥⑭⑮⑯)。
    // 管理者専用のAdminScheduleController(/api/admin/schedule/departments/{id}/shared/**)とは別に、
    // ログインしていれば誰でも(ROLE_USER/ROLE_ADMINどちらも)呼べる。会社・部署・登録者は
    // 常にログインユーザー(principal)から解決し、リクエストのdepartmentIdは一切使わない(⑤⑫⑱)。
    // ------------------------------------------------------------------

    /** 自分の所属部署の共有スケジュールとして登録する(①③⑥) */
    @PostMapping("/department")
    public DepartmentScheduleEventDto createDepartmentEvent(
            @RequestBody DepartmentScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.createForUser(principal.getAppUser(), dto);
    }

    /** 自分が登録した部署共有スケジュールを編集する(⑭)。管理者は他人の登録分も編集できる(⑯) */
    @PutMapping("/department/{eventId}")
    public DepartmentScheduleEventDto updateDepartmentEvent(
            @PathVariable Long eventId,
            @RequestBody DepartmentScheduleEventDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentScheduleService.updateOwn(principal.getAppUser(), eventId, dto);
    }

    /** 自分が登録した部署共有スケジュールを削除する(⑮)。管理者は他人の登録分も削除できる(⑯) */
    @DeleteMapping("/department/{eventId}")
    public void deleteDepartmentEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        departmentScheduleService.deleteOwn(principal.getAppUser(), eventId);
    }
}
