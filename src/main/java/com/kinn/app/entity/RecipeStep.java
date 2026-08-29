package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * レシピ({@link Recipe})1件に含まれる調理手順1ステップ(Phase 4)。
 * 「1. 食材を切る」「2. フライパンを加熱する」のように stepNo 順に並べて表示する。
 */
@Entity
@Table(name = "recipe_step")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 親{@link Recipe}のID */
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    /** 手順の番号(1から開始) */
    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "description", nullable = false, length = 500)
    private String description;
}
