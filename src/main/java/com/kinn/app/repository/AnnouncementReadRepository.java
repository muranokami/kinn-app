package com.kinn.app.repository;

import com.kinn.app.entity.AnnouncementRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, Long> {

    boolean existsByAnnouncementIdAndAppUserId(Long announcementId, Long appUserId);

    /** ログインユーザーの既読お知らせID一覧。一覧画面で1件ずつ既読判定するより1回のクエリで済ませる */
    List<AnnouncementRead> findByAppUserId(Long appUserId);

    /** 管理者向け既読状況API用。指定お知らせの既読記録全件(対象社員と突き合わせてreadAtを引く) */
    List<AnnouncementRead> findByAnnouncementId(Long announcementId);

    /**
     * 管理者の投稿一覧の既読/未読人数表示用の軽量COUNTクエリ。AnnouncementRepository#findVisibleForUser
     * と同じ可視条件(companyId一致 かつ (departmentIdがnull または一致))で対象社員を絞り込んだ上で、
     * そのうち既読記録があるものだけを数える。単純に announcement_read を丸ごとCOUNTするのではなく
     * app_user側の条件を必ず経由することで、部署異動・退職などで対象外になったユーザーの既読記録が
     * 人数として紛れ込まないようにする。
     */
    @Query("SELECT COUNT(r) FROM AnnouncementRead r WHERE r.announcementId = :announcementId "
            + "AND r.appUserId IN (SELECT u.id FROM AppUser u WHERE u.companyId = :companyId "
            + "AND (:departmentId IS NULL OR u.departmentId = :departmentId))")
    long countScopedReadsForAnnouncement(@Param("announcementId") Long announcementId,
                                          @Param("companyId") Long companyId,
                                          @Param("departmentId") Long departmentId);
}
