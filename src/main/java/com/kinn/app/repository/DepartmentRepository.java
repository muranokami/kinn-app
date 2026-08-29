package com.kinn.app.repository;

import com.kinn.app.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Department> findByCompanyIdAndName(Long companyId, String name);

    boolean existsByCompanyIdAndName(Long companyId, String name);

    /** 会社単位のアクセス制御用。他社の部署IDへは到達できないようにする */
    Optional<Department> findByIdAndCompanyId(Long id, Long companyId);
}
