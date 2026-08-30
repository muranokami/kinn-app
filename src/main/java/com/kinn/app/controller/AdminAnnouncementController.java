package com.kinn.app.controller;

import com.kinn.app.dto.AnnouncementDto;
import com.kinn.app.dto.AnnouncementReadCountDto;
import com.kinn.app.dto.AnnouncementReadStatusDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.AnnouncementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理者向けお知らせ管理API。ログイン中の管理者と同じ会社のお知らせのみを対象とする
 * (他社のデータは含めない。AdminTaskControllerと同じ設計方針)。
 * /api/admin/** は SecurityConfig で hasRole("ADMIN") に限定済みのため、一般ユーザーは到達できない。
 * クラスレベルの@PreAuthorizeも多層防御として付与している(SecurityConfigのjavadoc参照)。
 */
@RestController
@RequestMapping("/api/admin/announcements")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /** 自社の投稿一覧(投稿管理用) */
    @GetMapping
    public List<AnnouncementDto> getAnnouncements(@AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.getForAdmin(principal.getAppUser().getCompanyId());
    }

    /** 新規投稿。departmentIdを省略/nullにすると全社向けになる */
    @PostMapping
    public AnnouncementDto create(@RequestBody AnnouncementDto dto,
                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.createByAdmin(principal.getAppUser(), dto);
    }

    /** 編集。自社の投稿のみ対象(他社の投稿IDは404) */
    @PutMapping("/{id}")
    public AnnouncementDto update(@PathVariable Long id, @RequestBody AnnouncementDto dto,
                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.updateByAdmin(principal.getAppUser(), id, dto);
    }

    /** 削除。自社の投稿のみ対象 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        announcementService.deleteByAdmin(principal.getAppUser(), id);
    }

    /** 投稿一覧に既読/未読人数を軽く表示するための一覧(氏名までは含まない) */
    @GetMapping("/read-counts")
    public List<AnnouncementReadCountDto> getReadCounts(@AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.getReadCountsForAdmin(principal.getAppUser().getCompanyId());
    }

    /**
     * 既読者一覧・未読者一覧(㊳自社の投稿のみ。他社の投稿IDを指定した場合は404で、
     * 他社の社員情報は一切含まれない)。/api/admin/** はSecurityConfigでhasRole("ADMIN")に
     * 限定済みのため、ここに到達できるのは管理者のみ。
     */
    @GetMapping("/{id}/read-status")
    public AnnouncementReadStatusDto getReadStatus(@PathVariable Long id,
                                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        return announcementService.getReadStatusForAdmin(principal.getAppUser(), id);
    }
}
