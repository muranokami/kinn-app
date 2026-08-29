package com.kinn.app.controller;

import com.kinn.app.dto.RecipeDto;
import com.kinn.app.dto.RecipeGenerateRequestDto;
import com.kinn.app.dto.RecipeSummaryDto;
import com.kinn.app.service.RecipeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * レシピ・調理方法・調理手順API。ログイン中のユーザーのレシピのみを参照・更新・削除できる。
 */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /** 一覧(材料・手順は含まない軽量表示) */
    @GetMapping
    public List<RecipeSummaryDto> getAll(Authentication authentication) {
        return recipeService.getAll(authentication.getName());
    }

    /** 詳細(材料・手順を含む) */
    @GetMapping("/{id}")
    public RecipeDto getById(
            @PathVariable Long id,
            Authentication authentication) {
        return recipeService.getById(authentication.getName(), id);
    }

    /** 新規登録 */
    @PostMapping
    public RecipeDto create(
            @Valid @RequestBody RecipeDto dto,
            Authentication authentication) {
        dto.setId(null); // POSTは常に新規(bodyにidが混入していても無視する)
        return recipeService.save(authentication.getName(), dto);
    }

    /** 更新 */
    @PutMapping("/{id}")
    public RecipeDto update(
            @PathVariable Long id,
            @Valid @RequestBody RecipeDto dto,
            Authentication authentication) {
        dto.setId(id);
        return recipeService.save(authentication.getName(), dto);
    }

    /** 削除 */
    @DeleteMapping("/{id}")
    public void delete(
            @PathVariable Long id,
            Authentication authentication) {
        recipeService.delete(authentication.getName(), id);
    }

    /**
     * 「レシピを見る/レシピを作成」(⑥⑦⑧⑨⑫)。同名の既存レシピがあればそれを返し、
     * 無ければAIで生成して保存する。mealRecordIdを指定すると、その食事記録にも紐付ける。
     */
    @PostMapping("/ai-generate")
    public RecipeDto generateForDish(
            @Valid @RequestBody RecipeGenerateRequestDto request,
            Authentication authentication) {
        return recipeService.generateForDish(authentication.getName(), request);
    }
}
