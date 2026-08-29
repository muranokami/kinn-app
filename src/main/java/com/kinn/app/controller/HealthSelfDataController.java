package com.kinn.app.controller;

import com.kinn.app.audit.Audited;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.service.HealthSelfDataDeletionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康管理(拡張機能)の自己データ削除API。
 *
 * 本ツールは労働安全衛生法上のストレスチェック制度の代替ではなく、従業員本人による
 * 健康状態の自己記録・セルフケア支援を目的とした機能であり、入力は完全に任意である
 * (docs/health-audit-legal-checklist.md 参照)。そのため、本人がいつでも自分の入力データを
 * 削除できる手段を用意している(health-profile.htmlの「健康記録を削除する」ボタン)。
 *
 * 本人専用: ログイン中のユーザー自身のデータのみ削除できる。管理者であっても、
 * このAPIでは他人のデータを削除できない(他人のデータ削除が必要な場合は別途検討すること)。
 */
@RestController
@RequestMapping("/api/health/self-data")
public class HealthSelfDataController {

    private final HealthSelfDataDeletionService deletionService;

    public HealthSelfDataController(HealthSelfDataDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    /**
     * 健康プロフィール・体調チェック履歴・健康アラートを本人分すべて削除する。
     * 削除対象は複数リソース(PROFILE/DAILY_CHECK/ALERT)にまたがるが、@Auditedは
     * 1操作につき1リソース種別のため、代表としてPROFILEを指定し、refで削除範囲を明記する。
     */
    @Audited(resource = HealthAuditResource.PROFILE, action = HealthAuditAction.DELETE,
            ref = "'profile + daily_check + alert (self-data deletion)'")
    @DeleteMapping
    public void deleteAll(Authentication authentication) {
        deletionService.deleteAllForEmployee(authentication.getName());
    }
}
