package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthRecordDto;
import com.kinn.app.dto.MonthHealthDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /** 指定した年月の日次データ+集計を取得 */
    @Audited(resource = HealthAuditResource.MONTHLY_RECORD, action = HealthAuditAction.VIEW, ref = "#year + '-' + #month")
    @GetMapping("/{year}/{month}")
    public MonthHealthDto getMonth(
            @PathVariable int year,
            @PathVariable int month,
            Authentication authentication) {
        return healthService.getMonth(authentication.getName(), year, month);
    }

    /** 指定した年月の日次データをまとめて保存(登録・更新) */
    @Audited(resource = HealthAuditResource.MONTHLY_RECORD, action = HealthAuditAction.CREATE,
            ref = "#year + '-' + #month + ' (' + #days.size() + '件)'")
    @PostMapping("/{year}/{month}")
    public MonthHealthDto saveMonth(
            @PathVariable int year,
            @PathVariable int month,
            @RequestBody List<HealthRecordDto> days,
            Authentication authentication) {
        return healthService.saveMonth(authentication.getName(), year, month, days);
    }

    /** 1日分だけ保存 */
    @Audited(resource = HealthAuditResource.MONTHLY_RECORD, action = HealthAuditAction.UPDATE, ref = "#dto.recordDate")
    @PutMapping("/day")
    public HealthRecordDto saveDay(
            @RequestBody HealthRecordDto dto,
            Authentication authentication) {
        return healthService.saveDay(authentication.getName(), dto);
    }

    /** 1日分を削除(未入力状態に戻す) */
    @Audited(resource = HealthAuditResource.MONTHLY_RECORD, action = HealthAuditAction.DELETE, ref = "#date")
    @DeleteMapping("/day/{date}")
    public void deleteDay(
            @PathVariable String date,
            Authentication authentication) {
        healthService.deleteDay(authentication.getName(), LocalDate.parse(date));
    }
}
