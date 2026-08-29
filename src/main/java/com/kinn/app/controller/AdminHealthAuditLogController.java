package com.kinn.app.controller;

import com.kinn.app.audit.AuditTarget;
import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthAuditSearchResultDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.HealthAuditLogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理者向け:健康管理監査ログの検索API。
 * 期間・対象ユーザー(loginId)・操作種別で絞り込み、自社の従業員分のみを返す
 * (/api/admin/** はSecurityConfigでhasRole("ADMIN")により保護済み)。
 */
@RestController
@RequestMapping("/api/admin/health/audit-log")
public class AdminHealthAuditLogController {

    private final HealthAuditLogService healthAuditLogService;

    public AdminHealthAuditLogController(HealthAuditLogService healthAuditLogService) {
        this.healthAuditLogService = healthAuditLogService;
    }

    /** 監査ログの閲覧自体も、閲覧した事実を記録する(複数人を横断した検索のためtarget=NONE) */
    @Audited(resource = HealthAuditResource.AUDIT_LOG, action = HealthAuditAction.VIEW, target = AuditTarget.NONE,
            ref = "'target=' + (#targetLoginId ?: 'ALL') + ' action=' + (#action ?: 'ALL')"
                    + " + ' resource=' + (#resource ?: 'ALL') + ' ' + (#from ?: '') + '~' + (#to ?: '')")
    @GetMapping
    public HealthAuditSearchResultDto search(
            @RequestParam(required = false) String targetLoginId,
            @RequestParam(required = false) HealthAuditAction action,
            @RequestParam(required = false) HealthAuditResource resource,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        LocalDate fromDate = (from == null || from.isBlank()) ? null : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank()) ? null : LocalDate.parse(to);
        return healthAuditLogService.searchForAdmin(
                principal.getAppUser().getCompanyId(), targetLoginId, action, resource, fromDate, toDate);
    }
}
