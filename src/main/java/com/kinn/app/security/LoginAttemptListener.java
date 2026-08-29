package com.kinn.app.security;

import com.kinn.app.entity.AppUser;
import com.kinn.app.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ログイン試行制限(一定回数のログイン失敗でアカウントを一時ロックする)。
 *
 * authenticationManager.authenticate() は(AuthControllerが手動で呼び出す独自ログインフローでも)
 * 内部的に ProviderManager が AuthenticationSuccessEvent /
 * AuthenticationFailureBadCredentialsEvent を必ず発行するため、フィルタチェーンの構成に関係なく
 * ここでイベントを拾うだけでよい(AuthControllerのコードには手を入れない)。
 *
 * ロックの実際の強制(ログインさせない)は AppUserPrincipal#isAccountNonLocked() を
 * DaoAuthenticationProvider が自動的にチェックすることで行われる。ここでは
 * 「失敗回数を数え、閾値を超えたらロック時刻を設定する」ことだけを行う。
 *
 * DaoAuthenticationProvider は既定で hideUserNotFoundExceptions=true のため、
 * 存在しないユーザーIDへのログイン試行もBadCredentialsExceptionとして届く。該当ユーザーが
 * 存在しない場合は何もしない(ロックする対象が無いため。かつ、ここで何もしないこと自体が
 * 「アカウントが存在するかどうか」を外部から区別させないという既存の設計方針とも整合する)。
 *
 * イベントリスナー内で例外が起きるとログイン処理自体に伝播してしまうため、
 * 本来のログイン処理を絶対に壊さないよう全体をtry/catchで保護している。
 */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final AppUserRepository appUserRepository;
    private final int maxFailedAttempts;
    private final long lockDurationMinutes;

    public LoginAttemptListener(AppUserRepository appUserRepository,
                                 @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
                                 @Value("${app.security.login.lock-duration-minutes:15}") long lockDurationMinutes) {
        this.appUserRepository = appUserRepository;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        try {
            handleFailure(event);
        } catch (Exception e) {
            log.warn("ログイン失敗回数の記録に失敗しました(ログイン処理自体には影響しません)", e);
        }
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        try {
            handleSuccess(event);
        } catch (Exception e) {
            log.warn("ログイン失敗回数のリセットに失敗しました(ログイン処理自体には影響しません)", e);
        }
    }

    private void handleFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        parseAndFind(username).ifPresent(user -> {
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
                log.warn("ログイン失敗が{}回に達したためアカウントを一時ロックしました: user={}, lockDurationMinutes={}",
                        attempts, username, lockDurationMinutes);
            }
            appUserRepository.save(user);
        });
    }

    private void handleSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof AppUserPrincipal principal)) {
            return;
        }
        AppUser user = principal.getAppUser();
        boolean hasFailures = user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0;
        if (hasFailures || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            appUserRepository.save(user);
        }
    }

    /** username は "companyId|loginId" 形式(AppUserDetailsServiceと同じ解析ロジック) */
    private Optional<AppUser> parseAndFind(String username) {
        if (username == null) {
            return Optional.empty();
        }
        int sep = username.indexOf('|');
        if (sep < 0) {
            return Optional.empty();
        }
        try {
            long companyId = Long.parseLong(username.substring(0, sep));
            String loginId = username.substring(sep + 1);
            return appUserRepository.findByCompanyIdAndLoginId(companyId, loginId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
