package com.kinn.app.repository;

import com.kinn.app.entity.MealRecord;
import com.kinn.app.entity.MealType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealRecordRepository extends JpaRepository<MealRecord, Long> {

    List<MealRecord> findByEmployeeIdAndMealDateOrderByMealTypeAscMealTimeAsc(
            String employeeId, LocalDate mealDate);

    List<MealRecord> findByEmployeeIdAndMealDateBetweenOrderByMealDateAscMealTypeAscMealTimeAsc(
            String employeeId, LocalDate from, LocalDate to);

    /** 朝・昼・夕の上書き保存用。同じ日・区分の既存レコードがあれば最初の1件を取得する */
    Optional<MealRecord> findFirstByEmployeeIdAndMealDateAndMealTypeOrderByIdAsc(
            String employeeId, LocalDate mealDate, MealType mealType);

    Optional<MealRecord> findByIdAndEmployeeId(Long id, String employeeId);
}
