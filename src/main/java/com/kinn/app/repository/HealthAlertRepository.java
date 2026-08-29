package com.kinn.app.repository;

import com.kinn.app.entity.HealthAlert;
import com.kinn.app.entity.HealthAlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HealthAlertRepository extends JpaRepository<HealthAlert, Long> {

    List<HealthAlert> findByEmployeeIdAndTriggeredDateBetweenOrderByTriggeredDateDesc(
            String employeeId, LocalDate from, LocalDate to);

    boolean existsByEmployeeIdAndAlertTypeAndTriggeredDate(
            String employeeId, HealthAlertType alertType, LocalDate triggeredDate);

    long countByTriggeredDateBetween(LocalDate from, LocalDate to);

    long countByEmployeeIdAndTriggeredDateBetween(String employeeId, LocalDate from, LocalDate to);

    /** 本人からの削除申請対応(HealthSelfDataDeletionService参照)に使う */
    void deleteByEmployeeId(String employeeId);
}
