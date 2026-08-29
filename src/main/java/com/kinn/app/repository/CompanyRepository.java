package com.kinn.app.repository;

import com.kinn.app.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * ログイン(AuthService#resolveUsername)専用。会社名は一意制約を持たないため、
     * 万一同名の会社が複数存在する場合はSpring Data JPAが例外を投げる(意図的。
     * Company.javaのjavadoc参照)。新規登録のテナント決定にはfindByCompanyCodeを使うこと。
     */
    Optional<Company> findByName(String name);

    boolean existsByName(String name);

    /** 新規登録(既存の会社に参加)・会社情報照会で使う、テナントを一意に決定するための検索 */
    Optional<Company> findByCompanyCode(String companyCode);

    boolean existsByCompanyCode(String companyCode);
}
