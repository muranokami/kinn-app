package com.kinn.app.entity;

import com.kinn.app.security.crypto.EncryptedDoubleConverter;
import com.kinn.app.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

/**
 * レシピ({@link Recipe})1件に含まれる材料1行(Phase 4)。
 * quantity/unitを数値と単位に分けて持つのは、将来の「買い物リスト自動生成」
 * (必要量 - 冷蔵庫の在庫量、の引き算)をそのまま計算できるようにするため。
 *
 * 材料名・数量・単位は食生活を推測しうる情報のため、Recipeと同様に{@code @Convert}で
 * DB保存前にAES-256-GCM暗号化する(HealthDataEncryptor参照。V9マイグレーションで
 * 対象列をtextへ変更)。displayOrderは画面表示順の制御にのみ使う数値のため暗号化しない。
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

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    /** 数量(未入力可。「適量」のような材料もあるため任意項目とする) */
    @Convert(converter = EncryptedDoubleConverter.class)
    @Column(name = "quantity", columnDefinition = "text")
    private Double quantity;

    /** 単位(例: "g", "個", "本") */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "unit", columnDefinition = "text")
    private String unit;

    /** 画面表示順 */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
