package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthAuditLogDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.HealthAuditLogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 一般ユーザー向け:「自分の健康データに誰がアクセスしたか」を確認するためのAPI。
 * 自分自身が対象(target)の監査ログのみを返す(他人のログは閲覧できない)。
 */
@RestController
@RequestMapping("/api/health/audit-log")
public class HealthAuditLogController {

    private final HealthAuditLogService healthAuditLogService;

    public HealthAuditLogController(HealthAuditLogService healthAuditLogService) {
        this.healthAuditLogService = healthAuditLogService;
    }

    /** 監査ログの閲覧自体も、閲覧した事実を記録する(resource=AUDIT_LOG) */
    @Audited(resource = HealthAuditResource.AUDIT_LOG, action = HealthAuditAction.VIEW)
    @GetMapping
    public List<HealthAuditLogDto> getMyAuditLog(@AuthenticationPrincipal AppUserPrincipal principal) {
        return healthAuditLogService.getMyAuditLogs(
                principal.getUsername(), principal.getAppUser().getCompanyId());
    }
}
