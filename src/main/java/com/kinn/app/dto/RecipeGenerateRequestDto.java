package com.kinn.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 「レシピを見る/レシピを作成」(⑥⑦⑧⑨)のリクエスト。
 * 既に同名レシピがあれば流用し、無ければAIで生成して保存する({@link com.kinn.app.service.RecipeService#generateForDish}参照)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecipeGenerateRequestDto {

    @NotBlank(message = "料理名は必須です")
    private String dishName;

    /** AI献立提案の「使用食材」など、生成のヒントにする自由記述(任意) */
    private String ingredientsHint;

    /**
     * 呼び出し元の食事記録ID(任意)。指定すると、解決/生成したレシピをその食事記録に
     * 自動で紐付ける(⑫)。自分の食事記録でない場合は無視される(㉔)。
     */
    private Long mealRecordId;
}
