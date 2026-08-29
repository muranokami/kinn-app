package com.kinn.app.controller;

import com.kinn.app.dto.AuthUserDto;
import com.kinn.app.dto.ChangePasswordRequestDto;
import com.kinn.app.dto.CompanyLookupDto;
import com.kinn.app.dto.LoginRequestDto;
import com.kinn.app.dto.RegisterRequestDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.UserRole;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.security.RateLimiter;
import com.kinn.app.service.AuthService;
import com.kinn.app.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 新規登録・ログインAPI。ログアウトはSecurityConfigの.logout()に一本化しているため
 * ここには置かない(重複実装を避けるため)。
 *
 * このアプリはSpring Securityのformログインフィルタを使わず、ここで
 * authenticationManager.authenticate() を直接呼び出す独自ログインフローのため、
 * 本来SessionManagementFilterが自動的に行うセッション認証戦略の適用
 * (同時セッション数制限・セッション固定攻撃対策)も、ここで明示的に呼び出している
 * (SecurityConfig#sessionAuthenticationStrategy 参照)。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final RateLimiter rateLimiter;
    private final int loginRateLimitMax;
    private final long loginRateLimitWindowSeconds;
    private final int registerRateLimitMax;
    private final long registerRateLimitWindowSeconds;

    public AuthController(AuthService authService,
                           PasswordService passwordService,
                           AuthenticationManager authenticationManager,
                           SecurityContextRepository securityContextRepository,
                           SessionAuthenticationStrategy sessionAuthenticationStrategy,
                           RateLimiter rateLimiter,
                           @Value("${app.security.rate-limit.login.max-requests:10}") int loginRateLimitMax,
                           @Value("${app.security.rate-limit.login.window-seconds:60}") long loginRateLimitWindowSeconds,
                           @Value("${app.security.rate-limit.register.max-requests:5}") int registerRateLimitMax,
                           @Value("${app.security.rate-limit.register.window-seconds:60}") long registerRateLimitWindowSeconds) {
        this.authService = authService;
        this.passwordService = passwordService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.rateLimiter = rateLimiter;
        this.loginRateLimitMax = loginRateLimitMax;
        this.loginRateLimitWindowSeconds = loginRateLimitWindowSeconds;
        this.registerRateLimitMax = registerRateLimitMax;
        this.registerRateLimitWindowSeconds = registerRateLimitWindowSeconds;
    }

    @PostMapping("/register")
    public AuthUserDto register(@Valid @RequestBody RegisterRequestDto dto, HttpServletRequest request) {
        checkRateLimit("register", request, registerRateLimitMax, registerRateLimitWindowSeconds);
        return authService.register(dto);
    }

    /**
     * 新規登録画面(既存の会社に参加する場合)が、入力された会社コードから会社名・部署の
     * 選択肢を取得するための公開API。会社コードが一致しなければ404(register.jsが
     * 「会社コードが正しくありません」といった案内に変換する)。
     * 旧・会社名ベースの /departments エンドポイントは廃止した(会社名の一意性を
     * 前提にできなくなったため。AuthService#lookupCompanyByCode参照)。
     */
    @GetMapping("/company-lookup")
    public CompanyLookupDto lookupCompany(@RequestParam String companyCode) {
        return authService.lookupCompanyByCode(companyCode);
    }

    @PostMapping("/login")
    public AuthUserDto login(@Valid @RequestBody LoginRequestDto dto,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        checkRateLimit("login", request, loginRateLimitMax, loginRateLimitWindowSeconds);
        try {
            String username = authService.resolveUsername(dto.getCompanyName(), dto.getLoginId());
            Authentication authRequest = new UsernamePasswordAuthenticationToken(username, dto.getPassword());
            Authentication authResult = authenticationManager.authenticate(authRequest);

            // 同時セッション数制限・セッション固定攻撃対策(SecurityConfig参照)。
            // maxSessionsPreventsLogin=trueの場合、上限超過時はSessionAuthenticationExceptionを
            // 投げてこの後のSecurityContext保存には進ませない。
            sessionAuthenticationStrategy.onAuthentication(authResult, request, response);

            AppUserPrincipal principal = (AppUserPrincipal) authResult.getPrincipal();
            // ここまで到達した時点でログイン成功が確定しているため、今回のログイン日時を記録する
            // (principal.getAppUser()は以降toDto()が使うのと同じインスタンスなので、
            // ここで更新した値がそのままレスポンスの lastLoginAt に反映される)。
            authService.recordLogin(principal.getAppUser());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authResult);
            SecurityContextHolder.setContext(context);
            // formLoginフィルタを使わず自前で認証しているため、セッションへの保存も明示的に行う
            securityContextRepository.saveContext(context, request, response);

            return authService.toDto(principal);
        } catch (LockedException e) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "ログイン試行回数が上限に達したため、アカウントが一時的にロックされています。しばらくしてから再度お試しください。");
        } catch (DisabledException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "このアカウントは無効化されています。管理者にお問い合わせください。");
        } catch (BadCredentialsException e) {
            // 会社名が存在しない/ユーザーIDが存在しない/パスワードが違う、いずれも同じ文言にすることで
            // アカウントの存在を特定されにくくする
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "会社名、ユーザーID、またはパスワードが正しくありません。");
        } catch (SessionAuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "同時にログインできる上限に達しています。他の端末・ブラウザでログアウトしてから再度お試しください。");
        }
    }

    @GetMapping("/me")
    public AuthUserDto me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.toDto(principal);
    }

    /**
     * 本人によるパスワード変更。mustChangePassword=trueの状態からの強制変更・任意の変更のどちらも
     * このAPI一本に統一している(change-password.html参照)。このエンドポイント自体は
     * SecurityConfigのanyRequest().authenticated()で保護されるが、mustChangePassword=trueの
     * ユーザーが他の画面へ進むのを防ぐMustChangePasswordFilter側では、逆にこのエンドポイントだけは
     * 常に許可している(でなければ強制変更フローそのものが成立しなくなるため)。
     *
     * 一般ユーザー(USER)は任意にパスワードを変更できない(仕様上の判断。パスワード管理は
     * 管理者側に一本化する)。ただしmustChangePassword=true(管理者による強制リセット直後)は
     * 一般ユーザーでも変更を完了できないと永久に他の画面へ進めなくなってしまうため、
     * role不問で常に許可する。管理者(ADMIN)は常に任意のタイミングで変更できる。
     *
     * 現在のパスワードの入力は求めない(PasswordService#changePassword参照)。
     * loginIdはセッション(principal)から取得できるため認可上は不要だが、画面に入力させることで
     * 「今どのユーザーIDを変更しようとしているか」を操作者自身が目視確認できるようにしている。
     * ログイン中のユーザー自身のloginIdと一致しない場合はエラーにする(他人のIDを誤って
     * 入力したまま変更が進んでしまう事故を防ぐ)。
     */
    @PostMapping("/change-password")
    public AuthUserDto changePassword(@Valid @RequestBody ChangePasswordRequestDto dto,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        AppUser currentUser = principal.getAppUser();
        boolean forced = Boolean.TRUE.equals(currentUser.getMustChangePassword());
        if (currentUser.getRole() != UserRole.ADMIN && !forced) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "一般ユーザーはパスワードを自分で変更できません。パスワードの変更が必要な場合は管理者にご依頼ください。");
        }
        if (!dto.getLoginId().trim().equals(currentUser.getLoginId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "入力されたユーザーIDが、ログイン中のアカウントと一致しません。");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新しいパスワードが一致しません。");
        }
        passwordService.changePassword(currentUser, dto.getNewPassword());
        // principal.getAppUser()は同一セッション内で使い回されるインスタンスなので、
        // ここで更新した mustChangePassword=false がこのレスポンスにも以降のリクエストにも反映される
        return authService.toDto(principal);
    }

    /** IPアドレス単位のレート制限。超過時は429を返す(RateLimiter参照) */
    private void checkRateLimit(String bucket, HttpServletRequest request, int max, long windowSeconds) {
        String ip = clientIp(request);
        if (!rateLimiter.tryAcquire(bucket, ip, max, windowSeconds)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "リクエストが多すぎます。しばらくしてから再度お試しください。");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
