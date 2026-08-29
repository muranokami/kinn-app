package com.kinn.app.entity;

/**
 * 健康アラートの種別。
 * あくまで一般的な注意喚起のためのものであり、医療診断は行わない。
 */
public enum HealthAlertType {
    LOW_SLEEP("睡眠不足の継続"),
    HIGH_FATIGUE("疲労度が高い状態の継続"),
    HIGH_STRESS("ストレス度が高い状態の継続"),
    HIGH_OVERTIME("残業時間の多さ");

    private final String label;

    HealthAlertType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
