package com.kinn.app.controller;

import com.kinn.app.dto.DepartmentDayTaskDto;
import com.kinn.app.dto.MyTaskBoardDto;
import com.kinn.app.dto.TaskAlertsDto;
import com.kinn.app.dto.TaskAssigneeOptionDto;
import com.kinn.app.dto.TaskDto;
import com.kinn.app.dto.TaskStatusUpdateRequestDto;
import com.kinn.app.entity.TaskStatus;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.TaskService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 一般ユーザー向けタスクAPI(①②③④⑤⑥⑦⑧⑨⑩⑪⑬⑭⑮⑯⑰⑱⑳㉓)。ログインしていれば誰でも
 * (ROLE_USER/ROLE_ADMINどちらも)呼べる。会社・部署は常にログインユーザー(principal)から解決し、
 * リクエストボディのdepartmentIdは一切使わない(㊳)。担当者(assignedUserId)は登録時のみ
 * 同じ部署内であれば選択できる(②)が、依頼者(createdByUserId)は常に本人固定。
 * 管理者専用のAdminTaskController(/api/admin/tasks/**)とは別。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** マイタスク(未対応/対応中/完了の3列)(③㉞) */
    @GetMapping("/my")
    public MyTaskBoardDto getMyBoard(@AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getMyBoard(principal.getAppUser());
    }

    /**
     * 締め切りアラート(本日締め切り・期限切れの、ログイン中の本人が担当するタスクのみ)。
     * トップページの軽量表示・タスク管理画面の目立つ表示の両方から使う。
     */
    @GetMapping("/alerts")
    public TaskAlertsDto getMyAlerts(@AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getMyAlerts(principal.getAppUser());
    }

    /**
     * 同じ部署のタスク一覧(①②③④⑤⑥⑦⑧⑨⑩⑪⑬⑭⑮)。①部署別タスク一覧の画面から使う。
     * @param date 指定時はその日に該当するタスクのみ(⑩⑪日別表示)。省略時は日付で絞り込まず、
     *             部署の全タスクを対象にする(①)。
     * @param assignedUserId 指定時はその担当者のみに絞り込む(⑦「すべて/自分/山田/佐藤/鈴木」)。
     * @param status 指定時はそのステータスのみに絞り込む(⑧)。
     */
    @GetMapping("/department/day")
    public DepartmentDayTaskDto getDepartmentDay(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long assignedUserId,
            @RequestParam(required = false) TaskStatus status,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        LocalDate target = date != null ? LocalDate.parse(date) : null;
        return taskService.getDepartmentDay(principal.getAppUser(), target, assignedUserId, status);
    }

    /** 自分の部署のメンバー一覧(②タスク登録時の担当者選択肢) */
    @GetMapping("/department/members")
    public List<TaskAssigneeOptionDto> getDepartmentMembers(@AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getDepartmentMembers(principal.getAppUser());
    }

    /** タスク詳細(⑰) */
    @GetMapping("/{id}")
    public TaskDto getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getDetailForUser(principal.getAppUser(), id);
    }

    /** 自分を依頼者として新規登録する(⑨⑮②)。担当者は省略時は本人、同じ部署内なら他ユーザーも指定可 */
    @PostMapping
    public TaskDto create(
            @RequestBody TaskDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.createForSelf(principal.getAppUser(), dto);
    }

    /** 自分のタスクを編集する(⑱。ステータス・仕事内容・備考等。所属情報は変更不可) */
    @PutMapping("/{id}")
    public TaskDto update(
            @PathVariable Long id,
            @RequestBody TaskDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.updateOwn(principal.getAppUser(), id, dto);
    }

    /** ステータスのみを変更する(⑤「対応開始」「完了」ボタン・プルダウン共通) */
    @PatchMapping("/{id}/status")
    public TaskDto updateStatus(
            @PathVariable Long id,
            @RequestBody TaskStatusUpdateRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.updateStatusOwn(principal.getAppUser(), id, dto.getStatus());
    }

    /** 自分で作成したタスクを削除する(⑳) */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        taskService.deleteOwn(principal.getAppUser(), id);
    }
}
