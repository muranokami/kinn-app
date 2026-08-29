package com.kinn.app.repository;

import com.kinn.app.entity.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {

    List<HealthRecord> findByEmployeeIdAndRecordDateBetweenOrderByRecordDateAsc(
            String employeeId, LocalDate from, LocalDate to);

    Optional<HealthRecord> findByEmployeeIdAndRecordDate(String employeeId, LocalDate recordDate);
}
