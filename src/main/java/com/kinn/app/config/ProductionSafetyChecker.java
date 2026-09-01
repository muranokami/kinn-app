package com.kinn.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 本番プロファイル(--spring.profiles.active=prod)で起動する際、開発体験のために
 * 用意している「未設定でもすぐ動く」フォールバック値がそのまま使われていないかを
 * 起動時に検証する(セキュリティレビュー④対応)。
 *
 * 対象は以下の2つ。どちらも「未設定時は開発専用の既定値にフォールバックし、警告ログを
 * 出すだけ」という設計(DB_PASSWORD・ANTHROPIC_API_KEYと同じ「環境変数から注入し、
 * コードには直書きしない」方針。application.properties / HealthDataEncryptorのjavadoc参照)
 * のため、ログを見落としたまま本番運用してしまうリスクがある。
 * ・{@code spring.datasource.password}(既定値: postgres)
 * ・{@code app.security.health-encryption.key}(未設定=このリポジトリを見れば
 *   誰でも分かる固定鍵にフォールバックし、健康管理データが実質誰でも復号できてしまう)
 *
 * prodプロファイルでこれらが既定値のままの場合は、警告ログだけでなく起動そのものを
 * 失敗させる(他のCommandLineRunnerがDBへ書き込みを始める前に検知できるよう、
 * 最も早いOrderで実行する)。dev/testプロファイルでは何もしない(ローカル開発で
 * 環境変数を用意していなくてもすぐ動く、という既存の利便性は変えない)。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyChecker implements CommandLineRunner {

    private static final String PROD_PROFILE = "prod";
    private static final String INSECURE_DEFAULT_DB_PASSWORD = "postgres";

    private final Environment environment;
    private final String datasourcePassword;
    private final String healthEncryptionKey;

    public ProductionSafetyChecker(Environment environment,
                                    @Value("${spring.datasource.password:}") String datasourcePassword,
                                    @Value("${app.security.health-encryption.key:}") String healthEncryptionKey) {
        this.environment = environment;
        this.datasourcePassword = datasourcePassword;
        this.healthEncryptionKey = healthEncryptionKey;
    }

    @Override
    public void run(String... args) {
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }

        StringBuilder problems = new StringBuilder();
        if (INSECURE_DEFAULT_DB_PASSWORD.equals(datasourcePassword)) {
            problems.append("- DB_PASSWORD が開発用の既定値(postgres)のままです。"
                    + "環境変数 DB_PASSWORD に本番用のパスワードを設定してください。\n");
        }
        if (healthEncryptionKey == null || healthEncryptionKey.isBlank()) {
            problems.append("- APP_HEALTH_ENCRYPTION_KEY が未設定です。"
                    + "openssl rand -base64 32 等で生成した本番用の鍵を環境変数に設定してください。\n");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "本番プロファイル(prod)を開発用のデフォルト設定のまま起動しようとしました。"
                            + "このまま起動するとDB不正接続・健康管理データの復号など重大な情報漏洩リスクが"
                            + "あるため、起動を中止します:\n" + problems);
        }
    }
}
