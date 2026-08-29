package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * AI献立提案1回分({@link AiMealSuggestion})に含まれる1食分(朝食/昼食/夕食のいずれか)。
 * suggestionId で親を参照する(このアプリの既存Entityに倣い@ManyToOneは使わない)。
 */
@Entity
@Table(name = "ai_meal_suggestion_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMealSuggestionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 親{@link AiMealSuggestion}のID */
    @Column(name = "suggestion_id", nullable = false)
    private Long suggestionId;

    /** SNACKは対象外(BREAKFAST/LUNCH/DINNERのいずれか) */
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 32)
    private MealType mealType;

    @Column(name = "dish_name", length = 200)
    private String dishName;

    /** 使用食材(カンマ区切りの自由記述) */
    @Column(name = "ingredients", length = 500)
    private String ingredients;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "protein_g")
    private Double proteinG;

    @Column(name = "fat_g")
    private Double fatG;

    @Column(name = "carbs_g")
    private Double carbsG;

    /** おおよその調理時間(分) */
    @Column(name = "cooking_minutes")
    private Integer cookingMinutes;

    /** この献立をおすすめする理由 */
    @Column(name = "reason", length = 1000)
    private String reason;
}
