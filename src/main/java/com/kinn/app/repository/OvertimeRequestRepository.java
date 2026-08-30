package com.kinn.app.repository;

import com.kinn.app.entity.OvertimeRequest;
import com.kinn.app.entity.OvertimeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

    /**
     * 1件取得用(㊳非常に重要)。idだけでなくcompanyIdも一致することを要求するため、
     * 他社の申請IDを推測して指定してもOptional.emptyになる(URL改ざん対策。
     * AnnouncementRepository#findByIdAndCompanyIdと同じ考え方)。
     */
    Optional<OvertimeRequest> findByIdAndCompanyId(Long id, Long companyId);

    /** 申請者本人のマイページ用一覧。対象日の新しい順(同日は新しく作られたものを先に) */
    List<OvertimeRequest> findByCompanyIdAndApplicantUserIdOrderByTargetDateDescCreatedAtDesc(
            Long companyId, Long applicantUserId);

    /** 管理者向け: 部署未指定(全部署=会社全体)の一覧 */
    List<OvertimeRequest> findByCompanyIdOrderByTargetDateAsc(Long companyId);

    /**
     * 管理者向け: 部署で絞り込んだ一覧。company_id + department_id の組み合わせで
     * 他社データの混入を防ぐ(TaskRepositoryと同じ考え方)。
     */
    List<OvertimeRequest> findByCompanyIdAndDepartmentIdOrderByTargetDateAsc(Long companyId, Long departmentId);

    /** トップページの「承認待ち件数」表示用の軽量COUNTクエリ(管理者向け) */
    long countByCompanyIdAndStatus(Long companyId, OvertimeRequestStatus status);

    /**
     * トップページの「直近でステータスが更新された申請」件数表示用(申請者本人向け)。
     * まだ承認待ち(PENDING)ではない = 承認/却下された申請のうち、指定日時以降に更新された
     * ものだけを数える軽量COUNTクエリ(AnnouncementRepository#countUnreadForUserと同じ考え方で、
     * 一覧をまとめて取得しない)。
     */
    long countByCompanyIdAndApplicantUserIdAndStatusNotAndUpdatedAtAfter(
            Long companyId, Long applicantUserId, OvertimeRequestStatus excludedStatus, LocalDateTime after);
}
