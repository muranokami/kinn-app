package com.kinn.app.repository;

import com.kinn.app.entity.HealthProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HealthProfileRepository extends JpaRepository<HealthProfile, Long> {

    Optional<HealthProfile> findByEmployeeId(String employeeId);

    List<HealthProfile> findByEmployeeIdIn(List<String> employeeIds);

    /** 本人からの削除申請対応(HealthSelfDataDeletionService参照)に使う */
    void deleteByEmployeeId(String employeeId);

    @Query("select distinct p.employeeId from HealthProfile p")
    List<String> findDistinctEmployeeIds();
}
