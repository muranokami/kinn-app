package com.kinn.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * meal_record テーブルに、アプリ層のロジック(MealService#save の find-or-create)を
 * 補強する形で「ユーザーID + 食事日 + 食事区分(間食を除く)」の一意制約を追加する。
 *
 * ・spring.jpa.hibernate.ddl-auto=update では @Table 上の注釈だけでは部分インデックス
 *   (WHERE meal_type <> 'SNACK') を表現できないため、起動時に IF NOT EXISTS で作成する。
 * ・既存データを一切変更しないため安全。既に重複データがあり作成に失敗した場合も
 *   アプリの起動は止めず、ログに警告を出すだけに留める(運用側で重複を解消できるように)。
 */
@Component
public class MealSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MealSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public MealSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_meal_record_daily_type
                    ON meal_record (employee_id, meal_date, meal_type)
                    WHERE meal_type <> 'SNACK'
                    """);
            log.info("meal_record: 一意制約(employee_id, meal_date, meal_type)を確認しました");
        } catch (Exception e) {
            log.warn("meal_record の一意インデックス作成に失敗しました(既存データに重複がある可能性があります): {}",
                    e.getMessage());
        }
    }
}
