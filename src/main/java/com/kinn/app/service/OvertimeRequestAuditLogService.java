package com.kinn.app.service;

import com.kinn.app.entity.OvertimeRequest;
import com.kinn.app.entity.OvertimeRequestAuditAction;
import com.kinn.app.entity.OvertimeRequestAuditLog;
import com.kinn.app.entity.AppUser;
import com.kinn.app.repository.OvertimeRequestAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 残業申請の申請・承認・却下・取り下げ操作の監査ログ(overtime_request_audit_log)への記録を
 * 一箇所に集約する。AnnouncementAuditLogServiceと同じ考え方でREQUIRES_NEW(呼び出し元とは別の
 * 独立したトランザクション)で書き込み、ここでの書き込みが万一失敗しても、呼び出し元
 * (残業申請の申請・承認・却下・取り下げ本体)のトランザクション・永続化コンテキストを
 * 道連れにしない。
 */
@Service
public class OvertimeRequestAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(OvertimeRequestAuditLogService.class);

    private final OvertimeRequestAuditLogRepository overtimeRequestAuditLogRepository;

    public OvertimeRequestAuditLogService(OvertimeRequestAuditLogRepository overtimeRequestAuditLogRepository) {
        this.overtimeRequestAuditLogRepository = overtimeRequestAuditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AppUser actor, OvertimeRequest request, OvertimeRequestAuditAction action) {
        try {
            overtimeRequestAuditLogRepository.save(OvertimeRequestAuditLog.builder()
                    .overtimeRequestId(request.getId())
                    .actorUserId(actor.getId())
                    .action(action)
                    .build());
        } catch (Exception e) {
            log.warn("overtime_request_audit_logへの記録に失敗しました(呼び出し元の処理には影響しません)", e);
        }
    }
}
