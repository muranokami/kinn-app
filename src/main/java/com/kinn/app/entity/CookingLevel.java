package com.kinn.app.entity;

/**
 * 料理の習熟度。AI献立提案で提案する調理の複雑さの考慮に利用する(将来拡張)。
 */
public enum CookingLevel {
    BEGINNER("初級"),
    INTERMEDIATE("中級"),
    ADVANCED("上級");

    private final String label;

    CookingLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
