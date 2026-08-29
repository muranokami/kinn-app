package com.kinn.app.service;

import com.kinn.app.dto.DepartmentScheduleEventDto;
import com.kinn.app.dto.DepartmentScheduleListDto;
import com.kinn.app.dto.ScheduleFeedEventDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.ScheduleCategory;
import com.kinn.app.entity.ScheduleEvent;
import com.kinn.app.entity.ScheduleType;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.ScheduleEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部署共有スケジュール(②③⑥⑦⑯⑰⑱⑳)を扱うサービス。
 *
 * 個人スケジュール(ScheduleService)とは責務を分離しつつ、Entityは既存のScheduleEventを
 * そのまま共用する(⑮)。可視性の判定は必ず company_id + department_id + schedule_type=DEPARTMENT
 * の組み合わせで行い、他社・他部署のデータへは(URLの数値IDを書き換えても)到達できない
 * (⑨⑩⑪。ScheduleEventRepositoryのJavadoc参照)。
 *
 * 「個人」「部署」「すべて」を切り替えるカレンダー(④⑬)向けに、ScheduleServiceの月間取得を
 * 再利用してPERSONAL/DEPARTMENTを1つのフィードへ合成するメソッドも提供する。
 */
@Service
public class DepartmentScheduleService {

    private static final String[] WEEKDAY_LABELS_JA = {"月", "火", "水", "木", "金", "土", "日"};

    private final ScheduleEventRepository scheduleEventRepository;
    private final DepartmentService departmentService;
    private final ScheduleService scheduleService;
    private final AppUserRepository appUserRepository;

    public DepartmentScheduleService(ScheduleEventRepository scheduleEventRepository,
                                      DepartmentService departmentService,
                                      ScheduleService scheduleService,
                                      AppUserRepository appUserRepository) {
        this.scheduleEventRepository = scheduleEventRepository;
        this.departmentService = departmentService;
        this.scheduleService = scheduleService;
        this.appUserRepository = appUserRepository;
    }

    // ------------------------------------------------------------------
    // 月間一覧(⑥⑱)
    // ------------------------------------------------------------------

    /**
     * 一般ユーザー・管理者共通: ログイン中ユーザー自身が所属する部署の共有スケジュールを
     * 自動的に解決して取得する(⑨㉒。departmentIdをクライアントから受け取らないことで
     * URL改ざんによる他部署閲覧の余地自体をなくす)。部署未所属の場合は空一覧を返す。
     */
    @Transactional(readOnly = true)
    public DepartmentScheduleListDto getMonthForUser(AppUser user, int year, int month) {
        if (user.getDepartmentId() == null) {
            return DepartmentScheduleListDto.builder()
                    .year(year).month(month)
                    .departmentId(null).departmentName(null)
                    .events(List.of())
                    .build();
        }
        return getMonthInternal(user.getCompanyId(), user.getDepartmentId(), year, month);
    }

    /** 管理者: 明示的に指定した部署の共有スケジュールを取得する(自社の部署かは事前に確認する)(㉑) */
    @Transactional(readOnly = true)
    public DepartmentScheduleListDto getMonthForAdmin(Long companyId, Long departmentId, int year, int month) {
        departmentService.requireOwned(companyId, departmentId);
        return getMonthInternal(companyId, departmentId, year, month);
    }

    /**
     * 管理者: 部署を指定せず、自社の部署共有スケジュールを全部署まとめて取得する。
     * 一般ユーザーが登録した内容(⑥で追加した機能)も、管理者が部署ごとに見て回らなくても
     * 全部署横断で一度に確認できるようにする。行ごとに部署が異なりうるため、
     * departmentNameは1件ずつ解決する(getMonthInternalのような単一部署前提の共有ロジックは
     * 使わない)。
     */
    @Transactional(readOnly = true)
    public DepartmentScheduleListDto getMonthForAdminAllDepartments(Long companyId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<ScheduleEvent> events = scheduleEventRepository
                .findByCompanyIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        companyId, ScheduleType.DEPARTMENT, from, to);

        Map<Long, String> departmentNames = departmentService.getDepartmentNameMap(companyId);
        Map<Long, String> creatorNames = creatorNameMap(events);
        List<DepartmentScheduleEventDto> rows = events.stream()
                .map(e -> toRowDto(e, departmentNames.get(e.getDepartmentId()), creatorNames.get(e.getCreatedByUserId())))
                .toList();

        return DepartmentScheduleListDto.builder()
                .year(year).month(month)
                .departmentId(null).departmentName(null) // 全部署横断のため単一の部署IDではない
                .events(rows)
                .build();
    }

    private DepartmentScheduleListDto getMonthInternal(Long companyId, Long departmentId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        String departmentName = departmentService.getDepartmentNameOrNull(departmentId);

        List<ScheduleEvent> events = scheduleEventRepository
                .findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        companyId, departmentId, ScheduleType.DEPARTMENT, from, to);

        Map<Long, String> creatorNames = creatorNameMap(events);
        List<DepartmentScheduleEventDto> rows = events.stream()
                .map(e -> toRowDto(e, departmentName, creatorNames.get(e.getCreatedByUserId())))
                .toList();

        return DepartmentScheduleListDto.builder()
                .year(year).month(month)
                .departmentId(departmentId).departmentName(departmentName)
                .events(rows)
                .build();
    }

    // ------------------------------------------------------------------
    // 登録・編集・削除(⑦⑯⑰)。管理者のみ(AdminScheduleControllerがROLE_ADMIN限定)。
    // ------------------------------------------------------------------

    @Transactional
    public DepartmentScheduleEventDto create(Long companyId, Long departmentId, Long createdByUserId,
                                              DepartmentScheduleEventDto dto) {
        Department department = departmentService.requireOwned(companyId, departmentId);
        validate(dto);

        ScheduleEvent entity = ScheduleEvent.builder()
                .employeeId(companyId + "|admin:" + createdByUserId) // NOT NULL制約を満たすための監査用値。可視性判定には使わない
                .scheduleType(ScheduleType.DEPARTMENT)
                .companyId(companyId)
                .departmentId(departmentId)
                .createdByUserId(createdByUserId)
                .eventDate(dto.getEventDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .title(dto.getTitle())
                .category(dto.getCategory() == null ? ScheduleCategory.OTHER : dto.getCategory())
                .memo(dto.getContent())
                .location(dto.getLocation())
                .build();

        ScheduleEvent saved = scheduleEventRepository.save(entity);
        return toRowDto(saved, department.getName());
    }

    @Transactional
    public DepartmentScheduleEventDto update(Long companyId, Long departmentId, Long eventId,
                                              DepartmentScheduleEventDto dto) {
        Department department = departmentService.requireOwned(companyId, departmentId);
        validate(dto);
        ScheduleEvent entity = findOwnedSharedEvent(companyId, departmentId, eventId);

        entity.setEventDate(dto.getEventDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setTitle(dto.getTitle());
        entity.setCategory(dto.getCategory() == null ? ScheduleCategory.OTHER : dto.getCategory());
        entity.setMemo(dto.getContent());
        entity.setLocation(dto.getLocation());

        return toRowDto(scheduleEventRepository.save(entity), department.getName());
    }

    @Transactional
    public void delete(Long companyId, Long departmentId, Long eventId) {
        departmentService.requireOwned(companyId, departmentId);
        ScheduleEvent entity = findOwnedSharedEvent(companyId, departmentId, eventId);
        scheduleEventRepository.delete(entity);
    }

    private ScheduleEvent findOwnedSharedEvent(Long companyId, Long departmentId, Long eventId) {
        return scheduleEventRepository
                .findByIdAndCompanyIdAndDepartmentIdAndScheduleType(eventId, companyId, departmentId, ScheduleType.DEPARTMENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の部署共有スケジュールが見つかりません"));
    }

    // ------------------------------------------------------------------
    // 一般ユーザー自身による部署共有スケジュールの登録・編集・削除(⑥⑭⑮⑯)。
    // 上のcreate/update/delete(管理者専用。AdminScheduleControllerがROLE_ADMIN限定)とは別に、
    // ScheduleController(認証されていれば誰でも呼べる)から使う。部署は必ずログインユーザー
    // 自身から自動解決し、リクエストからdepartmentIdを受け取らない(⑤⑫⑱)。
    // ------------------------------------------------------------------

    /** 部署共有スケジュールを登録する。会社・部署・登録者はすべてログインユーザーから取得する(⑤⑥⑫) */
    @Transactional
    public DepartmentScheduleEventDto createForUser(AppUser user, DepartmentScheduleEventDto dto) {
        if (user.getDepartmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "部署に所属していないため、部署共有スケジュールを登録できません。");
        }
        validate(dto);
        Department department = departmentService.requireOwned(user.getCompanyId(), user.getDepartmentId());

        ScheduleEvent entity = ScheduleEvent.builder()
                // NOT NULL制約を満たすための監査用値。可視性判定には使わない(実際の可視性は
                // company_id + department_id + schedule_type のみで決まる。ScheduleEventのJavadoc参照)
                .employeeId(user.getCompanyId() + "|shared:" + user.getId())
                .scheduleType(ScheduleType.DEPARTMENT)
                .companyId(user.getCompanyId())
                .departmentId(user.getDepartmentId())
                .createdByUserId(user.getId())
                .eventDate(dto.getEventDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .title(dto.getTitle())
                .category(dto.getCategory() == null ? ScheduleCategory.OTHER : dto.getCategory())
                .memo(dto.getContent())
                .location(dto.getLocation())
                .build();

        ScheduleEvent saved = scheduleEventRepository.save(entity);
        return toRowDto(saved, department.getName(), user.getFullName());
    }

    /**
     * 自分が登録した部署共有スケジュールを編集する(⑭)。管理者は他人の登録分も編集できる(⑯)。
     * departmentIdでは絞り込まない(findByIdAndCompanyIdAndScheduleTypeのJavadoc参照。
     * 部署異動後も自分の登録分を編集できるようにするため)。companyIdの一致は必須(⑱)。
     */
    @Transactional
    public DepartmentScheduleEventDto updateOwn(AppUser user, Long eventId, DepartmentScheduleEventDto dto) {
        ScheduleEvent entity = findOwnedByCreatorOrAdmin(user, eventId, "編集");
        validate(dto);

        entity.setEventDate(dto.getEventDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setTitle(dto.getTitle());
        entity.setCategory(dto.getCategory() == null ? ScheduleCategory.OTHER : dto.getCategory());
        entity.setMemo(dto.getContent());
        entity.setLocation(dto.getLocation());

        String departmentName = departmentService.getDepartmentNameOrNull(entity.getDepartmentId());
        return toRowDto(scheduleEventRepository.save(entity), departmentName);
    }

    /** 自分が登録した部署共有スケジュールを削除する(⑮)。管理者は他人の登録分も削除できる(⑯) */
    @Transactional
    public void deleteOwn(AppUser user, Long eventId) {
        ScheduleEvent entity = findOwnedByCreatorOrAdmin(user, eventId, "削除");
        scheduleEventRepository.delete(entity);
    }

    /**
     * 所有者チェック(⑭⑮⑯⑰。非常に重要: 画面側だけでなく必ずここで確認する)。
     * 「同じ会社の部署共有スケジュールであること」(⑱)と「自分が登録した本人、または管理者
     * であること」(⑭⑯)の両方を満たさない限り編集・削除させない。
     */
    private ScheduleEvent findOwnedByCreatorOrAdmin(AppUser user, Long eventId, String action) {
        ScheduleEvent entity = scheduleEventRepository
                .findByIdAndCompanyIdAndScheduleType(eventId, user.getCompanyId(), ScheduleType.DEPARTMENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "この部署共有スケジュールは見つかりません。既に削除されている可能性があります。"));
        boolean isCreator = entity.getCreatedByUserId() != null && entity.getCreatedByUserId().equals(user.getId());
        boolean isAdmin = user.getRole() == com.kinn.app.entity.UserRole.ADMIN;
        if (!isCreator && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "この部署共有スケジュールを" + action + "する権限がありません。登録した本人または管理者のみ操作できます。");
        }
        return entity;
    }

    private void validate(DepartmentScheduleEventDto dto) {
        if (dto.getEventDate() == null || dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日付とタイトルは必須です");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null && dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "終了時間は開始時間より前にできません");
        }
    }

    // ------------------------------------------------------------------
    // 「個人」「部署」「すべて」切り替えカレンダー用フィード(④⑫⑬)
    // ------------------------------------------------------------------

    /** 自分の個人スケジュールをフィード形式で返す(既存ScheduleServiceの月間取得をそのまま再利用) */
    @Transactional(readOnly = true)
    public List<ScheduleFeedEventDto> getPersonalFeed(String employeeId, int year, int month) {
        return scheduleService.getMonth(employeeId, year, month).getEvents().stream()
                .map(e -> ScheduleFeedEventDto.builder()
                        .id(e.getId())
                        .scheduleType(ScheduleType.PERSONAL)
                        .eventDate(e.getEventDate())
                        .startTime(e.getStartTime())
                        .endTime(e.getEndTime())
                        .title(e.getTitle())
                        .content(e.getMemo())
                        .location(e.getLocation())
                        .category(e.getCategory())
                        .categoryLabel(e.getCategory().getLabel())
                        .departmentName(null)
                        .build())
                .toList();
    }

    /** 自分の部署の共有スケジュールをフィード形式で返す */
    @Transactional(readOnly = true)
    public List<ScheduleFeedEventDto> getDepartmentFeed(AppUser user, int year, int month) {
        return getMonthForUser(user, year, month).getEvents().stream()
                .map(e -> ScheduleFeedEventDto.builder()
                        .id(e.getId())
                        .scheduleType(ScheduleType.DEPARTMENT)
                        .eventDate(e.getEventDate())
                        .startTime(e.getStartTime())
                        .endTime(e.getEndTime())
                        .title(e.getTitle())
                        .content(e.getContent())
                        .location(e.getLocation())
                        .category(e.getCategory())
                        .categoryLabel(e.getCategoryLabel())
                        .departmentName(e.getDepartmentName())
                        .createdByUserId(e.getCreatedByUserId())
                        .createdByName(e.getCreatedByName())
                        .build())
                .toList();
    }

    /** 個人+部署共有を合わせて、日付→開始時刻の順に並べたフィード(⑬「すべて」表示) */
    @Transactional(readOnly = true)
    public List<ScheduleFeedEventDto> getAllFeed(AppUser user, String employeeId, int year, int month) {
        List<ScheduleFeedEventDto> merged = new ArrayList<>();
        merged.addAll(getPersonalFeed(employeeId, year, month));
        merged.addAll(getDepartmentFeed(user, year, month));
        merged.sort(Comparator
                .comparing(ScheduleFeedEventDto::getEventDate)
                .thenComparing(ev -> ev.getStartTime() == null ? java.time.LocalTime.MIN : ev.getStartTime()));
        return merged;
    }

    private DepartmentScheduleEventDto toRowDto(ScheduleEvent e, String departmentName) {
        return toRowDto(e, departmentName, resolveCreatorName(e.getCreatedByUserId()));
    }

    private DepartmentScheduleEventDto toRowDto(ScheduleEvent e, String departmentName, String createdByName) {
        return DepartmentScheduleEventDto.builder()
                .id(e.getId())
                .departmentId(e.getDepartmentId())
                .departmentName(departmentName)
                .eventDate(e.getEventDate())
                .dayOfWeekLabel(WEEKDAY_LABELS_JA[e.getEventDate().getDayOfWeek().getValue() - 1])
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .title(e.getTitle())
                .content(e.getMemo())
                .location(e.getLocation())
                .category(e.getCategory())
                .categoryLabel(e.getCategory().getLabel())
                .createdByUserId(e.getCreatedByUserId())
                .createdByName(createdByName)
                .build();
    }

    /**
     * 一覧取得用: N+1を避けるため、登場する登録者IDをまとめて1回で引く(㉑パフォーマンス対策)。
     * 戻り値は必ずHashMapにする(Map.of()は不変かつnullキーを許さないため、createdByUserIdが
     * nullなイベント[理論上発生しないが念のため]に対して.get(null)するとNPEになってしまう)。
     */
    private Map<Long, String> creatorNameMap(List<ScheduleEvent> events) {
        Set<Long> ids = events.stream()
                .map(ScheduleEvent::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        for (AppUser u : appUserRepository.findAllById(ids)) {
            map.put(u.getId(), u.getFullName());
        }
        return map;
    }

    /** 単発の登録・更新レスポンス用(1件だけなのでN+1にはならない) */
    private String resolveCreatorName(Long createdByUserId) {
        if (createdByUserId == null) return null;
        return appUserRepository.findById(createdByUserId).map(AppUser::getFullName).orElse(null);
    }
}
