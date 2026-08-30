package com.kinn.app.service;

import com.kinn.app.dto.AnnouncementDto;
import com.kinn.app.dto.AnnouncementReadCountDto;
import com.kinn.app.dto.AnnouncementReadStatusDto;
import com.kinn.app.dto.AnnouncementReadUserDto;
import com.kinn.app.dto.AnnouncementUnreadUserDto;
import com.kinn.app.entity.Announcement;
import com.kinn.app.entity.AnnouncementAuditAction;
import com.kinn.app.entity.AnnouncementImportance;
import com.kinn.app.entity.AnnouncementRead;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.repository.AnnouncementReadRepository;
import com.kinn.app.repository.AnnouncementRepository;
import com.kinn.app.repository.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * お知らせ機能のサービス。
 *
 * 一般ユーザー向け(自分が閲覧対象のお知らせの一覧・既読化・未読件数)と管理者向け
 * (自社のお知らせの投稿・編集・削除)の両方をこの1つのサービスにまとめている
 * (TaskServiceと同じ設計方針。共有Entityに対する「誰が呼ぶかで許可される操作範囲が
 * 変わる」ロジックを1箇所に集約するため)。
 *
 * 会社・部署のアクセス制御(㊳非常に重要)は、すべてのメソッドで以下を徹底する:
 * <ul>
 *   <li>一般ユーザー経路: companyId/departmentIdはリクエストから一切受け取らず、
 *       常にログインユーザー(AppUser)自身の値で絞り込む。</li>
 *   <li>管理者経路: companyIdは必ずログイン中の管理者自身の会社。departmentIdはリクエストの
 *       値を使うが、DepartmentService#requireOwnedで「自社に実在する部署か」を確認してから
 *       保存する(他社の部署IDを指定した投稿はできない)。</li>
 * </ul>
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final AppUserRepository appUserRepository;
    private final DepartmentService departmentService;
    private final AnnouncementAuditLogService announcementAuditLogService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                                AnnouncementReadRepository announcementReadRepository,
                                AppUserRepository appUserRepository,
                                DepartmentService departmentService,
                                AnnouncementAuditLogService announcementAuditLogService) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.appUserRepository = appUserRepository;
        this.departmentService = departmentService;
        this.announcementAuditLogService = announcementAuditLogService;
    }

    // ------------------------------------------------------------------
    // 一般ユーザー
    // ------------------------------------------------------------------

    /**
     * ログイン中のユーザーが閲覧対象(全社向け、または自分のdepartmentId宛)のお知らせのうち、
     * 公開済み・表示終了していないものを、重要度→投稿日時の新しい順に返す。各件に自分の
     * 既読状態を含める。
     */
    @Transactional(readOnly = true)
    public List<AnnouncementDto> getForUser(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        List<Announcement> visible = announcementRepository.findVisibleForUser(
                user.getCompanyId(), user.getDepartmentId(), now);

        Set<Long> readIds = new HashSet<>();
        for (AnnouncementRead r : announcementReadRepository.findByAppUserId(user.getId())) {
            readIds.add(r.getAnnouncementId());
        }
        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(user.getCompanyId());
        Map<Long, String> userNames = userNameMapForCompany(user.getCompanyId());

        return visible.stream()
                .sorted(Comparator
                        // IMPORTANTを先頭に(importanceは@Enumerated(STRING)のため文字列順では
                        // 意図した並びにならず、Java側でCASE分けして比較する)
                        .comparing((Announcement a) -> a.getImportance() == AnnouncementImportance.IMPORTANT ? 0 : 1)
                        .thenComparing(Announcement::getPublishedAt, Comparator.reverseOrder()))
                .map(a -> toDto(a, deptNames.get(a.getDepartmentId()), userNames.get(a.getCreatedByUserId()),
                        readIds.contains(a.getId())))
                .toList();
    }

    /**
     * 既読にする(冪等)。既に既読なら何もしない。会社が違う・自分の閲覧対象外(他部署宛)の
     * お知らせは既読登録できない(㊳)。
     */
    @Transactional
    public void markRead(AppUser user, Long announcementId) {
        Announcement announcement = announcementRepository.findByIdAndCompanyId(announcementId, user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のお知らせが見つかりません"));
        boolean visible = announcement.getDepartmentId() == null
                || announcement.getDepartmentId().equals(user.getDepartmentId());
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このお知らせを表示する権限がありません");
        }
        if (announcementReadRepository.existsByAnnouncementIdAndAppUserId(announcementId, user.getId())) {
            return; // 既に既読
        }
        try {
            announcementReadRepository.save(AnnouncementRead.builder()
                    .announcementId(announcementId)
                    .appUserId(user.getId())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 連打・複数タブ同時操作等でユニーク制約(announcement_id, app_user_id)に競合した場合も
            // 「既に既読」と同じ扱いにする(冪等性を保つ)
        }
    }

    /**
     * トップページの情報チップ表示用の軽量な未読件数(一覧をまとめて取得しない専用COUNTクエリ)。
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(AppUser user) {
        return announcementRepository.countUnreadForUser(
                user.getCompanyId(), user.getDepartmentId(), user.getId(), LocalDateTime.now());
    }

    // ------------------------------------------------------------------
    // 管理者
    // ------------------------------------------------------------------

    /** 自社の投稿一覧(投稿管理用)。公開日時・表示終了日時に関わらず全件を新しい順で返す */
    @Transactional(readOnly = true)
    public List<AnnouncementDto> getForAdmin(Long companyId) {
        List<Announcement> all = announcementRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(companyId);
        Map<Long, String> userNames = userNameMapForCompany(companyId);
        return all.stream()
                .map(a -> toDto(a, deptNames.get(a.getDepartmentId()), userNames.get(a.getCreatedByUserId()), false))
                .toList();
    }

    /** 新規投稿。departmentIdを指定した場合は必ず自社の部署であることを確認する(他社への投稿を防ぐ) */
    @Transactional
    public AnnouncementDto createByAdmin(AppUser admin, AnnouncementDto dto) {
        validateCommon(dto);
        Department department = resolveDepartmentOrNull(admin.getCompanyId(), dto.getDepartmentId());

        Announcement entity = Announcement.builder()
                .companyId(admin.getCompanyId())
                .departmentId(department == null ? null : department.getId())
                .title(dto.getTitle().trim())
                .body(dto.getBody())
                .importance(dto.getImportance() == null ? AnnouncementImportance.NORMAL : dto.getImportance())
                .createdByUserId(admin.getId())
                .publishedAt(dto.getPublishedAt() == null ? LocalDateTime.now() : dto.getPublishedAt())
                .expiresAt(dto.getExpiresAt())
                .build();
        Announcement saved = announcementRepository.save(entity);
        announcementAuditLogService.record(admin, saved, AnnouncementAuditAction.CREATE);
        return toDtoResolved(saved);
    }

    /** 編集。自社の投稿のみ対象(他社の投稿IDは404。㊳)。departmentIdの変更も自社の部署に限る */
    @Transactional
    public AnnouncementDto updateByAdmin(AppUser admin, Long id, AnnouncementDto dto) {
        Announcement entity = announcementRepository.findByIdAndCompanyId(id, admin.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のお知らせが見つかりません"));
        validateCommon(dto);
        Department department = resolveDepartmentOrNull(admin.getCompanyId(), dto.getDepartmentId());

        entity.setDepartmentId(department == null ? null : department.getId());
        entity.setTitle(dto.getTitle().trim());
        entity.setBody(dto.getBody());
        if (dto.getImportance() != null) entity.setImportance(dto.getImportance());
        entity.setPublishedAt(dto.getPublishedAt() == null ? entity.getPublishedAt() : dto.getPublishedAt());
        entity.setExpiresAt(dto.getExpiresAt());

        Announcement saved = announcementRepository.save(entity);
        announcementAuditLogService.record(admin, saved, AnnouncementAuditAction.UPDATE);
        return toDtoResolved(saved);
    }

    /** 削除。自社の投稿のみ対象(㊳) */
    @Transactional
    public void deleteByAdmin(AppUser admin, Long id) {
        Announcement entity = announcementRepository.findByIdAndCompanyId(id, admin.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のお知らせが見つかりません"));
        announcementAuditLogService.record(admin, entity, AnnouncementAuditAction.DELETE);
        announcementRepository.delete(entity);
        // announcement_read側の既読レコードは外部キー制約を張っていないため削除後も残るが、
        // 参照元のお知らせが無くなればgetForUser/countUnreadForUser双方が対象外になるため
        // 表示・集計への実害はない(孤立レコードのクリーンアップは行わない。TaskのdeleteByAdmin等
        // 既存の削除処理と同じ方針)。
    }

    // ------------------------------------------------------------------
    // 管理者向け: 既読状況
    // ------------------------------------------------------------------

    /**
     * 投稿一覧に既読/未読人数を軽く表示するための一覧API(例:「既読 8/12人」)。
     * お知らせ1件ごとに、findVisibleForUserと同じ可視条件(㊳)で対象社員数を数え、
     * そのうち何人が既読済みかを返す(氏名の一覧は含めない。詳細はgetReadStatusForAdmin参照)。
     */
    @Transactional(readOnly = true)
    public List<AnnouncementReadCountDto> getReadCountsForAdmin(Long companyId) {
        List<Announcement> all = announcementRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        long companyTotal = appUserRepository.countByCompanyId(companyId);
        Map<Long, Long> deptTotalCache = new HashMap<>();

        List<AnnouncementReadCountDto> result = new ArrayList<>();
        for (Announcement a : all) {
            long total = a.getDepartmentId() == null
                    ? companyTotal
                    : deptTotalCache.computeIfAbsent(a.getDepartmentId(),
                            deptId -> appUserRepository.countByCompanyIdAndDepartmentId(companyId, deptId));
            long readCount = announcementReadRepository.countScopedReadsForAnnouncement(
                    a.getId(), companyId, a.getDepartmentId());
            result.add(AnnouncementReadCountDto.builder()
                    .announcementId(a.getId())
                    .totalCount((int) total)
                    .readCount((int) readCount)
                    .unreadCount((int) (total - readCount))
                    .build());
        }
        return result;
    }

    /**
     * 既読者一覧・未読者一覧(管理者向け)。自社の投稿のみ対象(㊳findByIdAndCompanyIdで、
     * 他社の投稿IDを指定した場合は404。他社の社員情報が一切含まれない)。
     *
     * 対象社員の算出はfindVisibleForUserと完全に同じ条件(companyId一致 かつ
     * (departmentIdがnull=全社向け または一致))で行う(resolveTargetUsers参照。
     * 一般ユーザー向け表示と対象範囲が食い違わないようにするため)。
     */
    @Transactional(readOnly = true)
    public AnnouncementReadStatusDto getReadStatusForAdmin(AppUser admin, Long id) {
        Announcement announcement = announcementRepository.findByIdAndCompanyId(id, admin.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のお知らせが見つかりません"));

        List<AppUser> targetUsers = resolveTargetUsers(admin.getCompanyId(), announcement.getDepartmentId());

        Map<Long, LocalDateTime> readAtByUserId = new HashMap<>();
        for (AnnouncementRead r : announcementReadRepository.findByAnnouncementId(id)) {
            readAtByUserId.put(r.getAppUserId(), r.getReadAt());
        }

        List<AnnouncementReadUserDto> readUsers = new ArrayList<>();
        List<AnnouncementUnreadUserDto> unreadUsers = new ArrayList<>();
        for (AppUser u : targetUsers) {
            LocalDateTime readAt = readAtByUserId.get(u.getId());
            if (readAt != null) {
                readUsers.add(AnnouncementReadUserDto.builder()
                        .userId(u.getId())
                        .fullName(u.getFullName())
                        .readAt(readAt)
                        .build());
            } else {
                unreadUsers.add(AnnouncementUnreadUserDto.builder()
                        .userId(u.getId())
                        .fullName(u.getFullName())
                        .build());
            }
        }

        String departmentName = announcement.getDepartmentId() == null
                ? "全社"
                : departmentService.getDepartmentNameOrNull(announcement.getDepartmentId());

        return AnnouncementReadStatusDto.builder()
                .announcementId(announcement.getId())
                .title(announcement.getTitle())
                .departmentId(announcement.getDepartmentId())
                .departmentName(departmentName)
                .totalCount(targetUsers.size())
                .readCount(readUsers.size())
                .unreadCount(unreadUsers.size())
                .readUsers(readUsers)
                .unreadUsers(unreadUsers)
                .build();
    }

    /**
     * お知らせの配信対象社員を算出する(㊳非常に重要: AnnouncementRepository#findVisibleForUserの
     * 可視条件と完全に一致させること)。departmentIdがnullなら全社員、指定されていればその部署の
     * 社員のみを、findByCompanyIdAndDepartmentIdOrderByFullNameAscでDBクエリの時点で絞り込む
     * (全社員を取得してからアプリ側で部署フィルタする実装は、フィルタ漏れの余地が生まれるため避ける)。
     * 氏名順で返すため、既読者一覧・未読者一覧の表示順もそのまま氏名順になる。
     */
    private List<AppUser> resolveTargetUsers(Long companyId, Long departmentId) {
        return departmentId == null
                ? appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId)
                : appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(companyId, departmentId);
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /** departmentId指定時は「本当に自社の部署か」を確認する(㊳会社・部署アクセス制御)。nullなら全社向け */
    private Department resolveDepartmentOrNull(Long companyId, Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentService.requireOwned(companyId, departmentId);
    }

    /** DB列(announcement.title/body、V3マイグレーション参照)の上限文字数と一致させる */
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int BODY_MAX_LENGTH = 4000;

    /**
     * 入力チェック。タイトル・本文は必須、表示終了日時は公開日時より前にできない。
     * 文字数上限もここで明示的に弾く(セキュリティレビューで指摘・追加。上限チェックが
     * 無いと、DB列(varchar)の上限を超えた入力がDataIntegrityViolationExceptionとして
     * 汎用500エラーになり、利用者に「文字数オーバー」という原因が伝わらないため)。
     */
    private void validateCommon(AnnouncementDto dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "タイトルは必須です");
        }
        if (dto.getTitle().length() > TITLE_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "タイトルは" + TITLE_MAX_LENGTH + "文字以内で入力してください");
        }
        if (dto.getBody() == null || dto.getBody().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本文は必須です");
        }
        if (dto.getBody().length() > BODY_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "本文は" + BODY_MAX_LENGTH + "文字以内で入力してください");
        }
        if (dto.getPublishedAt() != null && dto.getExpiresAt() != null
                && dto.getExpiresAt().isBefore(dto.getPublishedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表示終了日時は公開日時より前にできません");
        }
    }

    private AnnouncementDto toDtoResolved(Announcement a) {
        String deptName = departmentService.getDepartmentNameOrNull(a.getDepartmentId());
        String createdByName = resolveUserName(a.getCreatedByUserId());
        return toDto(a, deptName, createdByName, false);
    }

    private AnnouncementDto toDto(Announcement a, String departmentName, String createdByName, boolean read) {
        return AnnouncementDto.builder()
                .id(a.getId())
                .departmentId(a.getDepartmentId())
                .departmentName(a.getDepartmentId() == null ? "全社" : departmentName)
                .title(a.getTitle())
                .body(a.getBody())
                .importance(a.getImportance())
                .importanceLabel(a.getImportance().getLabel())
                .createdByUserId(a.getCreatedByUserId())
                .createdByName(createdByName)
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .publishedAt(a.getPublishedAt())
                .expiresAt(a.getExpiresAt())
                .read(read)
                .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return appUserRepository.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    /** N+1回避用: 会社内の全ユーザーのID→氏名をまとめて1回で引く(TaskServiceと同じ考え方) */
    private Map<Long, String> userNameMapForCompany(Long companyId) {
        Map<Long, String> map = new HashMap<>();
        for (AppUser u : appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId)) {
            map.put(u.getId(), u.getFullName());
        }
        return map;
    }
}
