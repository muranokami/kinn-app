package com.kinn.app.entity;

/**
 * 「今日の体調チェック」で使う5段階の体調。
 * 月次健康記録({@link Condition})とは用途が異なる別物なので、
 * 既存の Condition には手を加えず新しい列挙型として追加している。
 */
public enum HealthConditionLevel {
    GREAT("絶好調"),
    GOOD("好調"),
    NORMAL("普通"),
    SLIGHTLY_BAD("少し不調"),
    BAD("不調");

    private final String label;

    HealthConditionLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
