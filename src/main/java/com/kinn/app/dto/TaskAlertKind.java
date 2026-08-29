package com.kinn.app.dto;

/**
 * タスク締め切りアラートの種別。DB保存はせず、TaskAlertsDto組み立て時に
 * dueDateと本日の日付を比較して算出するだけの表示用区分(TaskService#getMyAlerts参照)。
 */
public enum TaskAlertKind {
    /** 本日が締め切り */
    DUE_TODAY("本日締め切り"),
    /** 締め切りを過ぎている */
    OVERDUE("期限切れ");

    private final String label;

    TaskAlertKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
