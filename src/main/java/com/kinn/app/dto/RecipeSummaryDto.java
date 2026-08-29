package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** レシピ一覧(カード表示)用の軽量DTO。材料・手順は含まない(詳細取得時のみ) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeSummaryDto {
    private Long id;
    private String name;
    private String cookingMethodLabel;
    private Integer prepMinutes;
    private Integer cookMinutes;
    private Integer totalMinutes;
    private Integer difficulty;
    private String equipment;

    /** カロリー(kcal)。一覧カードでも一目で分かるよう軽量表示にも含める(⑰) */
    private Integer calories;
}
