package com.kinn.app.entity;

/**
 * 1日ごとの勤怠区分。
 */
public enum DayType {
    NORMAL("通常勤務"),
    SCHEDULED_HOLIDAY("所定休日"),      // 会社が定める休日(法定外)
    STATUTORY_HOLIDAY("法定休日"),      // 労基法で定める休日(週1日など)
    PAID_LEAVE("有給休暇"),
    SUBSTITUTE_HOLIDAY("振替休日"),      // 振休
    COMPENSATORY_HOLIDAY("代休"),
    ABSENCE("欠勤");

    private final String label;

    DayType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
