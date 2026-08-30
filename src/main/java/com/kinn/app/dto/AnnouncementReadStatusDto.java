package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 管理者向け「既読状況」API(GET /api/admin/announcements/{id}/read-status)のレスポンス。
 *
 * 対象社員の範囲は、一般ユーザー向けのAnnouncementRepository#findVisibleForUserと完全に同じ条件
 * (companyId一致 かつ (departmentIdがnull=全社向け または自分のdepartmentIdと一致))で算出する
 * (AnnouncementService#resolveTargetUsers参照)。ここが一般ユーザー向け表示とズレると、
 * 「実際には届いていない人が既読者として数えられる」「本来対象外の人が対象に含まれる」
 * といった不整合が起きるため、必ず同じロジックを再利用すること。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementReadStatusDto {
    private Long announcementId;
    private String title;

    /** 対象部署。nullは全社向け */
    private Long departmentId;
    /** departmentIdがnullの場合は「全社」を返す */
    private String departmentName;

    private int totalCount;
    private int readCount;
    private int unreadCount;

    /** 既読者一覧(氏名・既読日時)。対象社員を氏名昇順で走査して振り分けるため氏名順になる */
    private List<AnnouncementReadUserDto> readUsers;
    /** 未読者一覧(氏名) */
    private List<AnnouncementUnreadUserDto> unreadUsers;
}
