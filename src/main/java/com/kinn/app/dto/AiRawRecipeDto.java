package com.kinn.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * AI API(Anthropic Messages API)のレシピ生成レスポンステキストをJSONとしてパースする際の受け皿
 * (㉓「AIから返された内容をそのままHTMLに表示せず、構造化データとして受け取る」の実装箇所)。
 * 外部からの応答であり未知フィールドや欠落フィールドを含みうるため ignoreUnknown=true とし、
 * 呼び出し元(RecipeService)で不正な値(null・範囲外の難易度など)を必ず検証してから使う。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRawRecipeDto {

    private String name;

    /** CookingMethod のenum名(例: "GRILL")を期待するが、想定外の値ならOTHER扱いにする */
    private String cookingMethod;

    private Integer prepMinutes;
    private Integer cookMinutes;

    /** 1〜3を期待するが、範囲外ならnull扱いにする */
    private Integer difficulty;

    private Integer calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double saltG;

    private List<Ingredient> ingredients;
    private List<String> steps;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ingredient {
        private String name;
        private Double quantity;
        private String unit;
    }
}
