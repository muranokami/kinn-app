package com.kinn.app.entity;

/**
 * 予定の種別。
 *
 * PERSONAL   … 特定のユーザー本人だけの予定(従来のScheduleEventはすべてこれ)。
 * DEPARTMENT … 部署全体で共有する仕事の予定。同じ部署に所属する全員(管理者・一般ユーザー問わず)が
 *              閲覧できる(「誰が見るか」ではなく「部署として何が予定されているか」を表す)。
 *
 * 既存のScheduleEventをそのまま拡張して両方を表現する(新規Entityは作らない。設計判断は
 * ScheduleEventのJavadoc参照)。
 */
public enum ScheduleType {
    PERSONAL("個人予定"),
    DEPARTMENT("部署共有予定");

    private final String label;

    ScheduleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
