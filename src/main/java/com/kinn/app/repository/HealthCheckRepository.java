package com.kinn.app.repository;

import com.kinn.app.entity.HealthCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {

    Optional<HealthCheck> findByEmployeeIdAndCheckDate(String employeeId, LocalDate checkDate);

    List<HealthCheck> findByEmployeeIdAndCheckDateBetweenOrderByCheckDateAsc(
            String employeeId, LocalDate from, LocalDate to);

    @Query("select distinct c.employeeId from HealthCheck c")
    List<String> findDistinctEmployeeIds();

    List<HealthCheck> findByCheckDateBetweenOrderByCheckDateAsc(LocalDate from, LocalDate to);
}
