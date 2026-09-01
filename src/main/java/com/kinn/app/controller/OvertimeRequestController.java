package com.kinn.app.controller;

import com.kinn.app.dto.OvertimeRequestDto;
import com.kinn.app.dto.OvertimeRequestRecentDecisionCountDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.OvertimeRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 申請者本人向け残業申請API。会社・部署・申請者は常にログインユーザー(principal)から解決し、
 * リクエストでcompanyId/departmentId/applicantUserIdを受け取ることは一切ない
 * (AnnouncementControllerと同じ設計方針)。管理者専用のAdminOvertimeRequestController
 * (/api/admin/overtime-requests)とは別。
 */
@RestController
@RequestMapping("/api/overtime-requests")
public class OvertimeRequestController {

    private final OvertimeRequestService overtimeRequestService;

    public OvertimeRequestController(OvertimeRequestService overtimeRequestService) {
        this.overtimeRequestService = overtimeRequestService;
    }

    /** 新規申請。applicantUserIdは必ずログイン中の本人固定(他人のIDを指定させない) */
    @PostMapping
    public OvertimeRequestDto create(@RequestBody OvertimeRequestDto dto,
                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        return overtimeRequestService.create(principal.getAppUser(), dto);
    }

    /** 自分の申請一覧(ステータス・対象日を確認できる。対象日の新しい順) */
    @GetMapping("/mine")
    public List<OvertimeRequestDto> getMine(@AuthenticationPrincipal AppUserPrincipal principal) {
        return overtimeRequestService.getMine(principal.getAppUser());
    }

    /** 取り下げ。PENDING状態の自分の申請のみ対象(他人の申請idを指定しても404/403) */
    @DeleteMapping("/{id}")
    public void withdraw(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        overtimeRequestService.withdraw(principal.getAppUser(), id);
    }

    /** トップページの情報チップ表示用の軽量な件数(本日中に承認/却下された自分の申請の件数) */
    @GetMapping("/mine/recent-decision-count")
    public OvertimeRequestRecentDecisionCountDto getRecentDecisionCount(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return OvertimeRequestRecentDecisionCountDto.builder()
                .recentDecisionCount(overtimeRequestService.getRecentDecisionCount(principal.getAppUser()))
                .build();
    }
}
