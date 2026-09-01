package com.kinn.app.repository;

import com.kinn.app.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByEmployeeIdOrderByIdDesc(String employeeId);

    /** 常にこのメソッドを経由して取得することで、他ユーザーのレシピに到達できないようにする */
    Optional<Recipe> findByIdAndEmployeeId(Long id, String employeeId);
}
