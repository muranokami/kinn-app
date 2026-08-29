package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthAlertDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthAlertService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 健康アラート(一般的な注意喚起)API。医療診断は行わない。
 */
@RestController
@RequestMapping("/api/health/alerts")
public class HealthAlertController {

    private final HealthAlertService healthAlertService;

    public HealthAlertController(HealthAlertService healthAlertService) {
        this.healthAlertService = healthAlertService;
    }

    @Audited(resource = HealthAuditResource.ALERT, action = HealthAuditAction.VIEW, ref = "'days=' + #days")
    @GetMapping
    public List<HealthAlertDto> getAlerts(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        return healthAlertService.evaluateAndGetAlerts(authentication.getName(), days);
    }
}
