package com.kinn.app.repository;

import com.kinn.app.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** 管理者の投稿管理一覧用。自社の投稿のみを新しい順で返す */
    List<Announcement> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    /**
     * 1件取得用(㊳非常に重要)。idだけでなくcompanyIdも一致することを要求するため、
     * 他社の投稿IDを推測して指定してもOptional.emptyになる(URL改ざん対策。TaskRepositoryと同じ考え方)。
     */
    Optional<Announcement> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * 一般ユーザーが閲覧できるお知らせ(㊳)。全社向け(departmentIdがnull)または自分の部署宛で、
     * 公開日時(publishedAt)を過ぎていて、かつ表示終了日時(expiresAt)が無いかまだ過ぎていない
     * ものだけを返す。
     *
     * departmentIdにユーザー自身の部署IDを渡すため、部署未所属ユーザー(departmentId=null)を渡した
     * 場合は「a.departmentId = :departmentId」がSQL上のNULL比較になり常に偽になる
     * (JPQL/SQLの三値論理)。結果として全社向け(IS NULL)の分岐だけが該当し、
     * 部署未所属ユーザーには全社向けのお知らせのみが表示される、という意図どおりの挙動になる。
     *
     * 重要度→投稿日時の並び替えは呼び出し側(AnnouncementService)でJavaのComparatorを使って行う
     * (importanceは@Enumerated(STRING)で文字列カラムのため、DB側のORDER BYでは
     * NORMAL/IMPORTANTの意味的な優先順にならないため)。
     */
    @Query("SELECT a FROM Announcement a WHERE a.companyId = :companyId "
            + "AND (a.departmentId IS NULL OR a.departmentId = :departmentId) "
            + "AND a.publishedAt <= :now "
            + "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    List<Announcement> findVisibleForUser(@Param("companyId") Long companyId,
                                           @Param("departmentId") Long departmentId,
                                           @Param("now") LocalDateTime now);

    /**
     * トップページの未読件数表示用の軽量COUNTクエリ。可視条件はfindVisibleForUserと同じで、
     * さらにannouncement_readに記録が無い(未読の)ものだけを数える。一覧をまとめて取得してから
     * 件数を数えるのではなく、この専用COUNTクエリ1回だけで完結させる(トップページを開くたびに
     * 重い処理を走らせないため)。
     */
    @Query("SELECT COUNT(a) FROM Announcement a WHERE a.companyId = :companyId "
            + "AND (a.departmentId IS NULL OR a.departmentId = :departmentId) "
            + "AND a.publishedAt <= :now "
            + "AND (a.expiresAt IS NULL OR a.expiresAt > :now) "
            + "AND NOT EXISTS (SELECT 1 FROM AnnouncementRead r WHERE r.announcementId = a.id AND r.appUserId = :userId)")
    long countUnreadForUser(@Param("companyId") Long companyId,
                             @Param("departmentId") Long departmentId,
                             @Param("userId") Long userId,
                             @Param("now") LocalDateTime now);
}
