package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedDoubleConverter;
import com.kinn.app.security.crypto.EncryptedHealthConditionLevelConverter;
import com.kinn.app.security.crypto.EncryptedIntegerConverter;
import com.kinn.app.security.crypto.EncryptedStringConverter;
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
 *
 * 体調・睡眠時間・疲労度・運動時間・体温・メモは要配慮個人情報のため、{@code @Convert}で
 * DB保存前にAES-256-GCM暗号化する(HealthDataEncryptor参照。セキュリティレビュー③対応。
 * V5マイグレーションで列をtextへ変更済み)。
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

    // @Enumeratedとは併用できないため、Enum名そのものを暗号化するConverterに置き換えている
    @Convert(converter = EncryptedHealthConditionLevelConverter.class)
    @Column(name = "condition_level", columnDefinition = "text")
    private HealthConditionLevel condition;

    /** 睡眠時間(時間) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "sleep_hours", columnDefinition = "text")
    private Double sleepHours;

    /** 疲労度(1〜5、5が最も疲労が高い) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "fatigue_level", columnDefinition = "text")
    private Integer fatigueLevel;

    /** 運動時間(分) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "exercise_minutes", columnDefinition = "text")
    private Integer exerciseMinutes;

    /** 体温(℃) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "body_temperature", columnDefinition = "text")
    private Double bodyTemperature;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "memo", columnDefinition = "text")
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
