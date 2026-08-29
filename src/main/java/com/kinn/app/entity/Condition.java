package com.kinn.app.entity;

/**
 * その日の体調。
 */
public enum Condition {
    GREAT("絶好調"),
    GOOD("良い"),
    NORMAL("普通"),
    BAD("不調");

    private final String label;

    Condition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
