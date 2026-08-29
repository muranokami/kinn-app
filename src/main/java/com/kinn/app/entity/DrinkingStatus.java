package com.kinn.app.entity;

/**
 * 飲酒状況。
 */
public enum DrinkingStatus {
    NONE("飲まない"),
    OCCASIONALLY("時々飲む"),
    REGULARLY("よく飲む");

    private final String label;

    DrinkingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
