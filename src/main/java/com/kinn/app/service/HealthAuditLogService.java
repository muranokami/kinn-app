package com.kinn.app.service;

import com.kinn.app.dto.HealthAuditLogDto;
import com.kinn.app.dto.HealthAuditSearchResultDto;
import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditLog;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.repository.HealthAuditLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康管理監査ログの参照(検索・閲覧)を扱うサービス。記録処理自体は {@link com.kinn.app.audit.HealthAuditAspect}
 * が横断的に行うため、ここでは「参照」のみを扱う(追加専用リポジトリなので更新・削除は行わない)。
 *
 * company_idによるスコープ分離を必ずここで行い、他社の監査ログが混ざることはない
 * (管理者検索・自分専用ログ閲覧のどちらも、呼び出し元は必ずAuthenticationから取得した
 * 自分自身のcompanyIdをここへ渡すこと)。
 */
@Service
public class HealthAuditLogService {

    /** 一覧が際限なく肥大化しないための上限(将来ページングUIに拡張する余地は残す) */
    private static final int RESULT_LIMIT = 200;

    private final HealthAuditLogRepository healthAuditLogRepository;

    /** 保持期間(日数)。自動削除は未実装だが、画面表示用にハードコードせず設定値から取得する */
    private final int retentionDays;

    public HealthAuditLogService(HealthAuditLogRepository healthAuditLogRepository,
                                  @Value("${app.audit.health.retention-days:365}") int retentionDays) {
        this.healthAuditLogRepository = healthAuditLogRepository;
        this.retentionDays = retentionDays;
    }

    /** 一般ユーザー向け:「自分のデータに誰がアクセスしたか」を新しい順に返す */
    public List<HealthAuditLogDto> getMyAuditLogs(String selfEmployeeId, Long companyId) {
        return healthAuditLogRepository
                .findByCompanyIdAndTargetEmployeeIdOrderByOccurredAtDesc(
                        companyId, selfEmployeeId, PageRequest.of(0, RESULT_LIMIT))
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * 管理者向け:期間・対象ユーザー・操作種別で絞り込み検索する。
     * companyIdは必ず呼び出し元(管理者自身)のものを使い、targetLoginIdは同じ会社のloginIdとして
     * 解釈する(= companyId + "|" + targetLoginId)。他社のemployeeId文字列を直接受け取らないため、
     * 仮に不正な値を渡されても company_id 条件で弾かれ、他社データが混ざることはない。
     */
    public HealthAuditSearchResultDto searchForAdmin(Long companyId,
                                                       String targetLoginId,
                                                       HealthAuditAction action,
                                                       HealthAuditResource resource,
                                                       LocalDate from,
                                                       LocalDate to) {
        String targetEmployeeId = (targetLoginId == null || targetLoginId.isBlank())
                ? null
                : companyId + "|" + targetLoginId;
        LocalDateTime fromDateTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toDateTime = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);

        // company_idは常に必須条件にし、他の条件は指定された(nullでない)ものだけ追加する。
        // JPQLの「(:x is null or …)」パターンは、enum/日時パラメータがnullのときPostgreSQLが
        // 型を推論できず失敗することがあるため、Specificationで動的に組み立てる。
        Specification<HealthAuditLog> spec = (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
        if (targetEmployeeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetEmployeeId"), targetEmployeeId));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (resource != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resource"), resource));
        }
        if (fromDateTime != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDateTime));
        }
        if (toDateTime != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), toDateTime));
        }

        List<HealthAuditLogDto> items = healthAuditLogRepository
                .findAll(spec, PageRequest.of(0, RESULT_LIMIT, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .stream()
                .map(this::toDto)
                .toList();

        return HealthAuditSearchResultDto.builder()
                .items(items)
                .retentionDays(retentionDays)
                .build();
    }

    private HealthAuditLogDto toDto(HealthAuditLog e) {
        return HealthAuditLogDto.builder()
                .id(e.getId())
                .occurredAt(e.getOccurredAt())
                .actorEmployeeId(e.getActorEmployeeId())
                .actorName(e.getActorName())
                .targetEmployeeId(e.getTargetEmployeeId())
                .targetName(e.getTargetName())
                .action(e.getAction())
                .resource(e.getResource())
                .targetRef(e.getTargetRef())
                .ipAddress(e.getIpAddress())
                .userAgent(e.getUserAgent())
                .result(e.getResult())
                .errorMessage(e.getErrorMessage())
                .selfAction(e.getTargetEmployeeId() != null && e.getTargetEmployeeId().equals(e.getActorEmployeeId()))
                .build();
    }
}
