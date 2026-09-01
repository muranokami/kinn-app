package com.kinn.app.repository;

import com.kinn.app.entity.AnnouncementAuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * お知らせ監査ログの追加専用リポジトリ。AccountSecurityLogRepositoryと同じ考え方で、
 * CRUDメソッドを一切持たないマーカーインターフェース{@link Repository}を継承し、
 * save + 検索系メソッドだけを個別に公開する(deleteやfind-then-updateによる改ざんを
 * 構造的にできなくする)。
 */
public interface AnnouncementAuditLogRepository extends Repository<AnnouncementAuditLog, Long> {

    AnnouncementAuditLog save(AnnouncementAuditLog entity);

    /** 自社の操作履歴(将来、管理者向けの閲覧画面を追加する場合のための参照メソッド) */
    List<AnnouncementAuditLog> findByCompanyIdOrderByOccurredAtDesc(Long companyId);
}
