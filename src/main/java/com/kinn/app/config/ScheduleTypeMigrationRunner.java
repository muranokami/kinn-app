package com.kinn.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 部署共有スケジュール機能の追加に伴うMigration。
 *
 * schedule_event に新しく追加した schedule_type 列は、既存行を壊さないよう意図的に
 * NOT NULL制約を付けていない(ScheduleEventのJavadoc参照)。そのため列追加直後は
 * 既存の全行が schedule_type = NULL の状態になる。これらは実質すべて「個人スケジュール」
 * だったデータなので、起動のたびに安全に(冪等に) PERSONAL へ補完する。
 *
 * ・既存データを削除・上書きすることはない(NULLの行だけを対象にUPDATEする)。
 * ・DepartmentMigrationRunnerと同じ方針(CommandLineRunner + JdbcTemplateでIF NOT EXISTS
 *   相当の安全な補完)を踏襲する。
 */
@Component
@Order(11) // DepartmentMigrationRunner(10)の後でよい。部署Entityの移行が先に終わっている方が安全
public class ScheduleTypeMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTypeMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public ScheduleTypeMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE schedule_event SET schedule_type = 'PERSONAL' WHERE schedule_type IS NULL");
            if (updated > 0) {
                log.info("schedule_type移行: {}件の既存予定をPERSONALへ補完しました", updated);
            } else {
                log.info("schedule_type移行: 対象データなし(すでに移行済み、または既存データなし)");
            }
        } catch (Exception e) {
            // schedule_event テーブル自体が無い新規構築環境などでも起動を止めない
            log.warn("schedule_type移行の確認中にエラーが発生しました(致命的ではありません): {}", e.getMessage());
        }
    }
}
