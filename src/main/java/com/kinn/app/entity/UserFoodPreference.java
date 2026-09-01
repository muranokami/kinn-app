package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ユーザーごとの食事の好み・制約。AI献立提案で考慮する(将来的にはレシピ検索等にも利用予定)。
 * 特にアレルギー(allergies)は献立から確実に除外するための必須情報として扱う
 * (MealRecommendationServiceでAIへの送信前・AI応答後の両方でチェックする)。
 *
 * favoriteFoods/dislikedFoods/allergies/dietaryRestrictionsは、要配慮個人情報への該当有無が
 * 未確認のまま(docs/health-audit-legal-checklist.md 9.参照)ではあるものの、個人の身体的特徴・
 * 健康状態に関わりうる自由記述のため、他の健康管理データと同様に{@code @Convert}で
 * AES-256-GCM暗号化する(セキュリティレビューで指摘・2026-08-30対応。V6マイグレーション参照)。
 * budgetYen/cookingMinutes/selfCooked/cookingLevelは単なる調理条件の希望であり、暗号化対象には
 * 含めていない。
 */
@Entity
@Table(
        name = "user_food_preference",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFoodPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    /** 好きな食材(カンマ区切りの自由記述) */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "favorite_foods", columnDefinition = "text")
    private String favoriteFoods;

    /** 苦手な食材(カンマ区切りの自由記述) */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "disliked_foods", columnDefinition = "text")
    private String dislikedFoods;

    /** アレルギー(カンマ区切りの自由記述)。献立から必ず除外する */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "allergies", columnDefinition = "text")
    private String allergies;

    /** 食事制限(例: ベジタリアン、減塩 など。カンマ区切りの自由記述) */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "dietary_restrictions", columnDefinition = "text")
    private String dietaryRestrictions;

    /** 1食あたりの予算(円) */
    @Column(name = "budget_yen")
    private Integer budgetYen;

    /** 許容できる調理時間(分) */
    @Column(name = "cooking_minutes")
    private Integer cookingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_cooked", length = 32)
    private SelfCookedPreference selfCooked;

    @Enumerated(EnumType.STRING)
    @Column(name = "cooking_level", length = 32)
    private CookingLevel cookingLevel;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
