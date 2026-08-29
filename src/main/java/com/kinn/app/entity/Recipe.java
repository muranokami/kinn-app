package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * レシピ(料理名・調理方法・調理時間・難易度・調理器具)の1件(Phase 4)。
 * 材料は{@link RecipeIngredient}、調理手順は{@link RecipeStep}に分けて持つ
 * (このアプリの既存Entityに倣い@OneToMany等の関連は張らずrecipeIdカラムのみで参照する)。
 *
 * 将来の「1週間の献立」「冷蔵庫の食材から献立」機能では、このRecipeを候補として
 * 参照する想定。AI献立提案(MealRecommendationService)とは、料理名をキーに
 * {@link com.kinn.app.service.RecipeService#generateForDish}で連携する(Phase 5)。
 *
 * 作り置き拡張を見込み、保存方法・保存期間・再加熱方法もあらかじめ持たせている(すべて任意)。
 */
@Entity
@Table(name = "recipe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** レシピの持ち主。他ユーザーのレシピは参照・編集・削除できない */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cooking_method", length = 32)
    private CookingMethod cookingMethod;

    /** 準備時間(分) */
    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    /** 調理時間(分) */
    @Column(name = "cook_minutes")
    private Integer cookMinutes;

    /** 難易度(1〜3。★の数として画面表示する) */
    @Column(name = "difficulty")
    private Integer difficulty;

    /** 使用する調理器具(自由記述、カンマ区切り。例: "フライパン, 鍋") */
    @Column(name = "equipment", length = 200)
    private String equipment;

    /** メモ(任意) */
    @Column(name = "memo", length = 500)
    private String memo;

    // ---- 栄養情報(⑰健康管理との連携。1人前・1食分の目安。すべて任意) ----
    // MealRecordの栄養素カラムと同じ命名・粒度にしている(将来「今日の献立の栄養バランス」
    // 機能を作る際に、meal_record/recipeどちらの栄養値も同じ形で扱えるようにするため)。

    @Column(name = "calories")
    private Integer calories;

    /** たんぱく質(g) */
    @Column(name = "protein_g")
    private Double proteinG;

    /** 脂質(g) */
    @Column(name = "fat_g")
    private Double fatG;

    /** 炭水化物(g) */
    @Column(name = "carbs_g")
    private Double carbsG;

    /** 食塩相当量(g) */
    @Column(name = "salt_g")
    private Double saltG;

    // ---- 作り置き拡張用(将来利用。現時点では画面から未入力でもよい) ----

    @Column(name = "storage_fridge")
    private Boolean storageFridge;

    @Column(name = "storage_freezer")
    private Boolean storageFreezer;

    /** 保存可能日数の目安 */
    @Column(name = "storage_days")
    private Integer storageDays;

    /** 再加熱方法(自由記述。例: "電子レンジ600Wで2分") */
    @Column(name = "reheat_method", length = 200)
    private String reheatMethod;

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
