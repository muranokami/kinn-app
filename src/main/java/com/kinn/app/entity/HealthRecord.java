package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 1日分の健康記録。
 * 月次集計(平均体重・平均睡眠時間など)はここでは持たず、HealthService 側で
 * 都度計算する(集計ロジックを1箇所にまとめるため)。
 */
@Entity
@Table(
        name = "health_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "record_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 体重(kg) */
    @Column(name = "weight_kg")
    private Double weightKg;

    /** 睡眠時間(時間) */
    @Column(name = "sleep_hours")
    private Double sleepHours;

    /** 歩数 */
    @Column(name = "steps")
    private Integer steps;

    /** 運動時間(分) */
    @Column(name = "exercise_minutes")
    private Integer exerciseMinutes;

    /** 収縮期血圧(上) */
    @Column(name = "systolic_bp")
    private Integer systolicBp;

    /** 拡張期血圧(下) */
    @Column(name = "diastolic_bp")
    private Integer diastolicBp;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", length = 32)
    private Condition condition;

    @Column(name = "memo", length = 255)
    private String memo;
}
