package com.kinn.app.controller;

import com.kinn.app.dto.AnnouncementDto;
import com.kinn.app.dto.AnnouncementUnreadCountDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.AnnouncementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 一般ユーザー向けお知らせAPI。ログインしていれば誰でも(ROLE_USER/ROLE_ADMINどちらも)呼べる。
 * 会社・部署は常にログインユーザー(principal)から解決し、リクエストパラメータで
 * companyId/departmentIdを受け取ることは一切ない(TaskControllerと同じ設計方針)。
 * 管理者専用のAdminAnnouncementController(/api/admin/announcements)とは別。
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** 自分が閲覧対象のお知らせ一覧(重要度→投稿日時の新しい順)。各件に自分の既読状態を含む */
    @GetMapping
    public List<AnnouncementDto> getAnnouncements(@AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.getForUser(principal.getAppUser());
    }

    /** 既読にする(冪等。既に既読の場合は何もしない) */
    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        announcementService.markRead(principal.getAppUser(), id);
    }

    /** トップページの情報チップ表示用の軽量な未読件数のみ(一覧全体は取得しない) */
    @GetMapping("/unread-count")
    public AnnouncementUnreadCountDto getUnreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return AnnouncementUnreadCountDto.builder()
                .unreadCount(announcementService.getUnreadCount(principal.getAppUser()))
                .build();
    }
}
