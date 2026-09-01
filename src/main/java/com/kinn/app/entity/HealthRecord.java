package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedConditionConverter;
import com.kinn.app.security.crypto.EncryptedDoubleConverter;
import com.kinn.app.security.crypto.EncryptedIntegerConverter;
import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 1日分の健康記録。
 * 月次集計(平均体重・平均睡眠時間など)はここでは持たず、HealthService 側で
 * 都度計算する(集計ロジックを1箇所にまとめるため)。
 *
 * 体重・睡眠時間・歩数・運動時間・血圧・体調・メモは要配慮個人情報のため、{@code @Convert}で
 * DB保存前にAES-256-GCM暗号化する(HealthDataEncryptor参照。セキュリティレビュー③対応。
 * V5マイグレーションで列をtextへ変更済み)。
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
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "weight_kg", columnDefinition = "text")
    private Double weightKg;

    /** 睡眠時間(時間) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "sleep_hours", columnDefinition = "text")
    private Double sleepHours;

    /** 歩数 */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "steps", columnDefinition = "text")
    private Integer steps;

    /** 運動時間(分) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "exercise_minutes", columnDefinition = "text")
    private Integer exerciseMinutes;

    /** 収縮期血圧(上) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "systolic_bp", columnDefinition = "text")
    private Integer systolicBp;

    /** 拡張期血圧(下) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "diastolic_bp", columnDefinition = "text")
    private Integer diastolicBp;

    // @Enumeratedとは併用できないため、Enum名そのものを暗号化するConverterに置き換えている
    @Convert(converter = EncryptedConditionConverter.class)
    @Column(name = "condition", columnDefinition = "text")
    private Condition condition;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "memo", columnDefinition = "text")
    private String memo;
}
