package com.kinn.app.service;

import com.kinn.app.entity.AccountSecurityAction;
import com.kinn.app.entity.AccountSecurityLog;
import com.kinn.app.repository.AccountSecurityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 認証・アカウント操作系監査ログ(account_security_log)への記録を一箇所に集約する。
 * PasswordService・ForgotPasswordServiceの両方から使う。
 *
 * REQUIRES_NEW(呼び出し元とは別の独立したトランザクション)で書き込む。単にtry/catchで
 * 例外を握りつぶすだけでは不十分で、Hibernateは一度例外が起きたSessionをそのまま
 * 使い続けられない(「don't flush the Session after an exception occurs」)ため、
 * ここでの書き込みが万一失敗しても、呼び出し元(パスワード変更・リセット等の本来の処理)の
 * トランザクション・永続化コンテキストを道連れにしないよう、明示的に切り離している。
 */
@Service
public class AccountSecurityLogService {

    private static final Logger log = LoggerFactory.getLogger(AccountSecurityLogService.class);

    private final AccountSecurityLogRepository accountSecurityLogRepository;

    public AccountSecurityLogService(AccountSecurityLogRepository accountSecurityLogRepository) {
        this.accountSecurityLogRepository = accountSecurityLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String targetEmployeeId, String targetName,
                        String performedByEmployeeId, String performedByName,
                        AccountSecurityAction action) {
        try {
            accountSecurityLogRepository.save(AccountSecurityLog.builder()
                    .targetEmployeeId(targetEmployeeId)
                    .targetName(targetName)
                    .performedByEmployeeId(performedByEmployeeId)
                    .performedByName(performedByName)
                    .actionType(action)
                    .build());
        } catch (Exception e) {
            log.warn("account_security_logへの記録に失敗しました(呼び出し元の処理には影響しません)", e);
        }
    }
}
