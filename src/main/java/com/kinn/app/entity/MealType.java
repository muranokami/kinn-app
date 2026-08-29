package com.kinn.app.entity;

/**
 * 食事区分。将来的な間食対応を見据えて最初から SNACK を含めている
 * (間食は1日に複数件登録できる想定。朝・昼・夕は基本1日1件の上書き運用)。
 */
public enum MealType {
    BREAKFAST("朝食"),
    LUNCH("昼食"),
    DINNER("夕食"),
    SNACK("間食");

    private final String label;

    MealType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
