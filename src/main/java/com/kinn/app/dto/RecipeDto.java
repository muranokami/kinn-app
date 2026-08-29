package com.kinn.app.dto;

import com.kinn.app.entity.CookingMethod;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** レシピの詳細(一覧のカード表示にも、詳細表示・登録・編集フォームにもこの1つを使う) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeDto {
    private Long id;

    @NotBlank(message = "料理名は必須です")
    private String name;

    private CookingMethod cookingMethod;
    private String cookingMethodLabel;

    private Integer prepMinutes;
    private Integer cookMinutes;

    /** prepMinutes + cookMinutes(保存はせず読み取り時に計算する) */
    private Integer totalMinutes;

    /** 1〜3(★の数) */
    private Integer difficulty;

    private String equipment;
    private String memo;

    /** 栄養情報(⑰。1人前・1食分の目安。すべて任意) */
    private Integer calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double saltG;

    private Boolean storageFridge;
    private Boolean storageFreezer;
    private Integer storageDays;
    private String reheatMethod;

    @Builder.Default
    private List<RecipeIngredientDto> ingredients = List.of();

    /** 手順本文のみの配列。順序 = 配列の並び順(1番目が手順1) */
    @Builder.Default
    private List<String> steps = List.of();
}
