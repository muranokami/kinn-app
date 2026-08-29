package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthProfileDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 健康プロフィール(身長・体重・血圧などのベースライン情報)API。
 */
@RestController
@RequestMapping("/api/health/profile")
public class HealthProfileController {

    private final HealthProfileService healthProfileService;

    public HealthProfileController(HealthProfileService healthProfileService) {
        this.healthProfileService = healthProfileService;
    }

    @Audited(resource = HealthAuditResource.PROFILE, action = HealthAuditAction.VIEW)
    @GetMapping
    public HealthProfileDto getProfile(Authentication authentication) {
        return healthProfileService.getProfile(authentication.getName());
    }

    @Audited(resource = HealthAuditResource.PROFILE, action = HealthAuditAction.UPDATE)
    @PutMapping
    public HealthProfileDto saveProfile(
            @RequestBody HealthProfileDto dto,
            Authentication authentication) {
        return healthProfileService.saveProfile(authentication.getName(), dto);
    }
}
