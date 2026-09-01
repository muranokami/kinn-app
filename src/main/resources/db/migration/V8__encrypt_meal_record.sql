-- ==============================================================
-- V8: 食事記録の保存時暗号化に伴う列型変更
--
-- セキュリティレビュー④対応(企業への提出前チェック)。何を食べたか(dish_name/items/amount)・
-- 栄養素(calories/protein_g/fat_g/carbs_g/fiber_g/salt_g)・写真URL・メモは食生活や
-- 健康状態を推測しうる情報のため、他の健康管理データ(health_profile/health_record/
-- health_check)と同じ方式(HealthDataEncryptor経由のJPA AttributeConverter, AES-256-GCM)で
-- アプリ層から暗号化してから保存するようにした。暗号文はBase64文字列になり元の値より
-- ずっと長くなるため、対象列をtextへ変更する。
--
-- meal_type/meal_date/meal_time/recipe_idは、既存のクエリ条件
-- (findByEmployeeIdAndMealDateAndMealType等)でDB側の絞り込みに使われているため、
-- このマイグレーションの対象には含めない(MealRecordエンティティのjavadoc参照)。
-- meal_typeのCHECK制約(meal_record_meal_type_check)もそのまま維持する。
--
-- 既存データ(平文)そのものの暗号化はこのSQLでは行わない。型変更のみ行い、実際の暗号化は
-- アプリ起動時のHealthDataEncryptionMigrationRunnerが安全に(冪等に)行う(V5・V6と同じ方針)。
-- ==============================================================

ALTER TABLE public.meal_record
    ALTER COLUMN dish_name TYPE text USING dish_name::text,
    ALTER COLUMN items TYPE text USING items::text,
    ALTER COLUMN amount TYPE text USING amount::text,
    ALTER COLUMN calories TYPE text USING calories::text,
    ALTER COLUMN protein_g TYPE text USING protein_g::text,
    ALTER COLUMN fat_g TYPE text USING fat_g::text,
    ALTER COLUMN carbs_g TYPE text USING carbs_g::text,
    ALTER COLUMN fiber_g TYPE text USING fiber_g::text,
    ALTER COLUMN salt_g TYPE text USING salt_g::text,
    ALTER COLUMN photo_url TYPE text USING photo_url::text,
    ALTER COLUMN memo TYPE text USING memo::text;
