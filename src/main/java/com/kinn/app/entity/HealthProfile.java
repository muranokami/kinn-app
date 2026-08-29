package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ユーザーごとの健康プロフィール(基本情報)。
 * 「今日の体調チェック」のような毎日の記録ではなく、身長・体重・血圧など
 * 本人が随時編集するベースライン情報を1人1行で保持する。
 * BMIは身長・体重から都度計算するため、ここでは保持しない(HealthProfileService参照)。
 */
@Entity
@Table(
        name = "health_profile",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    /**
     * 所属部署。管理者ダッシュボードでの部署別集計を将来的に有効化するための項目。
     * 未設定の場合はnull(集計上は「未設定」として扱う)。
     */
    @Column(name = "department", length = 64)
    private String department;

    /** 身長(cm) */
    @Column(name = "height_cm")
    private Double heightCm;

    /** 体重(kg) */
    @Column(name = "weight_kg")
    private Double weightKg;

    /** 収縮期血圧(上) */
    @Column(name = "systolic_bp")
    private Integer systolicBp;

    /** 拡張期血圧(下) */
    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    /** 体温(℃) */
    @Column(name = "body_temperature")
    private Double bodyTemperature;

    /** 平均的な運動時間(分/日) */
    @Column(name = "exercise_minutes")
    private Integer exerciseMinutes;

    /** 平均睡眠時間(時間) */
    @Column(name = "avg_sleep_hours")
    private Double avgSleepHours;

    /** 普段のストレス度(1〜5) */
    @Column(name = "stress_level")
    private Integer stressLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "smoking_status", length = 32)
    private SmokingStatus smokingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "drinking_status", length = 32)
    private DrinkingStatus drinkingStatus;

    @Column(name = "health_memo", length = 1000)
    private String healthMemo;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
