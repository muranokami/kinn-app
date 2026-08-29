package com.kinn.app.dto;

import com.kinn.app.entity.MealType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealRecordDto {
    private Long id;

    @NotNull(message = "食事日は必須です")
    private LocalDate mealDate;

    @NotNull(message = "食事区分は必須です")
    private MealType mealType;

    private LocalTime mealTime;
    private String dishName;

    /** 紐づくレシピのID(⑫)。未連携ならnull(「レシピを作成」を案内する対象) */
    private Long recipeId;
    private String items;
    private String amount;
    private Integer calories;
    private Double proteinG;
    private Double fatG;
    private Double carbsG;
    private Double fiberG;
    private Double saltG;
    private String photoUrl;
    private String memo;
}
