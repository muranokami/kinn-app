package com.kinn.app.config;

import com.kinn.app.security.crypto.HealthDataEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 健康管理データ(health_profile / health_record / health_check)の既存レコードを
 * アプリ層の暗号化(HealthDataEncryptor, AES-256-GCM)へ移行する(セキュリティレビュー③対応)。
 *
 * V5マイグレーションで対象列をtextへ変更しただけでは、既存データは依然として平文
 * (数値やEnum名の文字列表現)のまま残っている。このランナーは起動のたびに、各列の値が
 * まだ暗号化されていない(HealthDataEncryptor.isEncrypted()がfalseの)行を探し、
 * 暗号化した値で上書きする(冪等: 既に暗号化済みの行・NULLの行には触れない。
 * DepartmentMigrationRunter等、既存のMigrationRunnerと同じ方針)。
 *
 * JPA Entity経由ではなくJdbcTemplateで直接UPDATEする(Entity側のConverterは読み込み時に
 * 復号を試みるため、生SQLで完結させたほうが移行順序を気にせず安全)。
 */
@Component
@Order(20)
public class HealthDataEncryptionMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthDataEncryptionMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final HealthDataEncryptor encryptor;

    public HealthDataEncryptionMigrationRunner(JdbcTemplate jdbcTemplate, HealthDataEncryptor encryptor) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptor = encryptor;
    }

    @Override
    public void run(String... args) {
        migrateTable("health_profile",
                "height_cm", "weight_kg", "systolic_bp", "diastolic_bp", "body_temperature",
                "exercise_minutes", "avg_sleep_hours", "smoking_status", "drinking_status", "health_memo");
        migrateTable("health_record",
                "weight_kg", "sleep_hours", "steps", "exercise_minutes", "systolic_bp", "diastolic_bp",
                "condition", "memo");
        migrateTable("health_check",
                "condition_level", "sleep_hours", "fatigue_level", "exercise_minutes", "body_temperature", "memo");
        // V6対応分(健康アラート種別・食の好み情報)
        migrateTable("health_alert", "alert_type", "severity", "message");
        migrateTable("user_food_preference",
                "favorite_foods", "disliked_foods", "allergies", "dietary_restrictions");
    }

    private void migrateTable(String table, String... columns) {
        for (String column : columns) {
            try {
                migrateColumn(table, column);
            } catch (Exception e) {
                // 対象テーブル・列が存在しない環境(テスト用DB等)でも起動を止めない
                log.warn("{}.{} の健康管理データ暗号化移行中にエラーが発生しました(致命的ではありません): {}",
                        table, column, e.getMessage());
            }
        }
    }

    /** table/columnは呼び出し元で固定したホワイトリスト値のみを渡す(利用者入力は一切含まない) */
    private void migrateColumn(String table, String column) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, " + column + " AS value FROM " + table + " WHERE " + column + " IS NOT NULL");

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            String value = (String) row.get("value");
            if (value == null || encryptor.isEncrypted(value)) {
                continue; // 既に暗号化済み
            }
            Long id = ((Number) row.get("id")).longValue();
            jdbcTemplate.update(
                    "UPDATE " + table + " SET " + column + " = ? WHERE id = ?",
                    encryptor.encrypt(value), id);
            migrated++;
        }
        if (migrated > 0) {
            log.info("{}.{}: {}件を暗号化しました", table, column, migrated);
        }
    }
}
