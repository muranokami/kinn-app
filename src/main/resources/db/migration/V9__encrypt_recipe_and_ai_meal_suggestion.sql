-- ==============================================================
-- V9: レシピ・AI献立提案の保存時暗号化に伴う列型変更
--
-- セキュリティレビュー④対応(企業への提出前チェック)。meal_record(V8)と同じ理由
-- (食生活・健康状態を推測しうる情報)で、レシピ・AI献立提案の料理名・材料・調理手順・
-- 栄養素・メモ・提案理由等もアプリ層で暗号化してから保存するようにした
-- (HealthDataEncryptor経由のJPA AttributeConverter, AES-256-GCM)。暗号文はBase64文字列に
-- なり元の値よりずっと長くなるため、対象列をtextへ変更する。
--
-- recipe.cooking_method・ai_meal_suggestion.source・ai_meal_suggestion_item.meal_typeには
-- 値を許容リストに限定するCHECK制約が張られているが、これらの列は区分値でありクエリでの
-- 絞り込みにも使うため暗号化対象外とした(Recipe/AiMealSuggestion/AiMealSuggestionItem
-- エンティティのjavadoc参照)。そのためCHECK制約の削除は不要。
--
-- recipe.nameを暗号化したことで、DB側の完全一致検索(重複レシピ防止。RecipeRepository参照)
-- ができなくなったため、アプリ側(RecipeService#findExistingByName)で復号後のJava文字列
-- 比較に変更済み(HealthAlertServiceの重複判定と同じ方針)。
--
-- 既存データ(平文)そのものの暗号化はこのSQLでは行わない。型変更のみ行い、実際の暗号化は
-- アプリ起動時のHealthDataEncryptionMigrationRunnerが安全に(冪等に)行う(V5・V6・V8と同じ方針)。
-- ==============================================================

ALTER TABLE public.recipe
    ALTER COLUMN name TYPE text USING name::text,
    ALTER COLUMN equipment TYPE text USING equipment::text,
    ALTER COLUMN memo TYPE text USING memo::text,
    ALTER COLUMN calories TYPE text USING calories::text,
    ALTER COLUMN protein_g TYPE text USING protein_g::text,
    ALTER COLUMN fat_g TYPE text USING fat_g::text,
    ALTER COLUMN carbs_g TYPE text USING carbs_g::text,
    ALTER COLUMN salt_g TYPE text USING salt_g::text,
    ALTER COLUMN reheat_method TYPE text USING reheat_method::text;

ALTER TABLE public.recipe_ingredient
    ALTER COLUMN name TYPE text USING name::text,
    ALTER COLUMN quantity TYPE text USING quantity::text,
    ALTER COLUMN unit TYPE text USING unit::text;

ALTER TABLE public.recipe_step
    ALTER COLUMN description TYPE text USING description::text;

ALTER TABLE public.ai_meal_suggestion
    ALTER COLUMN summary_note TYPE text USING summary_note::text;

ALTER TABLE public.ai_meal_suggestion_item
    ALTER COLUMN dish_name TYPE text USING dish_name::text,
    ALTER COLUMN ingredients TYPE text USING ingredients::text,
    ALTER COLUMN calories TYPE text USING calories::text,
    ALTER COLUMN protein_g TYPE text USING protein_g::text,
    ALTER COLUMN fat_g TYPE text USING fat_g::text,
    ALTER COLUMN carbs_g TYPE text USING carbs_g::text,
    ALTER COLUMN reason TYPE text USING reason::text;
