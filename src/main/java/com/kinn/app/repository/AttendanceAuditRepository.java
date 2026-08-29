package com.kinn.app.repository;

import com.kinn.app.entity.AttendanceAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceAuditRepository extends JpaRepository<AttendanceAudit, Long> {

    List<AttendanceAudit> findByTargetEmployeeIdOrderByEditedAtDesc(String targetEmployeeId);

    List<AttendanceAudit> findByTargetEmployeeIdAndWorkDateOrderByEditedAtDesc(String targetEmployeeId, java.time.LocalDate workDate);
}
