package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.dto.HealthScoreDto;
import com.kinn.app.dto.HealthTrendDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthScoreService;
import com.kinn.app.service.HealthTrendService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 健康スコアと、その推移(健康状態の推移グラフ)を返すAPI。
 */
@RestController
@RequestMapping("/api/health/score")
public class HealthScoreController {

    private final HealthScoreService healthScoreService;
    private final HealthTrendService healthTrendService;

    public HealthScoreController(HealthScoreService healthScoreService, HealthTrendService healthTrendService) {
        this.healthScoreService = healthScoreService;
        this.healthTrendService = healthTrendService;
    }

    /** 指定日(省略時は今日)の健康スコアと内訳 */
    @Audited(resource = HealthAuditResource.SCORE, action = HealthAuditAction.VIEW,
            ref = "#date != null ? #date : T(java.time.LocalDate).now()")
    @GetMapping
    public HealthScoreDto getScore(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return healthScoreService.getScoreForDate(authentication.getName(), d);
    }

    /** 健康スコア・体重・睡眠時間などの推移(1w/1m/3m/6m) */
    @Audited(resource = HealthAuditResource.TREND, action = HealthAuditAction.VIEW, ref = "#period")
    @GetMapping("/trend")
    public HealthTrendDto getTrend(
            @RequestParam(defaultValue = "1m") String period,
            Authentication authentication) {
        return healthTrendService.getTrend(authentication.getName(), period);
    }
}
