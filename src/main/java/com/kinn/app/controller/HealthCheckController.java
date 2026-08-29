package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthCheckDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthCheckService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 「今日の体調チェック」API。ログイン中のユーザーのデータのみ参照・更新できる。
 */
@RestController
@RequestMapping("/api/health/check")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    public HealthCheckController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    /** 指定日(省略時は今日)のチェック内容を取得 */
    @Audited(resource = HealthAuditResource.DAILY_CHECK, action = HealthAuditAction.VIEW,
            ref = "#date != null ? #date : T(java.time.LocalDate).now()")
    @GetMapping
    public HealthCheckDto getByDate(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return healthCheckService.getByDate(authentication.getName(), d);
    }

    /** チェック内容を登録・更新(日付はDTO内のcheckDateを使用) */
    @Audited(resource = HealthAuditResource.DAILY_CHECK, action = HealthAuditAction.UPDATE, ref = "#dto.checkDate")
    @PutMapping
    public HealthCheckDto save(
            @RequestBody HealthCheckDto dto,
            Authentication authentication) {
        return healthCheckService.save(authentication.getName(), dto);
    }

    /** 過去の体調チェック履歴(期間指定) */
    @Audited(resource = HealthAuditResource.HISTORY, action = HealthAuditAction.VIEW, ref = "#from + '~' + #to")
    @GetMapping("/history")
    public List<HealthCheckDto> getHistory(
            @RequestParam String from,
            @RequestParam String to,
            Authentication authentication) {
        return healthCheckService.getHistory(authentication.getName(), LocalDate.parse(from), LocalDate.parse(to));
    }
}
