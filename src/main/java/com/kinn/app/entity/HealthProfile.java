package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedDoubleConverter;
import com.kinn.app.security.crypto.EncryptedDrinkingStatusConverter;
import com.kinn.app.security.crypto.EncryptedIntegerConverter;
import com.kinn.app.security.crypto.EncryptedSmokingStatusConverter;
import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ユーザーごとの健康プロフィール(基本情報)。
 * 「今日の体調チェック」のような毎日の記録ではなく、身長・体重・血圧など
 * 本人が随時編集するベースライン情報を1人1行で保持する。
 * BMIは身長・体重から都度計算するため、ここでは保持しない(HealthProfileService参照)。
 *
 * 以前は「普段のストレス度(stress_level)」も項目として持っていたが、労働安全衛生法上の
 * ストレスチェック制度(第66条の10)と紛らわしい外形を作らないため、アプリからは
 * 完全に削除した(docs/health-audit-legal-checklist.md 参照)。DB上の`stress_level`列自体は
 * 既存データ保護のため残しているが、このEntityからは参照しない。
 *
 * 身長・体重・血圧・体温・運動時間・睡眠時間・喫煙/飲酒状況・メモは要配慮個人情報のため、
 * {@code @Convert}でDB保存前にAES-256-GCM暗号化する(HealthDataEncryptor参照。
 * セキュリティレビュー③対応。V5マイグレーションで列をtextへ変更済み)。
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
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "height_cm", columnDefinition = "text")
    private Double heightCm;

    /** 体重(kg) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "weight_kg", columnDefinition = "text")
    private Double weightKg;

    /** 収縮期血圧(上) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "systolic_bp", columnDefinition = "text")
    private Integer systolicBp;

    /** 拡張期血圧(下) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "diastolic_bp", columnDefinition = "text")
    private Integer diastolicBp;

    /** 体温(℃) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "body_temperature", columnDefinition = "text")
    private Double bodyTemperature;

    /** 平均的な運動時間(分/日) */
    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "exercise_minutes", columnDefinition = "text")
    private Integer exerciseMinutes;

    /** 平均睡眠時間(時間) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "avg_sleep_hours", columnDefinition = "text")
    private Double avgSleepHours;

    // @Enumeratedとは併用できないため、Enum名そのものを暗号化するConverterに置き換えている
    // (AbstractEncryptedEnumConverterのjavadoc参照)。
    @Convert(converter = EncryptedSmokingStatusConverter.class)
    @Column(name = "smoking_status", columnDefinition = "text")
    private SmokingStatus smokingStatus;

    @Convert(converter = EncryptedDrinkingStatusConverter.class)
    @Column(name = "drinking_status", columnDefinition = "text")
    private DrinkingStatus drinkingStatus;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "health_memo", columnDefinition = "text")
    private String healthMemo;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
