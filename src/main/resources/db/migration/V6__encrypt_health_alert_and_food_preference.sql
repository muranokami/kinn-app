-- ==============================================================
-- V6: 健康アラート種別・食の好み情報の保存時暗号化に伴う列型変更
--
-- セキュリティレビュー(健康アラートの種別・食の好み情報も暗号化してほしいという指摘)への
-- 対応。health_alert.alert_type/severity/message、user_food_preference.favorite_foods/
-- disliked_foods/allergies/dietary_restrictionsは、V5と同じ方針(HealthDataEncryptor経由の
-- JPA AttributeConverter, AES-256-GCM)でアプリ層から暗号化してから保存するようにした。
-- 暗号文はBase64文字列になり元の値よりずっと長くなるため、対象列をtextへ変更する。
--
-- health_alert.alert_type/severityには値を許容リストに限定するCHECK制約が張られていたが、
-- 暗号化後は列の値が暗号文になり平文の許容リストとは一致しなくなるため削除する
-- (V5と同じ理由。値の妥当性チェックはアプリ層のJava Enumで引き続き保証される)。
--
-- health_alertには(employee_id, alert_type, triggered_date)のユニーク制約があったが、
-- alert_typeがランダムIVで暗号化される(=同じ値でも暗号文が毎回異なる)ため、DB側での
-- 一意性チェックが機能しなくなる。この制約は削除し、重複防止はHealthAlertService側の
-- アプリケーションレベルのチェックのみで行う(HealthAlertエンティティのjavadoc参照)。
--
-- 既存データの実際の暗号化はこのSQLでは行わない。型変更・制約削除のみ行い、実際の暗号化は
-- アプリ起動時のHealthDataEncryptionMigrationRunnerが安全に(冪等に)行う(V5と同じ方針)。
-- ==============================================================

ALTER TABLE public.health_alert
    DROP CONSTRAINT IF EXISTS ukhwg8fe4erdnbbul55ea8dk0gj,
    DROP CONSTRAINT IF EXISTS health_alert_alert_type_check,
    DROP CONSTRAINT IF EXISTS health_alert_severity_check;

ALTER TABLE public.health_alert
    ALTER COLUMN alert_type TYPE text USING alert_type::text,
    ALTER COLUMN severity TYPE text USING severity::text,
    ALTER COLUMN message TYPE text USING message::text;

ALTER TABLE public.user_food_preference
    ALTER COLUMN favorite_foods TYPE text USING favorite_foods::text,
    ALTER COLUMN disliked_foods TYPE text USING disliked_foods::text,
    ALTER COLUMN allergies TYPE text USING allergies::text,
    ALTER COLUMN dietary_restrictions TYPE text USING dietary_restrictions::text;
