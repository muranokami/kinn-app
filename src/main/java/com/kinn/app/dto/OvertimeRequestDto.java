package com.kinn.app.dto;

import com.kinn.app.entity.OvertimeRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 残業申請1件。申請・一覧表示・承認/却下のレスポンスすべてにこのDTOをそのまま使う
 * (AnnouncementDtoと同じ方針)。
 *
 * 新規申請リクエスト(POST /api/overtime-requests)で書き込み対象として使うのは
 * targetDate/plannedMinutes/reasonのみ。applicantUserId/companyId/departmentIdは
 * リクエストボディで受け取っても無視し、必ずログイン中の本人(AppUser)から
 * サーバー側で解決する(他人の申請を作れないようにするため)。
 *
 * id/departmentName/applicantName/status/statusLabel/approverUserId/approverName/
 * approvedAt/createdAt/updatedAt はレスポンス専用(リクエスト時に送っても無視される)。
 * rejectReasonは却下時のみ管理者APIから書き込む(却下API専用のOvertimeRequestRejectDto経由)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequestDto {
    private Long id;

    private Long departmentId;
    private String departmentName;

    private Long applicantUserId;
    private String applicantName;

    /** 対象日(残業を予定している日) */
    private LocalDate targetDate;
    /** 予定残業時間(分) */
    private Integer plannedMinutes;
    private String reason;

    private OvertimeRequestStatus status;
    private String statusLabel;

    /** 承認/却下を行った管理者のAppUser.id(PENDINGの間はnull) */
    private Long approverUserId;
    private String approverName;
    /** 承認日時(承認された場合のみ設定) */
    private LocalDateTime approvedAt;
    /** 却下理由(却下された場合のみ設定) */
    private String rejectReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
