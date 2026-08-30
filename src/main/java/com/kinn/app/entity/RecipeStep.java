package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

/**
 * レシピ({@link Recipe})1件に含まれる調理手順1ステップ(Phase 4)。
 * 「1. 食材を切る」「2. フライパンを加熱する」のように stepNo 順に並べて表示する。
 *
 * 調理手順は食生活を推測しうる情報のため、Recipeと同様に{@code @Convert}でDB保存前に
 * AES-256-GCM暗号化する(HealthDataEncryptor参照。V9マイグレーションで対象列をtextへ変更)。
 * stepNoは並び順の制御にのみ使う数値のため暗号化しない。
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

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
}
