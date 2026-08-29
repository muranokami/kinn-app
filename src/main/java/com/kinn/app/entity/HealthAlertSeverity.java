package com.kinn.app.entity;

/**
 * 健康アラートの深刻度。あくまで注意喚起の強さの目安であり、
 * 医療的な重症度判定ではない。
 */
public enum HealthAlertSeverity {
    INFO("お知らせ"),
    WARNING("注意");

    private final String label;

    HealthAlertSeverity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
