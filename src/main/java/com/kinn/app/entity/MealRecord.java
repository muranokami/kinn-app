package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedDoubleConverter;
import com.kinn.app.security.crypto.EncryptedIntegerConverter;
import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 1食分の食事記録。
 * 「何を食べたか」(items)だけの簡単な入力にも対応できるよう、栄養素などの項目は
 * すべて任意(null許容)にしている。朝・昼・夕はその日1件を上書きする運用を基本とし、
 * 間食(SNACK)は同じ日に複数件登録できる(unique制約は設けていない)。
 *
 * 何を食べたか・栄養素・写真・メモは食生活や健康状態を推測しうる情報のため、他の健康管理
 * データ(HealthCheck/HealthProfile/HealthRecord)と同様に{@code @Convert}でDB保存前に
 * AES-256-GCM暗号化する(HealthDataEncryptor参照。V8マイグレーションで対象列をtextへ変更)。
 * meal_type/meal_date/meal_time/recipe_idはクエリ条件(findByEmployeeIdAndMealDateAnd...)に
 * 使うため暗号化しない(暗号化するとDB側での絞り込みができなくなり、アプリ層で全件取得して
 * 比較する設計変更が必要になるため。他フィールドに比べて食事のタイミングだけでは健康状態の
 * 推測材料としての機微性が低いという判断でもある)。
 */
@Entity
@Table(name = "meal_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 32)
    private MealType mealType;

    /** 食事時刻(任意)。食事日時 = mealDate + mealTime */
    @Column(name = "meal_time")
    private LocalTime mealTime;

    /** 料理名(任意。例: 「鮭の塩焼き定食」) */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "dish_name", columnDefinition = "text")
    private String dishName;

    /**
     * 紐づくレシピ({@link Recipe#getId()})。任意(⑫食事記録とレシピの連携。Phase 5)。
     * dishNameだけ入力してレシピと未連携の記録も引き続き作れる(既存の食事記録機能を壊さないため)。
     * 参照先のレシピが削除された場合もこの列はそのまま残るが、アプリ側は「レシピが見つからない
     * =未作成扱い」として扱い、エラーにはしない(RecipeService/RecipeControllerのJavadoc参照)。
     */
    @Column(name = "recipe_id")
    private Long recipeId;

    /** 食べたもの(自由記述。例: 「トースト、目玉焼き、ヨーグルト」)。最低限これだけでも登録できる */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "items", columnDefinition = "text")
    private String items;

    /** 量(自由記述。例: 「茶碗1杯」「多め」) */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "amount", columnDefinition = "text")
    private String amount;

    @Convert(converter = EncryptedIntegerConverter.class)
    @Column(name = "calories", columnDefinition = "text")
    private Integer calories;

    /** たんぱく質(g) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "protein_g", columnDefinition = "text")
    private Double proteinG;

    /** 脂質(g) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "fat_g", columnDefinition = "text")
    private Double fatG;

    /** 炭水化物(g) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "carbs_g", columnDefinition = "text")
    private Double carbsG;

    /** 食物繊維(g) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "fiber_g", columnDefinition = "text")
    private Double fiberG;

    /** 塩分(g) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "salt_g", columnDefinition = "text")
    private Double saltG;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "photo_url", columnDefinition = "text")
    private String photoUrl;

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
