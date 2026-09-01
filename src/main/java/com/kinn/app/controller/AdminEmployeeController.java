package com.kinn.app.controller;

import com.kinn.app.dto.AdminCreateEmployeeRequestDto;
import com.kinn.app.dto.EmployeeDepartmentUpdateRequestDto;
import com.kinn.app.dto.EmployeeDetailDto;
import com.kinn.app.dto.EmployeeEnabledUpdateRequestDto;
import com.kinn.app.dto.EmployeeRoleUpdateRequestDto;
import com.kinn.app.dto.EmployeeSummaryDto;
import com.kinn.app.dto.EmployeeUpdateRequestDto;
import com.kinn.app.dto.PasswordResetResultDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.AdminEmployeeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理者向け従業員管理API。ログイン中の管理者と同じ会社の従業員のみを対象とする
 * (他社の従業員IDを指定しても404になる。会社単位のアクセス制御はService層で実施)。
 * クラスレベルの@PreAuthorizeも多層防御として付与している(SecurityConfigのjavadoc参照)。
 */
@RestController
@RequestMapping("/api/admin/employees")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmployeeController {

    private final AdminEmployeeService adminEmployeeService;

    public AdminEmployeeController(AdminEmployeeService adminEmployeeService) {
        this.adminEmployeeService = adminEmployeeService;
    }

    /** 従業員一覧・検索(氏名/ユーザーIDの部分一致 + 部署IDの完全一致で絞り込み) */
    @GetMapping
    public List<EmployeeSummaryDto> getEmployees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.getEmployees(principal.getAppUser().getCompanyId(), keyword, departmentId);
    }

    /** 従業員詳細 */
    @GetMapping("/{id}")
    public EmployeeDetailDto getEmployeeDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.getEmployeeDetail(principal.getAppUser().getCompanyId(), id);
    }

    /** 従業員の新規登録(所属会社は常に管理者自身の会社) */
    @PostMapping
    public EmployeeDetailDto createEmployee(
            @Valid @RequestBody AdminCreateEmployeeRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.createEmployee(principal.getAppUser().getCompanyId(), dto);
    }

    /** 基本情報(氏名・役職)の編集 */
    @PutMapping("/{id}")
    public EmployeeDetailDto updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.updateProfile(principal.getAppUser().getCompanyId(), id, dto);
    }

    /**
     * 従業員の削除(自分自身、および自社で唯一の管理者は削除不可。Service層でチェックしている)。
     * /api/admin/** は SecurityConfig で hasRole("ADMIN") に限定済みのため、一般ユーザーは到達できない。
     */
    @DeleteMapping("/{id}")
    public void deleteEmployee(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        adminEmployeeService.deleteEmployee(
                principal.getAppUser().getCompanyId(), principal.getAppUser().getId(), id);
    }

    /** 権限変更(自分自身の権限は変更不可。Service層でも二重にチェックしている) */
    @PutMapping("/{id}/role")
    public EmployeeDetailDto updateRole(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRoleUpdateRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.updateRole(
                principal.getAppUser().getCompanyId(), principal.getAppUser().getId(), id, dto.getRole());
    }

    /**
     * アカウントの有効/無効切り替え(退職者アカウントを即座にログイン不可にする)。
     * 自分自身の無効化はService層で禁止している(権限変更・削除と同じ多重防御の考え方)。
     */
    @PutMapping("/{id}/enabled")
    public EmployeeDetailDto updateEnabled(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeEnabledUpdateRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.updateEnabled(
                principal.getAppUser().getCompanyId(), principal.getAppUser().getId(), id, dto.getEnabled());
    }

    /**
     * 対象社員のパスワードを管理者が強制的にリセットする。生成された一時パスワードは
     * このレスポンスに一度だけ含まれ、サーバー側には平文で保持しない
     * (画面表示後は二度と参照できない。AdminEmployeeService#resetPassword参照)。
     */
    @PostMapping("/{id}/reset-password")
    public PasswordResetResultDto resetPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.resetPassword(principal.getAppUser().getCompanyId(), principal.getAppUser(), id);
    }

    /** 所属部署の変更(指定した部署が自社のものであることをService層で確認する) */
    @PutMapping("/{id}/department")
    public EmployeeDetailDto updateDepartment(
            @PathVariable Long id,
            @RequestBody EmployeeDepartmentUpdateRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminEmployeeService.updateDepartment(
                principal.getAppUser().getCompanyId(), id, dto.getDepartmentId());
    }
}
