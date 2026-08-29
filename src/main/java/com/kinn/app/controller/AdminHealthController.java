package com.kinn.app.controller;

import com.kinn.app.audit.AuditTarget;
import com.kinn.app.audit.Audited;
import com.kinn.app.dto.AdminHealthDashboardDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.AdminHealthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 管理者向け健康ダッシュボードAPI。会社・部署単位の集計のみを返し、
 * 個人が特定できる健康情報は返さない。ログイン中の管理者と同じ会社の社員のみを対象とする
 * (他社のデータは決して含めない)。
 */
@RestController
@RequestMapping("/api/admin/health")
public class AdminHealthController {

    private final AdminHealthService adminHealthService;

    public AdminHealthController(AdminHealthService adminHealthService) {
        this.adminHealthService = adminHealthService;
    }

    /** 個人非紐付けの集計閲覧のため target=NONE(誰が閲覧したかはactor側の記録で分かる) */
    @Audited(resource = HealthAuditResource.ADMIN_DASHBOARD, action = HealthAuditAction.VIEW,
            target = AuditTarget.NONE, ref = "'days=' + #days")
    @GetMapping("/dashboard")
    public AdminHealthDashboardDto getDashboard(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        return adminHealthService.getDashboard(days, companyPrefixOf(authentication));
    }

    /** "companyId|loginId" から "companyId|" の接頭辞を取り出す */
    static String companyPrefixOf(Authentication authentication) {
        String name = authentication.getName();
        int sep = name.indexOf('|');
        return sep < 0 ? name : name.substring(0, sep + 1);
    }
}
