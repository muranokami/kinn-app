package com.kinn.app.entity;

/**
 * 健康アラートの種別。
 * あくまで一般的な注意喚起のためのものであり、医療診断は行わない。
 *
 * HIGH_STRESS: ストレス関連の測定・表示機能は撤廃した(労働安全衛生法上の
 * ストレスチェック制度と紛らわしい外形を作らないため。
 * docs/health-audit-legal-checklist.md参照)。HealthAlertServiceはこの種別の
 * アラートを新規に生成しない。列挙型からも削除したいところだが、
 * DBの列挙値チェック制約({@code health_alert_alert_type_check}）自体は
 * 変更していないため値として引き続き許容されており、万一過去に生成された行が
 * 残っていた場合に{@code @Enumerated(EnumType.STRING)}でのデシリアライズが
 * 失敗しないよう、あえてここに残している(V2__remove_stress_related_health_data.sqlで
 * 既存のHIGH_STRESSアラート行自体は削除済みのため、通常は新たに現れることはない)。
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
