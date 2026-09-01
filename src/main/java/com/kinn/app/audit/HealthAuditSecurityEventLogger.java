package com.kinn.app.audit;

import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditLog;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.entity.HealthAuditResult;
import com.kinn.app.repository.HealthAuditLogRepository;
import com.kinn.app.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link HealthAuditAspect} が捕捉できない、Spring Securityのフィルタ層で完結する
 * 拒否(SecurityConfigのaccessDeniedHandler/authenticationEntryPoint)を補うためのロガー。
 *
 * {@link HealthAuditAspect} は @Audited を付けたControllerメソッドが実際に実行された場合のみ
 * 動作するAOPのため、「ロール不足でControllerに到達する前にフィルタ層で403にされた」
 * リクエストはそもそも記録できない(健康管理の対象APIでこれが起こり得るのは
 * /api/admin/health/** のみ。/api/health/** は認証さえされていれば誰でも呼べるため、
 * ロール不足による403は発生しない)。
 *
 * 未認証(401)の場合は本人を特定できないため health_audit_log(actor_employee_id が必須)には
 * 書き込まず、アプリログに警告として残すだけにとどめる。
 */
@Component
public class HealthAuditSecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger(HealthAuditSecurityEventLogger.class);
    private static final int MAX_REF_LENGTH = 255;
    private static final int MAX_UA_LENGTH = 512;
    private static final int MAX_IP_LENGTH = 64;

    private final HealthAuditLogRepository healthAuditLogRepository;

    public HealthAuditSecurityEventLogger(HealthAuditLogRepository healthAuditLogRepository) {
        this.healthAuditLogRepository = healthAuditLogRepository;
    }

    /**
     * ロール不足による403(=認証はされているが権限が足りない)。
     * この時点で認証済みであることが保証されているため(Spring Securityが未認証は
     * authenticationEntryPoint側に振り分ける)、actorはAuthenticationから特定できる。
     */
    public void recordAccessDenied(HttpServletRequest request, Authentication authentication) {
        try {
            HealthAuditResource resource = resolveAdminHealthResource(request.getRequestURI());
            if (resource == null) {
                return; // 健康管理の管理者APIへのアクセスではない(このロガーの対象外)
            }
            if (!(authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
                log.warn("認証情報からactorを特定できないため、健康管理監査ログ(アクセス拒否)を記録しませんでした: path={}",
                        request.getRequestURI());
                return;
            }

            HealthAuditLog entry = HealthAuditLog.builder()
                    .companyId(principal.getAppUser().getCompanyId())
                    .actorEmployeeId(authentication.getName())
                    .actorName(principal.getFullName())
                    .targetEmployeeId(null)
                    .targetName(null)
                    .action(HealthAuditAction.VIEW)
                    .resource(resource)
                    .targetRef(truncate(request.getRequestURI(), MAX_REF_LENGTH))
                    .ipAddress(truncate(clientIp(request), MAX_IP_LENGTH))
                    .userAgent(truncate(request.getHeader("User-Agent"), MAX_UA_LENGTH))
                    .result(HealthAuditResult.DENIED)
                    .errorMessage("認可エラー: 管理者権限が必要です")
                    .occurredAt(LocalDateTime.now())
                    .build();
            healthAuditLogRepository.save(entry);
        } catch (Exception e) {
            // 監査ログの記録に失敗しても、本来のアクセス拒否レスポンス自体には影響させない
            log.warn("健康管理監査ログ(アクセス拒否)の記録に失敗しました", e);
        }
    }

    /**
     * 未認証(401)。本人を特定できないため health_audit_log には書かず、
     * 健康管理関連URLへの未認証アクセスがあったことをアプリログにのみ残す。
     */
    public void recordUnauthenticated(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!isHealthRelatedPath(path)) {
            return;
        }
        log.warn("未認証状態で健康管理関連URLへのアクセスがありました: path={}, ip={}", path, clientIp(request));
    }

    /** /api/admin/health/** のうち、実際にADMIN権限が必要なパスだけを対象リソースへ解決する */
    private HealthAuditResource resolveAdminHealthResource(String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("/api/admin/health/dashboard")) {
            return HealthAuditResource.ADMIN_DASHBOARD;
        }
        if (path.equals("/api/admin/health/audit-log")) {
            return HealthAuditResource.AUDIT_LOG;
        }
        return null;
    }

    private boolean isHealthRelatedPath(String path) {
        return path != null && (path.startsWith("/api/health/") || path.startsWith("/api/admin/health/"));
    }

    /**
     * X-Forwarded-Forは誰でも自由に送信できるヘッダーであり、直接信用すると
     * セキュリティイベントログのIPアドレスが攻撃者に偽装されてしまう
     * (セキュリティレビューで指摘・修正。AuthController#clientIpと同じ理由)。
     * getRemoteAddr()の使用理由・リバースプロキシ配下での正しい対応はHealthAuditAspect#clientIp
     * のjavadoc参照。
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
