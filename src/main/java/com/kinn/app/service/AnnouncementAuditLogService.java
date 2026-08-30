package com.kinn.app.service;

import com.kinn.app.entity.Announcement;
import com.kinn.app.entity.AnnouncementAuditAction;
import com.kinn.app.entity.AnnouncementAuditLog;
import com.kinn.app.entity.AppUser;
import com.kinn.app.repository.AnnouncementAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * お知らせの投稿・編集・削除操作の監査ログ(announcement_audit_log)への記録を一箇所に集約する。
 * AccountSecurityLogServiceと同じ考え方でREQUIRES_NEW(呼び出し元とは別の独立したトランザクション)
 * で書き込み、ここでの書き込みが万一失敗しても、呼び出し元(お知らせの投稿・編集・削除本体)の
 * トランザクション・永続化コンテキストを道連れにしない。
 */
@Service
public class AnnouncementAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementAuditLogService.class);

    private final AnnouncementAuditLogRepository announcementAuditLogRepository;

    public AnnouncementAuditLogService(AnnouncementAuditLogRepository announcementAuditLogRepository) {
        this.announcementAuditLogRepository = announcementAuditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AppUser performedBy, Announcement announcement, AnnouncementAuditAction action) {
        try {
            announcementAuditLogRepository.save(AnnouncementAuditLog.builder()
                    .companyId(performedBy.getCompanyId())
                    .announcementId(announcement.getId())
                    .announcementTitle(announcement.getTitle())
                    .actionType(action)
                    .performedByEmployeeId(performedBy.effectiveEmployeeId())
                    .performedByName(performedBy.getFullName())
                    .build());
        } catch (Exception e) {
            log.warn("announcement_audit_logへの記録に失敗しました(呼び出し元の処理には影響しません)", e);
        }
    }
}
