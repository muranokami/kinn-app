package com.kinn.app.repository;

import com.kinn.app.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByCompanyIdAndLoginId(Long companyId, String loginId);

    boolean existsByCompanyIdAndLoginId(Long companyId, String loginId);

    /** 管理者の従業員一覧・検索用(必ず companyId で絞り込んでから使うこと=会社単位のアクセス制御) */
    List<AppUser> findByCompanyIdOrderByFullNameAsc(Long companyId);

    /** 部署で絞り込んだ従業員一覧用。companyIdも条件に含めることで他社の部署IDが紛れ込んでも安全 */
    List<AppUser> findByCompanyIdAndDepartmentIdOrderByFullNameAsc(Long companyId, Long departmentId);

    /** 部署一覧画面の所属人数表示用 */
    long countByCompanyIdAndDepartmentId(Long companyId, Long departmentId);

    /** 管理者の従業員詳細用。companyIdも同時に条件にすることで他社ユーザーへの到達を防ぐ */
    Optional<AppUser> findByIdAndCompanyId(Long id, Long companyId);
}
