package com.kinn.app.repository;

import com.kinn.app.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByEmployeeIdOrderByIdDesc(String employeeId);

    /** 常にこのメソッドを経由して取得することで、他ユーザーのレシピに到達できないようにする */
    Optional<Recipe> findByIdAndEmployeeId(Long id, String employeeId);

    /**
     * 料理名からの検索(⑨同じ料理について不要な重複レシピを作らないための照合)。
     * 大文字小文字を区別しない完全一致。同名が複数ある場合はid降順で最新のものを返す。
     */
    Optional<Recipe> findFirstByEmployeeIdAndNameIgnoreCaseOrderByIdDesc(String employeeId, String name);
}
