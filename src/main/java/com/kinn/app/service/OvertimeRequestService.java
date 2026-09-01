package com.kinn.app.service;

import com.kinn.app.dto.OvertimeRequestDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.OvertimeRequest;
import com.kinn.app.entity.OvertimeRequestAuditAction;
import com.kinn.app.entity.OvertimeRequestStatus;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.OvertimeRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 残業申請機能のサービス。
 *
 * 申請者本人向け(新規申請・自分の一覧確認・取り下げ)と管理者向け(自社の申請一覧・承認・却下)
 * の両方をこの1つのサービスにまとめている(AnnouncementServiceと同じ設計方針。共有Entityに
 * 対する「誰が呼ぶかで許可される操作範囲が変わる」ロジックを1箇所に集約するため)。
 *
 * 会社・部署のアクセス制御(㊳非常に重要)は、すべてのメソッドで以下を徹底する:
 * <ul>
 *   <li>申請者本人経路: companyId/departmentId/applicantUserIdはリクエストから一切受け取らず、
 *       常にログインユーザー(AppUser)自身の値で解決する。</li>
 *   <li>管理者経路: companyIdは必ずログイン中の管理者自身の会社。departmentIdでの絞り込みは
 *       DepartmentService#requireOwnedで「自社に実在する部署か」を確認してから使う
 *       (他社の部署IDを指定した絞り込みはできない)。</li>
 *   <li>1件取得はfindByIdAndCompanyIdを必ず経由し、他社の申請IDを指定した場合は404にする
 *       (URL改ざん対策。AnnouncementServiceと同じ考え方)。</li>
 * </ul>
 *
 * 事前申請制であり、承認された申請は「予定」データとして保持するのみ。AttendanceRecordの実績
 * (実際の打刻から計算されるovertimeMinutes)は一切書き換えない(要件どおり。両者の食い違いは
 * それ自体を許容し、画面上で両方を並べて確認できれば十分とする)。
 */
@Service
public class OvertimeRequestService {

    /** 予定残業時間の上限(分)。1日24時間を超える申請は入力ミスとして弾く */
    private static final int MAX_PLANNED_MINUTES = 24 * 60;

    /** DB列(overtime_request.reason/reject_reason、V4マイグレーション参照)の上限文字数と一致させる */
    private static final int REASON_MAX_LENGTH = 500;

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final AppUserRepository appUserRepository;
    private final DepartmentService departmentService;
    private final OvertimeRequestAuditLogService overtimeRequestAuditLogService;

    public OvertimeRequestService(OvertimeRequestRepository overtimeRequestRepository,
                                   AppUserRepository appUserRepository,
                                   DepartmentService departmentService,
                                   OvertimeRequestAuditLogService overtimeRequestAuditLogService) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.appUserRepository = appUserRepository;
        this.departmentService = departmentService;
        this.overtimeRequestAuditLogService = overtimeRequestAuditLogService;
    }

    // ------------------------------------------------------------------
    // 申請者本人
    // ------------------------------------------------------------------

    /**
     * 新規申請。applicantUserId/companyId/departmentIdはリクエストから一切受け取らず、
     * 必ずログイン中の本人(AppUser)から解決する(他人の申請を作れないようにするため)。
     */
    @Transactional
    public OvertimeRequestDto create(AppUser applicant, OvertimeRequestDto dto) {
        validateCreate(dto);

        OvertimeRequest entity = OvertimeRequest.builder()
                .companyId(applicant.getCompanyId())
                .departmentId(applicant.getDepartmentId())
                .applicantUserId(applicant.getId())
                .targetDate(dto.getTargetDate())
                .plannedMinutes(dto.getPlannedMinutes())
                .reason(dto.getReason().trim())
                .status(OvertimeRequestStatus.PENDING)
                .build();
        OvertimeRequest saved = overtimeRequestRepository.save(entity);
        overtimeRequestAuditLogService.record(applicant, saved, OvertimeRequestAuditAction.CREATE);
        return toDtoResolved(saved);
    }

    /** 自分の申請一覧。ステータスを問わず全件、対象日の新しい順で返す(画面側でステータスを見分けられる) */
    @Transactional(readOnly = true)
    public List<OvertimeRequestDto> getMine(AppUser user) {
        List<OvertimeRequest> mine = overtimeRequestRepository
                .findByCompanyIdAndApplicantUserIdOrderByTargetDateDescCreatedAtDesc(
                        user.getCompanyId(), user.getId());
        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(user.getCompanyId());
        Map<Long, String> userNames = userNameMapForCompany(user.getCompanyId());
        return mine.stream().map(r -> toDto(r, deptNames, userNames)).toList();
    }

    /**
     * 取り下げ。PENDING状態の自分の申請のみ取り下げ可能(㊳)。
     * findByIdAndCompanyIdで他社の申請IDは404。会社は一致するが自分の申請でない場合は403
     * (他人の申請idを指定しても取り下げられない)。
     */
    @Transactional
    public void withdraw(AppUser user, Long id) {
        OvertimeRequest entity = overtimeRequestRepository.findByIdAndCompanyId(id, user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の申請が見つかりません"));
        if (!entity.getApplicantUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この申請を取り下げる権限がありません");
        }
        if (entity.getStatus() != OvertimeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "承認待ちの申請のみ取り下げできます");
        }
        overtimeRequestAuditLogService.record(user, entity, OvertimeRequestAuditAction.WITHDRAW);
        overtimeRequestRepository.delete(entity);
    }

    /**
     * トップページの情報チップ表示用の軽量な件数(申請者本人向け)。
     * 本日中に承認/却下されて動きがあった自分の申請の件数のみを返す専用COUNTクエリ
     * (一覧をまとめて取得しない)。
     */
    @Transactional(readOnly = true)
    public long getRecentDecisionCount(AppUser user) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return overtimeRequestRepository.countByCompanyIdAndApplicantUserIdAndStatusNotAndUpdatedAtAfter(
                user.getCompanyId(), user.getId(), OvertimeRequestStatus.PENDING, todayStart.minusNanos(1));
    }

    // ------------------------------------------------------------------
    // 管理者
    // ------------------------------------------------------------------

    /**
     * 自社の申請一覧。departmentId指定時は必ず自社の部署であることを確認した上で絞り込む
     * (TaskService#getForAdminと同じ考え方でDBクエリの時点で絞り込み、アプリ側フィルタ漏れを避ける)。
     * statusを指定した場合はさらにそのステータスだけに絞り込む。
     */
    @Transactional(readOnly = true)
    public List<OvertimeRequestDto> getForAdmin(Long companyId, Long departmentId, OvertimeRequestStatus status) {
        List<OvertimeRequest> scoped = loadScope(companyId, departmentId);
        List<OvertimeRequest> filtered = scoped.stream()
                .filter(r -> status == null || status == r.getStatus())
                .toList();

        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(companyId);
        Map<Long, String> userNames = userNameMapForCompany(companyId);
        return filtered.stream().map(r -> toDto(r, deptNames, userNames)).toList();
    }

    /** 承認。approverUserId・approvedAtを記録する。自社のPENDING申請のみ対象(㊳) */
    @Transactional
    public OvertimeRequestDto approve(AppUser admin, Long id) {
        OvertimeRequest entity = overtimeRequestRepository.findByIdAndCompanyId(id, admin.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の申請が見つかりません"));
        if (entity.getStatus() != OvertimeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "承認待ちの申請のみ承認できます");
        }
        entity.setStatus(OvertimeRequestStatus.APPROVED);
        entity.setApproverUserId(admin.getId());
        entity.setApprovedAt(LocalDateTime.now());
        OvertimeRequest saved = overtimeRequestRepository.save(entity);
        overtimeRequestAuditLogService.record(admin, saved, OvertimeRequestAuditAction.APPROVE);
        return toDtoResolved(saved);
    }

    /** 却下。rejectReasonは必須。自社のPENDING申請のみ対象(㊳) */
    @Transactional
    public OvertimeRequestDto reject(AppUser admin, Long id, String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "却下理由は必須です");
        }
        if (rejectReason.length() > REASON_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "却下理由は" + REASON_MAX_LENGTH + "文字以内で入力してください");
        }
        OvertimeRequest entity = overtimeRequestRepository.findByIdAndCompanyId(id, admin.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の申請が見つかりません"));
        if (entity.getStatus() != OvertimeRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "承認待ちの申請のみ却下できます");
        }
        entity.setStatus(OvertimeRequestStatus.REJECTED);
        // rejectはapprovedAtを設定しない(approvedAtは「承認された日時」専用のため)。
        // 却下操作を行った管理者の記録自体はapproverUserIdを兼用する(承認/却下いずれの決裁者かは
        // statusとあわせて判定する)。却下日時はupdatedAt(@PreUpdateで自動更新)で代替する。
        entity.setApproverUserId(admin.getId());
        entity.setRejectReason(rejectReason.trim());
        OvertimeRequest saved = overtimeRequestRepository.save(entity);
        overtimeRequestAuditLogService.record(admin, saved, OvertimeRequestAuditAction.REJECT);
        return toDtoResolved(saved);
    }

    /** トップページの情報チップ表示用の軽量な承認待ち件数(管理者向け) */
    @Transactional(readOnly = true)
    public long getPendingCount(Long companyId) {
        return overtimeRequestRepository.countByCompanyIdAndStatus(companyId, OvertimeRequestStatus.PENDING);
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /** departmentId指定時は必ず自社の部署か確認してから絞り込む(㊳会社・部署アクセス制御) */
    private List<OvertimeRequest> loadScope(Long companyId, Long departmentId) {
        if (departmentId == null) {
            return overtimeRequestRepository.findByCompanyIdOrderByTargetDateAsc(companyId);
        }
        departmentService.requireOwned(companyId, departmentId);
        return overtimeRequestRepository.findByCompanyIdAndDepartmentIdOrderByTargetDateAsc(companyId, departmentId);
    }

    /**
     * 入力チェック。対象日は必須かつ過去日不可、予定残業時間は1分〜24時間、理由は必須。
     * 理由の文字数上限もここで明示的に弾く(セキュリティレビューで指摘・追加。上限チェックが
     * 無いと、DB列(varchar)の上限を超えた入力がDataIntegrityViolationExceptionとして
     * 汎用500エラーになり、利用者に「文字数オーバー」という原因が伝わらないため)。
     */
    private void validateCreate(OvertimeRequestDto dto) {
        if (dto.getTargetDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "対象日は必須です");
        }
        if (dto.getTargetDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "対象日は本日以降の日付を指定してください");
        }
        if (dto.getPlannedMinutes() == null || dto.getPlannedMinutes() <= 0
                || dto.getPlannedMinutes() > MAX_PLANNED_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "予定残業時間の指定が正しくありません");
        }
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "理由は必須です");
        }
        if (dto.getReason().length() > REASON_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "理由は" + REASON_MAX_LENGTH + "文字以内で入力してください");
        }
    }

    private OvertimeRequestDto toDtoResolved(OvertimeRequest r) {
        String deptName = departmentService.getDepartmentNameOrNull(r.getDepartmentId());
        String applicantName = resolveUserName(r.getApplicantUserId());
        String approverName = resolveUserName(r.getApproverUserId());
        return toDto(r, deptName, applicantName, approverName);
    }

    private OvertimeRequestDto toDto(OvertimeRequest r, Map<Long, String> deptNames, Map<Long, String> userNames) {
        return toDto(r, deptNames.get(r.getDepartmentId()), userNames.get(r.getApplicantUserId()),
                userNames.get(r.getApproverUserId()));
    }

    private OvertimeRequestDto toDto(OvertimeRequest r, String departmentName, String applicantName, String approverName) {
        return OvertimeRequestDto.builder()
                .id(r.getId())
                .departmentId(r.getDepartmentId())
                // departmentIdは「申請者の所属部署」であり、Announcementのdepartmentidと違って
                // nullは「全社向け」ではなく「部署未所属」を意味する。表示用の代替文字列にする
                .departmentName(r.getDepartmentId() == null ? "未所属" : departmentName)
                .applicantUserId(r.getApplicantUserId())
                .applicantName(applicantName)
                .targetDate(r.getTargetDate())
                .plannedMinutes(r.getPlannedMinutes())
                .reason(r.getReason())
                .status(r.getStatus())
                .statusLabel(r.getStatus().getLabel())
                .approverUserId(r.getApproverUserId())
                .approverName(approverName)
                .approvedAt(r.getApprovedAt())
                .rejectReason(r.getRejectReason())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return appUserRepository.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    /** N+1回避用: 会社内の全ユーザーのID→氏名をまとめて1回で引く(AnnouncementServiceと同じ考え方) */
    private Map<Long, String> userNameMapForCompany(Long companyId) {
        Map<Long, String> map = new HashMap<>();
        for (AppUser u : appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId)) {
            map.put(u.getId(), u.getFullName());
        }
        return map;
    }
}
