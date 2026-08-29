package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthAnalysisDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthAnalysisService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 勤怠×健康の連携分析API。
 */
@RestController
@RequestMapping("/api/health/analysis")
public class HealthAnalysisController {

    private final HealthAnalysisService healthAnalysisService;

    public HealthAnalysisController(HealthAnalysisService healthAnalysisService) {
        this.healthAnalysisService = healthAnalysisService;
    }

    @Audited(resource = HealthAuditResource.ANALYSIS, action = HealthAuditAction.VIEW, ref = "#period")
    @GetMapping
    public HealthAnalysisDto getAnalysis(
            @RequestParam(defaultValue = "1m") String period,
            Authentication authentication) {
        return healthAnalysisService.getAnalysis(authentication.getName(), period);
    }
}
