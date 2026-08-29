package com.kinn.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI API(Anthropic Messages API)のレスポンステキストをJSONとしてパースする際の受け皿。
 * プロンプト側でこの構造(breakfast/lunch/dinner + summary)で返すよう指示している
 * (AiMealClient参照)。外部からの応答であり未知フィールドを含みうるため ignoreUnknown=true。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRawSuggestionDto {

    private String summary;
    private Meal breakfast;
    private Meal lunch;
    private Meal dinner;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meal {
        private String name;
        private String ingredients;
        private String reason;
        private Integer calories;
        private Double proteinG;
        private Double fatG;
        private Double carbsG;
        private Integer cookingMinutes;
    }
}
