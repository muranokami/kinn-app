package com.kinn.app.dto;

import com.kinn.app.entity.AnnouncementImportance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * お知らせ1件。登録・更新リクエストのボディにもレスポンスにもこのDTOをそのまま使う
 * (TaskDtoと同じ方針)。
 *
 * departmentId は管理者API(AdminAnnouncementController)からのみ書き込み対象として使う。
 * nullを指定すると「全社向け」になり、値を指定すると必ずAnnouncementService側で
 * 「自社に実在する部署か」を確認してから保存する(DepartmentService#requireOwned。
 * 他社の部署IDを指定しても保存できない)。一般ユーザー向けAPI(AnnouncementController)は
 * このDTOを書き込み用途では使わない(閲覧・既読化のみのため)。
 *
 * id/departmentName/importanceLabel/createdByUserId/createdByName/createdAt/updatedAt/read
 * はレスポンス専用(リクエスト時に送っても無視される)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementDto {
    private Long id;

    /** 対象部署。nullは全社向け */
    private Long departmentId;
    /** departmentIdがnullの場合は「全社」を返す */
    private String departmentName;

    private String title;
    private String body;

    private AnnouncementImportance importance;
    private String importanceLabel;

    private Long createdByUserId;
    private String createdByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 公開日時。未来日時を指定すると予約投稿になる */
    private LocalDateTime publishedAt;
    /** 表示終了日時。nullなら無期限 */
    private LocalDateTime expiresAt;

    /** ログイン中ユーザー本人の既読状態(一般ユーザー向けGET /api/announcements専用) */
    private boolean read;
}
