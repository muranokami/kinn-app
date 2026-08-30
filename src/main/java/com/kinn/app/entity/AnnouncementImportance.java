package com.kinn.app.entity;

/**
 * お知らせの重要度。TaskStatus/TaskPriorityと同じ考え方で、状態をEnumで表現する
 * (タスク管理機能のEnum設計パターンに合わせる)。
 */
public enum AnnouncementImportance {
    NORMAL("通常"),
    IMPORTANT("重要");

    private final String label;

    AnnouncementImportance(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
