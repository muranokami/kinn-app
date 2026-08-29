package com.kinn.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinn.app.audit.HealthAuditSecurityEventLogger;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionManagementFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ログイン機能の中核設定。
 *
 * 認証方式: Spring Security + セッション認証(HttpSession) + BCryptによるパスワードハッシュ化。
 * 独自の簡易認証は作らず、Spring Securityの標準コンポーネント
 * (AuthenticationManager / SecurityContextRepository / PasswordEncoder)をそのまま利用する。
 *
 * ログイン・新規登録はJSON APIで行う(AuthController)ため、formLogin/httpBasicは無効化し、
 * 代わりに「HTMLページへの未認証アクセスはログイン画面へリダイレクト」
 * 「APIへの未認証アクセスは401 JSON」を Accept ヘッダで振り分けるカスタムentry pointを使う。
 *
 * セキュリティ強化(2026-08-28)で以下を追加している:
 * ・セッション管理: 同時ログインセッション数の制限・セッション固定攻撃対策
 *   (ログインがformLoginフィルタを使わない独自フローのため、SessionAuthenticationStrategyを
 *   AuthController側からも明示的に呼び出している。下記Bean定義のjavadoc参照)
 * ・ログイン試行制限: LoginAttemptListener(失敗回数のカウント・一時ロック)
 * ・HTTPセキュリティヘッダー(CSP/HSTS/X-Frame-Options等)
 * ・Cookie属性(Secure/SameSite)をserver.servlet.session.cookie.*から取得し、
 *   JSESSIONID・XSRF-TOKEN両方に同じ設定を適用(dev/prodはプロファイルで切り替え)
 * ・健康管理以外を含む /api/admin/** への403/401も専用セキュリティログへ記録
 *
 * パスワードリセットは管理者操作のみに一本化している(2026-08-29)。メール経由のセルフサービス
 * リセット(本人がメールのリンクから自分で再設定する方式)は、外部のメール経路を伴い個人情報
 * 漏洩の経路になり得るという判断から実装ごと削除した。対象社員のパスワードリセットは、必ず
 * 管理者による強制リセット(/api/admin/employees/{id}/reset-password)を経由すること。
 *
 * 同じ理由で、健康アラート・タスク期限アラートの通知もメール/Slackでの外部送信は行わず、
 * 本人が画面(index.html/top.js)を開いた際にその場で表示するアプリ内アラートのみにしている
 * (index.htmlのhealthAlertBanner/taskAlertBanner参照)。会社単位の通知チャネル設定は存在しない。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** ロール不足・未認証アクセスを集約するための専用ロガー(logback-spring.xmlでファイル出力先を分離) */
    private static final Logger securityLog = LoggerFactory.getLogger("com.kinn.app.security.SECURITY_AUDIT");

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            // 既存の静的ページが inline style="..." を多用しているため style-src だけは
            // 'unsafe-inline' を許可する(script-src・default-srcは'self'のみで厳格に保つ)。
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self'",
            "font-src 'self'",
            // トップページの天気表示(js/top.js)がブラウザから直接Open-Meteo APIを呼ぶため許可する
            // (APIキー不要の外部気象API。バックエンドは仲介しない設計。天気APIの呼び出し先が
            // 増えた場合はここに追記すること)。
            "connect-src 'self' https://api.open-meteo.com",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    private final ObjectMapper objectMapper;
    private final HealthAuditSecurityEventLogger healthAuditSecurityEventLogger;

    public SecurityConfig(ObjectMapper objectMapper, HealthAuditSecurityEventLogger healthAuditSecurityEventLogger) {
        this.objectMapper = objectMapper;
        this.healthAuditSecurityEventLogger = healthAuditSecurityEventLogger;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** AuthControllerがログイン成功時に、認証済みAuthenticationをHttpSessionへ明示的に保存するために使う */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** どのユーザーが今どのセッションを持っているかを保持する(同時セッション数制限に必須) */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** セッション破棄(ログアウト・タイムアウト)をSessionRegistryへ反映するために必須のリスナー */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * セッション認証戦略。3つを順番に適用する:
     * 1. ConcurrentSessionControlAuthenticationStrategy: 同時ログインセッション数の制限
     *    (maximumSessions超過時、maxSessionsPreventsLogin=falseなら最古のセッションを失効させ、
     *    trueなら新しいログイン自体を拒否する)
     * 2. ChangeSessionIdAuthenticationStrategy: セッション固定攻撃対策
     *    (ログイン成功時にセッションIDを再発行する。Spring Securityの sessionFixation()
     *    migrateSession() と同じ実装で、これがデフォルト戦略でもある)
     * 3. RegisterSessionAuthenticationStrategy: 新しいセッションをSessionRegistryへ登録
     *
     * このアプリはformLoginフィルタを使わず、AuthControllerが
     * authenticationManager.authenticate() を直接呼び出す独自ログインフローのため、
     * 通常なら自動的に働くSessionManagementFilterのセッション認証戦略が発火しない
     * (フィルタチェーン内で認証状態が変化したリクエストだけを検知する仕組みのため)。
     * そのためこのBeanをAuthController側からも明示的に呼び出している。
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            SessionRegistry sessionRegistry,
            @Value("${app.security.session.max-concurrent-sessions:3}") int maxConcurrentSessions,
            @Value("${app.security.session.max-sessions-prevents-login:false}") boolean maxSessionsPreventsLogin) {
        ConcurrentSessionControlAuthenticationStrategy concurrentSessionStrategy =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrentSessionStrategy.setMaximumSessions(maxConcurrentSessions);
        concurrentSessionStrategy.setExceptionIfMaximumExceeded(maxSessionsPreventsLogin);

        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrentSessionStrategy,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    /**
     * ConcurrentSessionControlAuthenticationStrategyは「上限を超えた古いセッション」を
     * SessionRegistry上で失効マーク(expireNow())するだけで、実際にそのセッションを
     * 無効化してログイン不可にする役目は持たない。実際の失効処理はこのFilterが
     * 毎リクエストでSessionRegistryを見て行う(Spring Securityの.maximumSessions()DSLを
     * 使う場合は自動的に組み込まれるが、独自のSessionAuthenticationStrategyを
     * 指定したことで自動追加されなくなるため、明示的にBean化してフィルタチェーンへ足している)。
     */
    @Bean
    public ConcurrentSessionFilter concurrentSessionFilter(SessionRegistry sessionRegistry) {
        return new ConcurrentSessionFilter(sessionRegistry, event -> {
            jakarta.servlet.http.HttpServletRequest request = event.getRequest();
            HttpServletResponse response = event.getResponse();
            securityLog.warn("同時セッション数の上限超過により失効したセッションへのアクセス: path={}, ip={}",
                    request.getRequestURI(), request.getRemoteAddr());
            if (wantsHtml(request)) {
                response.sendRedirect("/login.html");
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(response, Map.of("message", "他の端末・ブラウザでログインされたため、このセッションは終了しました"));
        });
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     SecurityContextRepository securityContextRepository,
                                                     SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                                     ConcurrentSessionFilter concurrentSessionFilter,
                                                     ServerProperties serverProperties,
                                                     @Value("${app.security.hsts.max-age-seconds:31536000}") long hstsMaxAgeSeconds)
            throws Exception {
        // JSESSIONID・XSRF-TOKEN両方のCookie属性(Secure/SameSite)を、Spring Boot標準の
        // server.servlet.session.cookie.* から取得して揃える(dev/prodはプロファイルで切り替え。
        // application-prod.properties参照)。JSESSIONID自体はこのプロパティをTomcatが直接見るため
        // ここではXSRF-TOKEN側にだけ明示的に適用すればよい。
        var sessionCookie = serverProperties.getServlet().getSession().getCookie();
        boolean cookieSecure = Boolean.TRUE.equals(sessionCookie.getSecure());
        String cookieSameSite = sessionCookie.getSameSite() != null ? sessionCookie.getSameSite().name() : "Lax";

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite(cookieSameSite));
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler))
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionAuthenticationStrategy(sessionAuthenticationStrategy))
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(Customizer.withDefaults())
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(hstsMaxAgeSeconds))
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
            .authorizeHttpRequests(auth -> auth
                    // 未ログインでもアクセスできる画面・API(新規登録・ログイン)
                    .requestMatchers(
                            "/login.html", "/register.html",
                            "/css/**", "/js/**",
                            "/api/auth/register", "/api/auth/login", "/api/auth/company-lookup")
                    .permitAll()
                    // 管理者専用画面・API(既存の管理者機能を保護)。
                    .requestMatchers(
                            "/admin-top.html", "/admin-health.html", "/admin-attendance.html",
                            "/admin-dashboard.html", "/admin-employees.html", "/admin-departments.html",
                            "/admin-schedule.html", "/admin-task.html", "/admin-health-audit-log.html",
                            "/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(this::onUnauthenticated)
                    .accessDeniedHandler(this::onAccessDenied))
            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .logoutSuccessHandler((request, response, authentication) -> {
                        response.setStatus(HttpServletResponse.SC_OK);
                        writeJson(response, Map.of("message", "ログアウトしました"));
                    }))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            // Spring Security 6のCSRFトークンは遅延解決(参照されるまでCookieへ書き出されない)ため、
            // 素のJSクライアントが最初のGETだけでCookieを受け取れるよう、毎リクエストで強制的に
            // 解決させるフィルタを追加する(Spring公式のSPA向けCSRF連携パターン)。
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            // maximumSessions超過で失効マークされたセッションを実際に無効化するフィルタ
            // (SessionManagementFilterより前で毎リクエスト判定する。上のjavadoc参照)。
            .addFilterBefore(concurrentSessionFilter, SessionManagementFilter.class)
            // mustChangePassword=trueのユーザーをパスワード変更画面へ強制的に足止めするフィルタ。
            // AuthorizationFilter(ロールによる認可判定)より前に置き、SecurityContextが
            // セッションから復元された直後の状態で判定する(MustChangePasswordFilterのjavadoc参照)。
            .addFilterBefore(new MustChangePasswordFilter(objectMapper), AuthorizationFilter.class);

        return http.build();
    }

    /** 未認証アクセス: HTMLページ遷移ならログイン画面へリダイレクト、APIならJSONで401 */
    private void onUnauthenticated(jakarta.servlet.http.HttpServletRequest request,
                                    HttpServletResponse response,
                                    org.springframework.security.core.AuthenticationException ex) throws java.io.IOException {
        securityLog.warn("未認証アクセス: path={}, method={}, ip={}",
                request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        // 健康管理関連URLへの未認証アクセスは、本人を特定できないため health_audit_log には
        // 書き込めないが、アプリログにだけは残す(HealthAuditAspectが捕捉できないフィルタ層の
        // 拒否を補うため。処理自体には影響させない)。
        healthAuditSecurityEventLogger.recordUnauthenticated(request);

        if (wantsHtml(request)) {
            response.sendRedirect("/login.html");
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeJson(response, Map.of("message", "ログインが必要です"));
    }

    /** 認証済みだが権限不足(一般ユーザーが管理者画面へアクセスした場合など) */
    private void onAccessDenied(jakarta.servlet.http.HttpServletRequest request,
                                 HttpServletResponse response,
                                 org.springframework.security.access.AccessDeniedException ex) throws java.io.IOException {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        securityLog.warn("認可エラー(権限不足): path={}, method={}, principal={}, ip={}",
                request.getRequestURI(), request.getMethod(),
                authentication != null ? authentication.getName() : "unknown", request.getRemoteAddr());

        // HealthAuditAspectはControllerメソッドが実行された場合のみ動くAOPのため、
        // ロール不足でControllerに到達する前に拒否された今回のケースは捕捉できない。
        // 健康管理の管理者APIへのアクセス拒否であれば、ここから直接 health_audit_log に記録する
        // (対象外のURLであれば何もしない。記録に失敗しても本来のレスポンスには影響しない)。
        healthAuditSecurityEventLogger.recordAccessDenied(request, authentication);

        if (wantsHtml(request)) {
            response.sendRedirect("/index.html");
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        writeJson(response, Map.of("message", "このページを表示する権限がありません"));
    }

    private boolean wantsHtml(jakarta.servlet.http.HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private void writeJson(HttpServletResponse response, Map<String, String> body) throws java.io.IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new LinkedHashMap<>(body)));
    }
}
