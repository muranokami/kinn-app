package com.kinn.app.entity;

/**
 * 喫煙状況。
 */
public enum SmokingStatus {
    NONE("吸わない"),
    QUIT("禁煙中"),
    SMOKER("吸う");

    private final String label;

    SmokingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
