package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ユーザーごとの食事の好み・制約。AI献立提案で考慮する(将来的にはレシピ検索等にも利用予定)。
 * 特にアレルギー(allergies)は献立から確実に除外するための必須情報として扱う
 * (MealRecommendationServiceでAIへの送信前・AI応答後の両方でチェックする)。
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
    @Column(name = "favorite_foods", length = 500)
    private String favoriteFoods;

    /** 苦手な食材(カンマ区切りの自由記述) */
    @Column(name = "disliked_foods", length = 500)
    private String dislikedFoods;

    /** アレルギー(カンマ区切りの自由記述)。献立から必ず除外する */
    @Column(name = "allergies", length = 500)
    private String allergies;

    /** 食事制限(例: ベジタリアン、減塩 など。カンマ区切りの自由記述) */
    @Column(name = "dietary_restrictions", length = 500)
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
