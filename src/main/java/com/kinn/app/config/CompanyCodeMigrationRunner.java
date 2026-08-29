package com.kinn.app.config;

import com.kinn.app.service.CompanyCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 既存のCompanyレコード(company_codeがまだ発行されていないもの)へ、起動のたびに
 * 安全に(冪等に)company_codeを後埋めするMigration。DepartmentMigrationRunnerと同じ方針:
 * ・Companyエンティティ側はcompany_codeをnullable(必須制約なし)のまま保つことで、
 *   Hibernateのddl-auto=updateが起動時に列を追加するだけで済み、既存行がNOT NULL制約で
 *   即座にエラーになることを避ける。
 * ・実際の値の穴埋めはアプリケーション起動後にこのRunnerが行う。
 *
 * あわせて、旧バージョンでcompany.nameに付いていたDB上のUNIQUE制約を明示的に削除する。
 * spring.jpa.hibernate.ddl-auto=updateは列・制約の「追加」しか行わず、Entity側の
 * @Column(unique=true)を外しても既存DBの制約は自動では消えない(Hibernateの既知の制約)。
 * 削除せずにいると、同名の別会社を新規登録しようとした際にDB制約違反(500エラー)が発生し、
 * 今回の変更の目的(同姓同名の会社が複数存在できるようにする)を達成できない。
 */
@Component
@Order(5) // 会社コードは他の移行(部署等)より前に整えておく方が自然だが、強い依存はない
public class CompanyCodeMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanyCodeMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final CompanyCodeGenerator companyCodeGenerator;

    public CompanyCodeMigrationRunner(JdbcTemplate jdbcTemplate, CompanyCodeGenerator companyCodeGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.companyCodeGenerator = companyCodeGenerator;
    }

    @Override
    public void run(String... args) {
        dropLegacyNameUniqueConstraint();
        backfillCompanyCodes();
    }

    /**
     * company.name に付いているUNIQUE制約(旧バージョンの名残)をPostgreSQLのカタログから
     * 動的に検索して削除する。制約名はHibernateが生成するハッシュ名で環境ごとに異なり得るため、
     * ハードコードせず pg_constraint から「company テーブルの name 列に対するUNIQUE制約」を
     * 検索して特定する(冪等: 既に無ければ何もしない)。
     */
    private void dropLegacyNameUniqueConstraint() {
        try {
            List<String> constraintNames = jdbcTemplate.queryForList("""
                    SELECT conname FROM pg_constraint
                    WHERE conrelid = 'company'::regclass
                      AND contype = 'u'
                      AND pg_get_constraintdef(oid) = 'UNIQUE (name)'
                    """, String.class);
            for (String constraintName : constraintNames) {
                jdbcTemplate.execute("ALTER TABLE company DROP CONSTRAINT " + quoteIdentifier(constraintName));
                log.info("company.nameの旧UNIQUE制約を削除しました: {}", constraintName);
            }
        } catch (Exception e) {
            log.warn("company.nameのUNIQUE制約確認中にエラーが発生しました(致命的ではありません): {}", e.getMessage());
        }
    }

    /** PostgreSQLの識別子を安全にダブルクォートで囲む(pg_constraintから取得した既存の制約名のみに使う) */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private void backfillCompanyCodes() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id FROM company WHERE company_code IS NULL OR btrim(company_code) = ''
                    """);

            if (rows.isEmpty()) {
                log.info("company_code移行: 対象データなし(すでに移行済み、または新規データなし)");
                return;
            }

            int migrated = 0;
            for (Map<String, Object> row : rows) {
                Long companyId = ((Number) row.get("id")).longValue();
                // generateUnique()はDBを参照して一意性を確認するため、このループ内で
                // 都度発行しても同じコードが複数の会社に割り当たることはない。
                String code = companyCodeGenerator.generateUnique();
                jdbcTemplate.update("UPDATE company SET company_code = ? WHERE id = ?", code, companyId);
                migrated++;
            }
            log.info("company_code移行: {}件の会社にコードを発行しました", migrated);
        } catch (Exception e) {
            // 新規構築環境など、この移行が不要なケースでも起動を止めない
            log.warn("company_code移行の確認中にエラーが発生しました(致命的ではありません): {}", e.getMessage());
        }
    }
}
