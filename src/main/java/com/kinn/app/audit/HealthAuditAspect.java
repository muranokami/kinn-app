package com.kinn.app.audit;

import com.kinn.app.entity.HealthAuditLog;
import com.kinn.app.entity.HealthAuditResult;
import com.kinn.app.repository.HealthAuditLogRepository;
import com.kinn.app.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 健康管理APIの監査ログを横断的に記録するAspect。
 *
 * 設計方針(AttendanceAuditとの違い):
 * AttendanceAuditは「勤怠修正」という1操作専用で、Controller側が明示的に保存処理を呼び出す
 * 個別実装だった。健康管理は対象操作が閲覧を含め多数に及ぶため、Controllerメソッドへ
 * {@link Audited} を1行付けるだけで、このAspectが横断的に記録する方式に変えている。
 * 「修正前後(=操作の)事実をそのまま保存する」という設計思想自体は踏襲しており、
 * 健康情報の値そのものはログに残さない。
 *
 * 記録するactor(操作者)は必ず {@link Authentication} から取得し、リクエストパラメータは
 * 一切信用しない(なりすまし防止)。
 *
 * 監査ログの記録に失敗しても本来のAPI処理は失敗させない(このAspect内で例外を握りつぶし、
 * アプリログに警告を出すのみ)。まずは同期処理(@Aroundの中でそのままrepository.save)で
 * 実装しており、パフォーマンス上の問題が出た場合に @Async 化を検討できる構造にしている
 * (recordメソッドを切り出しているのはそのため)。
 */
@Aspect
@Component
public class HealthAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(HealthAuditAspect.class);
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_NAMES = new DefaultParameterNameDiscoverer();
    private static final int MAX_REF_LENGTH = 255;
    private static final int MAX_UA_LENGTH = 512;
    private static final int MAX_IP_LENGTH = 64;
    private static final int MAX_ERROR_LENGTH = 255;

    private final HealthAuditLogRepository healthAuditLogRepository;

    public HealthAuditAspect(HealthAuditLogRepository healthAuditLogRepository) {
        this.healthAuditLogRepository = healthAuditLogRepository;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        // 本来の処理より前に取得する(処理中にセッションが変わることはないが、念のため先に固定する)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            safeRecord(joinPoint, audited, authentication, failure);
        }
    }

    /** 監査ログの記録処理。ここで発生した例外は握りつぶし、本来のAPI処理には一切影響させない */
    private void safeRecord(ProceedingJoinPoint joinPoint, Audited audited, Authentication authentication, Throwable failure) {
        try {
            record(joinPoint, audited, authentication, failure);
        } catch (Exception loggingError) {
            log.warn("健康管理監査ログの記録に失敗しました(本来のAPI処理には影響しません): resource={}, action={}",
                    audited.resource(), audited.action(), loggingError);
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Audited audited, Authentication authentication, Throwable failure) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            // 認証情報が無い状態でこのAspectが呼ばれることは通常ないが(全APIが認証必須)、
            // 万一の場合はactorを特定できないため記録をスキップする(なりすまし防止のため
            // リクエストパラメータ等から推測して記録することはしない)。
            log.warn("認証情報からactorを特定できないため健康管理監査ログを記録しませんでした: resource={}, action={}",
                    audited.resource(), audited.action());
            return;
        }

        String actorEmployeeId = authentication.getName();
        Long companyId = principal.getAppUser().getCompanyId();
        String actorName = principal.getFullName();

        String targetEmployeeId = audited.target() == AuditTarget.NONE ? null : actorEmployeeId;
        String targetName = targetEmployeeId == null ? null : actorName;

        String ref = truncate(resolveRef(audited.ref(), joinPoint), MAX_REF_LENGTH);
        HttpServletRequest request = currentRequest();
        String ip = truncate(request == null ? null : clientIp(request), MAX_IP_LENGTH);
        String ua = truncate(request == null ? null : request.getHeader("User-Agent"), MAX_UA_LENGTH);

        HealthAuditResult result;
        String errorMessage = null;
        if (failure == null) {
            result = HealthAuditResult.SUCCESS;
        } else if (isAccessDenied(failure)) {
            result = HealthAuditResult.DENIED;
            errorMessage = truncate("認可エラー: " + failure.getClass().getSimpleName(), MAX_ERROR_LENGTH);
        } else {
            result = HealthAuditResult.FAILURE;
            errorMessage = truncate(failure.getClass().getSimpleName(), MAX_ERROR_LENGTH);
        }

        HealthAuditLog entry = HealthAuditLog.builder()
                .companyId(companyId)
                .actorEmployeeId(actorEmployeeId)
                .actorName(actorName)
                .targetEmployeeId(targetEmployeeId)
                .targetName(targetName)
                .action(audited.action())
                .resource(audited.resource())
                .targetRef(ref)
                .ipAddress(ip)
                .userAgent(ua)
                .result(result)
                .errorMessage(errorMessage)
                .occurredAt(LocalDateTime.now())
                .build();

        healthAuditLogRepository.save(entry);
    }

    private boolean isAccessDenied(Throwable failure) {
        if (failure instanceof AccessDeniedException) {
            return true;
        }
        if (failure instanceof ResponseStatusException rse) {
            return rse.getStatusCode().value() == 403 || rse.getStatusCode().value() == 401;
        }
        return false;
    }

    /**
     * {@link Audited#ref()} のSpEL式をメソッド引数(名前で参照可能)に対して評価する。
     * 式が空、あるいは評価に失敗した場合はnullを返す(target_refが取れないだけで、
     * 監査ログ自体の記録は継続する)。
     */
    private String resolveRef(String expression, ProceedingJoinPoint joinPoint) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object target = joinPoint.getTarget();
            Object[] args = joinPoint.getArgs();
            EvaluationContext ctx = new MethodBasedEvaluationContext(target, method, args, PARAM_NAMES);
            Object value = SPEL_PARSER.parseExpression(expression).getValue(ctx);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            log.warn("監査ログの対象データ識別情報(ref)の解決に失敗しました: expression={}", expression, e);
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    /**
     * 監査ログに記録するIPアドレス。X-Forwarded-Forは誰でも自由に送信できるヘッダーであり、
     * これを直接信用すると「誰が・どこからアクセスしたか」という監査ログ自体の証跡としての
     * 信頼性が攻撃者に偽装されてしまう(セキュリティレビューで指摘・修正。
     * AuthController#clientIpと同じ理由)。getRemoteAddr()はTCP接続の実際の送信元であり
     * アプリケーションコードでは偽装できない。リバースプロキシ配下で実クライアントIPを
     * 使いたい場合は、ここでヘッダーを自前でパースするのではなく、Tomcatの
     * RemoteIpValve(信頼するプロキシのIPを限定した上でgetRemoteAddr()自体を書き換える)を使うこと
     * (README「本番HTTPS配信チェックリスト」参照)。
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
