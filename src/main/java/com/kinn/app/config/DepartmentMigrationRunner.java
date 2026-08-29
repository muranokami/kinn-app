package com.kinn.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 部署の正規化Migration。
 *
 * これまで app_user.department は自由記述の文字列だったが、会社ごとに部署を
 * Department Entity として正規化して管理するように変更した(Company → Department → AppUser)。
 * このランナーは起動のたびに以下を安全に(冪等に)行う。
 *
 *  1. department_id が未設定で、旧 department 列に値が残っている行を探す
 *  2. (company_id, department名) の組み合わせで Department が無ければ作成する
 *  3. その Department.id を app_user.department_id へ反映する
 *
 * 旧 department 列自体は削除しない(既存データを壊さない安全な移行方針。
 * spring.jpa.hibernate.ddl-auto=update は使われなくなった列を自動では削除しないため、
 * 列は残るが単に参照されなくなるだけで実害はない)。
 */
@Component
@Order(10) // MealSchemaInitializer 等より後で良いが、順序に強い依存はない
public class DepartmentMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DepartmentMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DepartmentMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> legacyRows = jdbcTemplate.queryForList("""
                    SELECT id, company_id, department
                    FROM app_user
                    WHERE department_id IS NULL
                      AND department IS NOT NULL
                      AND btrim(department) <> ''
                    """);

            if (legacyRows.isEmpty()) {
                log.info("department移行: 対象データなし(すでに移行済み、または旧データなし)");
                return;
            }

            int migrated = 0;
            for (Map<String, Object> row : legacyRows) {
                Long userId = ((Number) row.get("id")).longValue();
                Long companyId = ((Number) row.get("company_id")).longValue();
                String deptName = String.valueOf(row.get("department")).trim();

                Long departmentId = findOrCreateDepartment(companyId, deptName);
                jdbcTemplate.update("UPDATE app_user SET department_id = ? WHERE id = ?", departmentId, userId);
                migrated++;
            }
            log.info("department移行: {}件のユーザーを部署Entityへ移行しました", migrated);
        } catch (Exception e) {
            // 旧department列が存在しない環境(新規構築など)でも起動を止めない
            log.warn("department移行の確認中にエラーが発生しました(致命的ではありません): {}", e.getMessage());
        }
    }

    private Long findOrCreateDepartment(Long companyId, String name) {
        List<Long> existing = jdbcTemplate.queryForList(
                "SELECT id FROM department WHERE company_id = ? AND name = ?",
                Long.class, companyId, name);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbcTemplate.update(
                "INSERT INTO department (company_id, name, created_at) VALUES (?, ?, now())",
                companyId, name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM department WHERE company_id = ? AND name = ?",
                Long.class, companyId, name);
    }
}
