package com.kinn.app.repository;

import com.kinn.app.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            String employeeId, LocalDate from, LocalDate to);

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(String employeeId, LocalDate workDate);

    /** 管理者ダッシュボード(休日勤務状況)で全社員を横断集計するために使う(HealthProfileRepositoryと同じ方式) */
    @Query("select distinct r.employeeId from AttendanceRecord r")
    List<String> findDistinctEmployeeIds();
}
