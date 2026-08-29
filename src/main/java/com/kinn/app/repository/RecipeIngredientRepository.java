package com.kinn.app.repository;

import com.kinn.app.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    List<RecipeIngredient> findByRecipeIdOrderByDisplayOrderAsc(Long recipeId);

    List<RecipeIngredient> findByRecipeIdIn(List<Long> recipeIds);

    void deleteByRecipeId(Long recipeId);
}
