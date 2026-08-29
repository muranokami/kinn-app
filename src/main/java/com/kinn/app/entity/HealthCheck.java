package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「今日の体調チェック」1日分の記録。
 * 月次健康記録({@link HealthRecord})とは別テーブルで管理する
 * (疲労度など、健康スコア算出に必要な項目を持つため)。
 *
 * 以前は「ストレス度(stress_level)」も項目として持っていたが、労働安全衛生法上の
 * ストレスチェック制度(第66条の10)と紛らわしい外形(心理的な負担の程度を個別に
 * 測定・表示する機能)を作らないため、アプリからは完全に削除した
 * (docs/health-audit-legal-checklist.md 参照)。DB上の`stress_level`列自体は
 * 既存データ保護のため残しているが、このEntityからは参照しない。
 */
@Entity
@Table(
        name = "health_check",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "check_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_level", length = 32)
    private HealthConditionLevel condition;

    /** 睡眠時間(時間) */
    @Column(name = "sleep_hours")
    private Double sleepHours;

    /** 疲労度(1〜5、5が最も疲労が高い) */
    @Column(name = "fatigue_level")
    private Integer fatigueLevel;

    /** 運動時間(分) */
    @Column(name = "exercise_minutes")
    private Integer exerciseMinutes;

    /** 体温(℃) */
    @Column(name = "body_temperature")
    private Double bodyTemperature;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
