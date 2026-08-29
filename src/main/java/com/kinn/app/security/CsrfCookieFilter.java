package com.kinn.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6のCsrfTokenは「実際に参照されるまでCookieへ書き出されない」遅延解決方式になっている。
 * 素のJavaScriptクライアント(このアプリのようにテンプレートエンジンを使わないSPA的構成)では
 * トークンを能動的に参照する場所が無いため、このフィルタで毎リクエスト強制的に解決させ、
 * ログイン前の最初のページ読み込みだけでXSRF-TOKEN Cookieを受け取れるようにする
 * (Spring公式ドキュメントが案内するSPA向けCSRF連携パターン)。
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // 参照することで CookieCsrfTokenRepository が Set-Cookie を書き出す
        }
        filterChain.doFilter(request, response);
    }
}
