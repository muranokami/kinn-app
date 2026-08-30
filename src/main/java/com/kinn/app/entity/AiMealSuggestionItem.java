package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedDoubleConverter;
import com.kinn.app.security.crypto.EncryptedIntegerConverter;
import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

/**
 * AI献立提案1回分({@link AiMealSuggestion})に含まれる1食分(朝食/昼食/夕食のいずれか)。
 * suggestionId で親を参照する(このアプリの既存Entityに倣い@ManyToOneは使わない)。
 *
 * 料理名・使用食材・栄養素・おすすめ理由は食生活や健康状態を推測しうる情報のため、
 * MealRecordと同様に{@code @Convert}でDB保存前にAES-256-GCM暗号化する
 * (HealthDataEncryptor参照。V9マイグレーションで対象列をtextへ変更)。mealType(区分)・
 * cookingMinutes(数値メタ情報)は暗号化しない。
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

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "dish_name", columnDefinition = "text")
    private String dishName;

    /** 使用食材(カンマ区切りの自由記述) */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ingredients", columnDefinition = "text")
    private String ingredients;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "calories", columnDefinition = "text")
    private Integer calories;

    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "protein_g", columnDefinition = "text")
    private Double proteinG;

    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "fat_g", columnDefinition = "text")
    private Double fatG;

    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "carbs_g", columnDefinition = "text")
    private Double carbsG;

    /** おおよその調理時間(分) */
    @Column(name = "cooking_minutes")
    private Integer cookingMinutes;

    /** この献立をおすすめする理由 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "reason", columnDefinition = "text")
    private String reason;
}
