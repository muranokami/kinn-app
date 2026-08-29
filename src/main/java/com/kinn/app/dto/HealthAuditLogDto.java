package com.kinn.app.dto;

import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;
import com.kinn.app.entity.HealthAuditResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 健康管理監査ログの表示用DTO。健康情報の値そのものは含まない(元データにも保存していない)。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAuditLogDto {
    private Long id;
    private LocalDateTime occurredAt;
    private String actorEmployeeId;
    private String actorName;
    private String targetEmployeeId;
    private String targetName;
    private HealthAuditAction action;
    private HealthAuditResource resource;
    private String targetRef;
    private String ipAddress;
    private String userAgent;
    private HealthAuditResult result;
    private String errorMessage;
    /** actorEmployeeId == targetEmployeeId かどうか(自分自身のデータへの操作か、他人のデータへの操作かの判定用) */
    private boolean selfAction;
}
