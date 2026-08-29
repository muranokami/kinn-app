package com.kinn.app.service;

import com.kinn.app.entity.AccountSecurityAction;
import com.kinn.app.entity.AppUser;
import com.kinn.app.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

/**
 * パスワードのリセット・変更を扱うサービス。
 *
 * 秘密情報の扱い方針: 生成した一時パスワード・入力された現在/新パスワードは、いかなる形でも
 * ログ出力・DB保存(平文)をしない。DBに保存するのはBCryptハッシュのみで、一時パスワードの
 * 平文は呼び出し元(AdminEmployeeService)へ戻り値として一度だけ返し、それ以降はどこにも残らない
 * (画面表示後は二度と参照できない設計)。
 *
 * 将来拡張について: {@link #resetPassword}は「誰が実行するか」をこのメソッド自身の関心事にせず、
 * actorEmployeeId/actorName を単なる記録用の引数として受け取るだけにしている。
 * これにより、今回は管理者操作(AdminEmployeeService)からのみ呼び出しているが、将来メール送信基盤
 * (SMTP)を追加してセルフサービスのリセット申請フロー(登録メールアドレス宛にリセットリンクを送り、
 * リンクのトークンを検証した後にこのメソッドを呼ぶ方式)を追加する場合も、
 * このメソッド自体は変更せずに(actorに本人自身の情報、あるいはnullを渡すだけで)再利用できる。
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    /**
     * 一時パスワードの文字集合。1/l/I、0/Oのような見間違えやすい文字を除いた英数字に、
     * 最低限の記号をいくつか加える(口頭・メモでの伝達を想定し、視認性を優先する)。
     */
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789#%@";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final AppUserRepository appUserRepository;
    private final AccountSecurityLogService accountSecurityLogService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordService(AppUserRepository appUserRepository,
                            AccountSecurityLogService accountSecurityLogService,
                            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.accountSecurityLogService = accountSecurityLogService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * パスワードリセットのコア処理。ランダムな一時パスワードを生成してBCryptハッシュ化し保存する。
     * mustChangePasswordをtrueにするため、対象ユーザーは次回ログイン後、パスワードを変更するまで
     * 他の画面・APIへ進めなくなる({@code MustChangePasswordFilter}参照)。
     *
     * あわせてログイン失敗回数・一時ロックもクリアする(パスワードを強制的に配布し直す=
     * 管理者による救済措置として自然な挙動のため。既存のアカウントロック機構自体には手を入れない)。
     *
     * @param actorEmployeeId 実行者の実効ID。管理者操作の場合のみ設定し、監査ログに記録する
     * @param actorName       実行者の氏名。同上
     * @return 生成した一時パスワード(平文)。呼び出し元は画面へ一度だけ表示し、保持しないこと
     */
    @Transactional
    public String resetPassword(AppUser targetUser, String actorEmployeeId, String actorName) {
        String temporaryPassword = generateTemporaryPassword();
        targetUser.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        targetUser.setMustChangePassword(true);
        targetUser.setFailedLoginAttempts(0);
        targetUser.setLockedUntil(null);
        appUserRepository.save(targetUser);

        recordLog(targetUser, actorEmployeeId, actorName, AccountSecurityAction.PASSWORD_RESET_BY_ADMIN);
        // 一時パスワードそのものは絶対にログへ出さない
        log.info("パスワードを強制リセットしました: target={}, actor={}",
                targetUser.effectiveEmployeeId(), actorEmployeeId);

        return temporaryPassword;
    }

    /**
     * 本人によるパスワード変更(ログイン中のセッションからの変更)。
     * mustChangePassword=trueからの強制変更・本人による任意の変更のどちらもこのメソッドに
     * 統一しており、変更ロジックを二重に持たない。監査ログの操作種別は
     * PASSWORD_CHANGED_BY_USER固定(3引数のオーバーロード参照)。
     *
     * 現在のパスワードの入力は求めない(仕様上の判断)。既にセッション認証済みの本人操作であること、
     * および呼び出し元(AuthController)でログイン中の本人のloginIdとの一致を確認していることの
     * 2点で本人性を担保し、加えて「忘れてしまった現在のパスワードが分からず変更もできない」という
     * 手詰まりを避けるため、あえて現在パスワードの再入力を必須にしていない。
     */
    @Transactional
    public void changePassword(AppUser user, String newPasswordRaw) {
        changePassword(user, newPasswordRaw, AccountSecurityAction.PASSWORD_CHANGED_BY_USER);
    }

    /**
     * パスワード変更のコア処理。操作種別(action)を呼び出し元が指定できるようにしたオーバーロード。
     * ForgotPasswordService(メールのトークン経由のセルフサービスリセット)は、同じ検証・保存ロジックを
     * 再利用しつつ、監査ログにはPASSWORD_RESET_COMPLETED_VIA_EMAILを記録するためにこちらを呼ぶ
     * (パスワード変更ロジックを二重に持たないため。PasswordServiceのjavadoc参照)。
     */
    @Transactional
    public void changePassword(AppUser user, String newPasswordRaw, AccountSecurityAction action) {
        if (passwordEncoder.matches(newPasswordRaw, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "現在のパスワードとは異なるパスワードを設定してください。");
        }

        user.setPasswordHash(passwordEncoder.encode(newPasswordRaw));
        user.setMustChangePassword(false);
        appUserRepository.save(user);

        // 本人操作のため実行者=対象者。performedByは管理者操作の場合のみ意味を持つのでnullのまま。
        recordLog(user, null, null, action);
        log.info("パスワードを変更しました: user={}, action={}", user.effectiveEmployeeId(), action);
    }

    /**
     * 監査ログへの記録に失敗しても、パスワード変更・リセット自体(本来の処理)には影響させない
     * (LoginAttemptListenerと同じ考え方)。実際の記録・例外の吸収はAccountSecurityLogServiceが
     * 独立したトランザクションで行う(このメソッド自身のトランザクションを道連れにしないため。
     * AccountSecurityLogServiceのjavadoc参照)。
     */
    private void recordLog(AppUser target, String actorEmployeeId, String actorName, AccountSecurityAction action) {
        accountSecurityLogService.record(
                target.effectiveEmployeeId(), target.getFullName(), actorEmployeeId, actorName, action);
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
