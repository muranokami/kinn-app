package com.kinn.app.repository;

import com.kinn.app.entity.AccountSecurityLog;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 認証・アカウント操作系監査ログの追加専用リポジトリ。
 * HealthAuditLogRepositoryと同じ考え方で、CRUDメソッドを一切持たないマーカーインターフェース
 * {@link Repository} を継承し、save + 検索系メソッドだけを個別に公開する
 * (deleteやfind-then-updateによる改ざんを構造的にできなくする)。
 */
public interface AccountSecurityLogRepository extends Repository<AccountSecurityLog, Long> {

    AccountSecurityLog save(AccountSecurityLog entity);

    /** 対象ユーザーの操作履歴(将来、本人・管理者向けの閲覧画面を追加する場合のための参照メソッド) */
    List<AccountSecurityLog> findByTargetEmployeeIdOrderByOccurredAtDesc(String targetEmployeeId);
}
