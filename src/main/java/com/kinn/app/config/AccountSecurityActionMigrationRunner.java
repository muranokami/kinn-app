package com.kinn.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hibernateは@Enumerated(EnumType.STRING)の列に対して、テーブル新規作成時点での
 * enum値だけを許可するCHECK制約(例: account_security_log_action_type_check)を
 * 自動生成する。spring.jpa.hibernate.ddl-auto=updateは既存の制約を更新しないため、
 * AccountSecurityActionへ新しい値(PASSWORD_RESET_REQUESTED等)を追加しても、
 * 古いCHECK制約が残ったままだとINSERTがDB制約違反で失敗してしまう
 * (CompanyCodeMigrationRunnerでcompany.nameのUNIQUE制約に対して行ったのと同じ問題)。
 *
 * このRunnerは起動のたびに、account_security_log.action_type に付いているCHECK制約を
 * カタログから動的に検索して削除する(冪等: 既に無ければ何もしない)。値の妥当性は
 * @Enumerated(EnumType.STRING)によりJava側で保証されるため、DB側のCHECK制約は無くても安全。
 *
 * あわせて、action_type列をvarchar(64)へ拡張する(PASSWORD_RESET_COMPLETED_VIA_EMAILのような
 * 長い値を格納できるようにするため)。spring.jpa.hibernate.ddl-auto=updateは既存列の型変更を
 * 確実には行わないため、ここで明示的にALTER COLUMNする(PostgreSQLのvarchar拡張は
 * テーブル書き換えを伴わない軽量なメタデータ操作であり、何度実行しても安全)。
 */
@Component
@Order(6)
public class AccountSecurityActionMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSecurityActionMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public AccountSecurityActionMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        dropLegacyCheckConstraint();
        widenActionTypeColumn();
    }

    private void dropLegacyCheckConstraint() {
        try {
            List<String> constraintNames = jdbcTemplate.queryForList("""
                    SELECT conname FROM pg_constraint
                    WHERE conrelid = 'account_security_log'::regclass
                      AND contype = 'c'
                      AND pg_get_constraintdef(oid) LIKE '%action_type%'
                    """, String.class);
            for (String constraintName : constraintNames) {
                jdbcTemplate.execute("ALTER TABLE account_security_log DROP CONSTRAINT " + quoteIdentifier(constraintName));
                log.info("account_security_log.action_typeの旧CHECK制約を削除しました: {}", constraintName);
            }
        } catch (Exception e) {
            // account_security_logテーブルがまだ存在しない環境(新規構築)でも起動を止めない
            log.warn("account_security_log.action_typeのCHECK制約確認中にエラーが発生しました(致命的ではありません): {}", e.getMessage());
        }
    }

    private void widenActionTypeColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE account_security_log ALTER COLUMN action_type TYPE varchar(64)");
        } catch (Exception e) {
            log.warn("account_security_log.action_typeの列幅拡張中にエラーが発生しました(致命的ではありません): {}", e.getMessage());
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
