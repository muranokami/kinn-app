package com.kinn.app.repository;

import com.kinn.app.entity.HealthAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 健康管理監査ログの追加専用リポジトリ。
 *
 * JpaRepository/CrudRepositoryではなく、CRUDメソッドを一切持たないマーカーインターフェース
 * {@link Repository} を継承し、必要なメソッド(save + 検索系)だけを個別に宣言している。
 * これにより deleteById / delete / deleteAll のような物理削除メソッドや、既存レコードを
 * 書き換えるための find-then-update的な操作を一切公開しない(呼び出し側は新しい
 * HealthAuditLog(id未設定)をsaveすることしかできず、事実上「追加専用」になる)。
 *
 * {@link JpaSpecificationExecutor} は検索専用(findAll/findOne/count等)のインターフェースで
 * こちらも更新・削除メソッドを持たないため、管理者検索画面の「期間・対象ユーザー・操作種別」
 * という複数の任意条件による絞り込みはここ経由のSpecification(HealthAuditLogServiceで組み立て)で行う。
 * ("条件がnullなら常にtrue"というJPQLの `(:x is null or …)` パターンは、enumや日時パラメータが
 * nullの場合にPostgreSQL/pgjdbcが型を推論できずエラーになることがあるため採用していない。)
 *
 * 保持期間経過後の自動削除バッチは今回実装しない(application.properties の
 * app.audit.health.retention-days は保持期間の「値」を管理するだけ)。将来バッチを追加する際は、
 * このインターフェースに deleteByOccurredAtBefore(LocalDateTime) 等を明示的に追加すること
 * (現状は存在しない=通常の実装コードから監査ログを削除する手段がない)。
 */
public interface HealthAuditLogRepository extends Repository<HealthAuditLog, Long>, JpaSpecificationExecutor<HealthAuditLog> {

    HealthAuditLog save(HealthAuditLog entity);

    /** 一般ユーザー向け:「自分のデータに誰がアクセスしたか」画面用。必ずcompanyIdでもスコープする */
    List<HealthAuditLog> findByCompanyIdAndTargetEmployeeIdOrderByOccurredAtDesc(
            Long companyId, String targetEmployeeId, Pageable pageable);
}
