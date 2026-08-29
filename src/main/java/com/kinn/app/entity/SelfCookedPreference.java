package com.kinn.app.entity;

/**
 * 自炊/外食の好み。AI献立提案の調理時間・手軽さの考慮に利用する(将来拡張)。
 */
public enum SelfCookedPreference {
    SELF_COOK("自炊"),
    EATING_OUT("外食"),
    EITHER("どちらでも");

    private final String label;

    SelfCookedPreference(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
