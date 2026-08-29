package com.kinn.app.entity;

/**
 * 調理方法。AI献立生成・レシピ管理の両方で共通利用する(Phase 4)。
 * 最低限これらを扱えればよいという要件のため列挙型にしているが、
 * 新しい調理方法を増やす場合はここに1件追加するだけでよい(DB側はvarchar格納のため
 * マイグレーション不要。CHECK制約もHibernateのddl-auto=updateでは自動追随しないため、
 * 値を追加した場合は既存のCHECK制約を手動で更新するか、制約を外す必要がある点に注意)。
 */
public enum CookingMethod {
    GRILL("焼く"),
    STIR_FRY("炒める"),
    SIMMER("煮る"),
    STEAM("蒸す"),
    BOIL("茹でる"),
    DEEP_FRY("揚げる"),
    MICROWAVE("電子レンジ"),
    RICE_COOKER("炊く"),
    TOSS("和える"),
    RAW("生食"),
    OTHER("その他");

    private final String label;

    CookingMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
