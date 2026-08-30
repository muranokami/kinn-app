package com.kinn.app.controller;

import com.kinn.app.dto.AdminTaskListDto;
import com.kinn.app.dto.TaskDto;
import com.kinn.app.dto.TaskProgressRowDto;
import com.kinn.app.entity.TaskPriority;
import com.kinn.app.entity.TaskStatus;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.TaskService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理者向けタスク管理API(⑦⑩〜⑳㉖㉗㉘)。
 * ログイン中の管理者と同じ会社の部署・従業員のみを対象とする(他社のデータは含めない。㉑㉒㉔)。
 * /api/admin/** は SecurityConfig で hasRole("ADMIN") に限定済みのため、一般ユーザーは到達できない。
 * クラスレベルの@PreAuthorizeも多層防御として付与している(SecurityConfigのjavadoc参照)。
 */
@RestController
@RequestMapping("/api/admin/tasks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaskController {

    private final TaskService taskService;

    public AdminTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * タスク一覧+集計(⑩⑫⑬⑭⑮⑯⑰⑱⑲⑳㉖)。
     * @param departmentId 指定時はその部署のみ(⑫⑰)。省略時は全部署=会社全体。
     * @param assignedUserId 指定時はその担当者のみ(⑬⑳)。
     * @param requesterUserId 指定時はその依頼者のみ(⑲「依頼者別確認」⑳)。
     * @param status 指定時はそのステータスのみ(⑭⑳)。
     * @param priority 指定時はその優先度のみ(⑳)。
     * @param date 指定時はその日に該当するタスクのみ(⑰⑱「部署+日付」画面。開始日&lt;=date&lt;=期限)。
     */
    @GetMapping
    public AdminTaskListDto getTasks(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long assignedUserId,
            @RequestParam(required = false) Long requesterUserId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) String date,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        LocalDate targetDate = date != null ? LocalDate.parse(date) : null;
        return taskService.getForAdmin(principal.getAppUser().getCompanyId(), departmentId, assignedUserId,
                requesterUserId, status, priority, targetDate);
    }

    /** ユーザー別進捗(㉗) */
    @GetMapping("/progress/users")
    public List<TaskProgressRowDto> getUserProgress(
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getUserProgress(principal.getAppUser().getCompanyId(), departmentId);
    }

    /** 部署別進捗(㉘) */
    @GetMapping("/progress/departments")
    public List<TaskProgressRowDto> getDepartmentProgress(@AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.getDepartmentProgress(principal.getAppUser().getCompanyId());
    }

    /** タスクを新規登録し、部署・ユーザーを選んで割り当てる(⑦) */
    @PostMapping
    public TaskDto create(
            @RequestBody TaskDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.createByAdmin(
                principal.getAppUser().getCompanyId(), principal.getAppUser().getId(), dto);
    }

    /** タスクを編集する(⑲。タスク名・仕事内容・担当者・期限・優先度・ステータス・備考) */
    @PutMapping("/{id}")
    public TaskDto update(
            @PathVariable Long id,
            @RequestBody TaskDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return taskService.updateByAdmin(principal.getAppUser().getCompanyId(), id, dto);
    }

    /** タスクを削除する(⑳) */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        taskService.deleteByAdmin(principal.getAppUser().getCompanyId(), id);
    }
}
