package com.kinn.app.controller;

import com.kinn.app.dto.OvertimeRequestDto;
import com.kinn.app.dto.OvertimeRequestPendingCountDto;
import com.kinn.app.dto.OvertimeRequestRejectDto;
import com.kinn.app.entity.OvertimeRequestStatus;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.OvertimeRequestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理者向け残業申請管理API。ログイン中の管理者と同じ会社の申請のみを対象とする
 * (他社のデータは含めない。AdminAnnouncementControllerと同じ設計方針)。
 * /api/admin/** は SecurityConfig で hasRole("ADMIN") に限定済みのため、一般ユーザーは到達できない。
 * クラスレベルの@PreAuthorizeも多層防御として付与している(SecurityConfigのjavadoc参照)。
 */
@RestController
@RequestMapping("/api/admin/overtime-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOvertimeRequestController {

    private final OvertimeRequestService overtimeRequestService;

    public AdminOvertimeRequestController(OvertimeRequestService overtimeRequestService) {
        this.overtimeRequestService = overtimeRequestService;
    }

    /**
     * 自社の申請一覧。statusを指定すればそのステータスのみ、departmentIdを指定すればその部署のみ
     * (departmentIdは必ず自社に実在する部署かをサービス側で確認してから絞り込む)。
     */
    @GetMapping
    public List<OvertimeRequestDto> getOvertimeRequests(
            @RequestParam(required = false) OvertimeRequestStatus status,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return overtimeRequestService.getForAdmin(principal.getAppUser().getCompanyId(), departmentId, status);
    }

    /** 承認。自社のPENDING申請のみ対象(他社の申請IDは404) */
    @PutMapping("/{id}/approve")
    public OvertimeRequestDto approve(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return overtimeRequestService.approve(principal.getAppUser(), id);
    }

    /** 却下。rejectReasonは必須。自社のPENDING申請のみ対象(他社の申請IDは404) */
    @PutMapping("/{id}/reject")
    public OvertimeRequestDto reject(@PathVariable Long id, @RequestBody OvertimeRequestRejectDto dto,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        return overtimeRequestService.reject(principal.getAppUser(), id, dto.getRejectReason());
    }

    /** トップページの情報チップ表示用の軽量な承認待ち件数 */
    @GetMapping("/pending-count")
    public OvertimeRequestPendingCountDto getPendingCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return OvertimeRequestPendingCountDto.builder()
                .pendingCount(overtimeRequestService.getPendingCount(principal.getAppUser().getCompanyId()))
                .build();
    }
}
