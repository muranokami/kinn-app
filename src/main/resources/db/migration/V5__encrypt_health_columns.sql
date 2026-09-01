-- ==============================================================
-- V5: 健康管理データの保存時暗号化に伴う列型変更
--
-- セキュリティレビュー(③要配慮個人情報の暗号化)への対応。身長・体重・血圧・睡眠時間・
-- 疲労度・体温・運動時間・歩数・喫煙/飲酒状況・体調・メモは、アプリ層
-- (HealthDataEncryptor経由のJPA AttributeConverter, AES-256-GCM)で暗号化してから
-- 保存するようにした。暗号文はBase64文字列になり元の数値/短い文字列よりずっと長くなるため、
-- 対象列をtextへ変更する。
--
-- 喫煙/飲酒状況・体調(condition/condition_level)には値を許容リストに限定するCHECK制約が
-- 張られていたが、暗号化後は列の値が暗号文(一見ランダムな文字列)になり、平文の許容リストとは
-- 一致しなくなるため、これらのCHECK制約は削除する(値の妥当性チェックは暗号化前、つまり
-- アプリ層のJava Enumで引き続き保証される)。
--
-- 既存データ(平文)そのものの暗号化はこのSQLでは行わない。型変更・制約削除のみ行い、
-- 実際の暗号化はアプリ起動時のHealthDataEncryptionMigrationRunnerが安全に(冪等に)行う
-- (SQLだけで暗号化しようとすると、鍵の管理・pgcrypto拡張の要否等でDBがアプリの暗号化方式と
-- 密結合してしまうため、Java側に寄せた)。
-- ==============================================================

ALTER TABLE public.health_check
    DROP CONSTRAINT IF EXISTS health_check_condition_level_check;

ALTER TABLE public.health_profile
    DROP CONSTRAINT IF EXISTS health_profile_drinking_status_check,
    DROP CONSTRAINT IF EXISTS health_profile_smoking_status_check;

ALTER TABLE public.health_record
    DROP CONSTRAINT IF EXISTS health_record_condition_check;

ALTER TABLE public.health_profile
    ALTER COLUMN height_cm TYPE text USING height_cm::text,
    ALTER COLUMN weight_kg TYPE text USING weight_kg::text,
    ALTER COLUMN systolic_bp TYPE text USING systolic_bp::text,
    ALTER COLUMN diastolic_bp TYPE text USING diastolic_bp::text,
    ALTER COLUMN body_temperature TYPE text USING body_temperature::text,
    ALTER COLUMN exercise_minutes TYPE text USING exercise_minutes::text,
    ALTER COLUMN avg_sleep_hours TYPE text USING avg_sleep_hours::text,
    ALTER COLUMN smoking_status TYPE text USING smoking_status::text,
    ALTER COLUMN drinking_status TYPE text USING drinking_status::text,
    ALTER COLUMN health_memo TYPE text USING health_memo::text;

ALTER TABLE public.health_record
    ALTER COLUMN weight_kg TYPE text USING weight_kg::text,
    ALTER COLUMN sleep_hours TYPE text USING sleep_hours::text,
    ALTER COLUMN steps TYPE text USING steps::text,
    ALTER COLUMN exercise_minutes TYPE text USING exercise_minutes::text,
    ALTER COLUMN systolic_bp TYPE text USING systolic_bp::text,
    ALTER COLUMN diastolic_bp TYPE text USING diastolic_bp::text,
    ALTER COLUMN condition TYPE text USING condition::text,
    ALTER COLUMN memo TYPE text USING memo::text;

ALTER TABLE public.health_check
    ALTER COLUMN condition_level TYPE text USING condition_level::text,
    ALTER COLUMN sleep_hours TYPE text USING sleep_hours::text,
    ALTER COLUMN fatigue_level TYPE text USING fatigue_level::text,
    ALTER COLUMN exercise_minutes TYPE text USING exercise_minutes::text,
    ALTER COLUMN body_temperature TYPE text USING body_temperature::text,
    ALTER COLUMN memo TYPE text USING memo::text;
