package com.kinn.app.repository;

import com.kinn.app.entity.OvertimeRequestAuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 残業申請監査ログの追加専用リポジトリ。AnnouncementAuditLogRepositoryと同じ考え方で、
 * CRUDメソッドを一切持たないマーカーインターフェース{@link Repository}を継承し、
 * save + 検索系メソッドだけを個別に公開する(deleteやfind-then-updateによる改ざんを
 * 構造的にできなくする)。
 */
public interface OvertimeRequestAuditLogRepository extends Repository<OvertimeRequestAuditLog, Long> {

    OvertimeRequestAuditLog save(OvertimeRequestAuditLog entity);

    /** 特定の申請の操作履歴(将来、管理者向けの閲覧画面を追加する場合のための参照メソッド) */
    List<OvertimeRequestAuditLog> findByOvertimeRequestIdOrderByActedAtAsc(Long overtimeRequestId);
}
