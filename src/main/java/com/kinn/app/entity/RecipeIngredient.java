package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * レシピ({@link Recipe})1件に含まれる材料1行(Phase 4)。
 * quantity/unitを数値と単位に分けて持つのは、将来の「買い物リスト自動生成」
 * (必要量 - 冷蔵庫の在庫量、の引き算)をそのまま計算できるようにするため。
 */
@Entity
@Table(name = "recipe_ingredient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 親{@link Recipe}のID */
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 数量(未入力可。「適量」のような材料もあるため任意項目とする) */
    @Column(name = "quantity")
    private Double quantity;

    /** 単位(例: "g", "個", "本") */
    @Column(name = "unit", length = 20)
    private String unit;

    /** 画面表示順 */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
