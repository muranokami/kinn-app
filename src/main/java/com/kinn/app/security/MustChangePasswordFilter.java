package com.kinn.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AppUser#mustChangePassword=true のユーザーを、パスワード変更が完了するまで
 * change-password.html(と、それを成立させるための最小限のエンドポイント)以外へ
 * 進ませないためのフィルタ。
 *
 * SecurityConfigのauthorizeHttpRequests()はロール(ADMIN/USER)による静的な認可判定しか
 * 表現できないため、「ログイン中の特定ユーザーの状態(mustChangePassword)」に応じた
 * 動的な画面遷移制御はここで別建てに行う(認可判定ではなく業務ルールとしての遷移制御という
 * 位置づけ)。CsrfCookieFilter/ConcurrentSessionFilterと同じ理由により、あえて@Componentにせず
 * SecurityConfigからnewして明示的にFilterChainProxyへ追加している
 * (Spring Bootの自動Filter登録に乗せてSecurityContext確立前に実行されてしまう事故を防ぐため)。
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    /** mustChangePassword=trueでも到達できるパス(これらが無いと変更フロー自体が成立しない) */
    private static final Set<String> ALLOWED_EXACT_PATHS = Set.of(
            "/change-password.html",
            "/api/auth/change-password",
            "/api/auth/logout",
            "/api/auth/me"
    );
    private static final List<String> ALLOWED_PREFIXES = List.of("/css/", "/js/");

    private final ObjectMapper objectMapper;

    public MustChangePasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && Boolean.TRUE.equals(principal.getAppUser().getMustChangePassword())
                && !isAllowed(request)) {
            if (wantsHtml(request)) {
                response.sendRedirect("/change-password.html");
            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                Map<String, String> body = new LinkedHashMap<>();
                body.put("message", "パスワードの変更が必要です。パスワード変更画面で新しいパスワードを設定してください。");
                response.getWriter().write(objectMapper.writeValueAsString(body));
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (ALLOWED_EXACT_PATHS.contains(path)) {
            return true;
        }
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean wantsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }
}
