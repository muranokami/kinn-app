package com.kinn.app.entity;

/**
 * 予定の分類。
 */
public enum ScheduleCategory {
    WORK("仕事"),
    MEETING("会議"),
    PRIVATE("プライベート"),
    HEALTH("通院・健康"),
    OTHER("その他");

    private final String label;

    ScheduleCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
