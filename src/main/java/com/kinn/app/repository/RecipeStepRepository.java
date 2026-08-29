package com.kinn.app.repository;

import com.kinn.app.entity.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Long> {

    List<RecipeStep> findByRecipeIdOrderByStepNoAsc(Long recipeId);

    void deleteByRecipeId(Long recipeId);
}
