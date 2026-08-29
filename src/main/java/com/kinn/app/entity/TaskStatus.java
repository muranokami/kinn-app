package com.kinn.app.entity;

/**
 * タスクの進捗状態(④)。一般ユーザーが自分のタスクを管理する3段階そのもの。
 * UNRESOLVED → IN_PROGRESS → COMPLETED の順に進むのが基本だが、
 * プルダウンでの直接変更(⑤)も許可するため前後関係の強制はしない。
 */
public enum TaskStatus {
    UNRESOLVED("未対応"),
    IN_PROGRESS("対応中"),
    COMPLETED("完了");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
