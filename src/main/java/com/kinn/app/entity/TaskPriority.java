package com.kinn.app.entity;

/** タスクの優先度(⑯)。3段階固定。 */
public enum TaskPriority {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低");

    private final String label;

    TaskPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
