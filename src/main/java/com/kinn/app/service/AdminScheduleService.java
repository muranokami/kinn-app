package com.kinn.app.service;

import com.kinn.app.dto.AdminScheduleListDto;
import com.kinn.app.dto.AdminScheduleRowDto;
import com.kinn.app.dto.ScheduleEventDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.ScheduleEvent;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.DepartmentRepository;
import com.kinn.app.repository.ScheduleEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 管理者向け「部署ごとのスケジュール」機能(①②③)。
 * 個人のCRUDロジックそのもの(登録・更新・削除・所有者チェック)は {@link ScheduleService} を
 * そのまま再利用し、ここでは「会社・部署に属する従業員だけに絞り込む」役割だけを持つ
 * (AdminAttendanceServiceと同じ設計。同じ計算・権限ロジックを重複実装しない)。
 *
 * 会社単位のアクセス制御(⑨): すべてのメソッドが companyId を引数に取り、
 * AppUserRepository#findByIdAndCompanyId / DepartmentRepository#findByIdAndCompanyId で
 * 確認してから employeeId を解決するため、URLの数値IDを書き換えても他社のスケジュールには
 * 到達できない。部署単位のアクセス制御(⑩)は DepartmentService#requireOwned が担う。
 *
 * 健康・食事データは一切参照しない(業務スケジュールに必要な情報のみを扱う)。
 */
@Service
public class AdminScheduleService {

    private static final String[] WEEKDAY_LABELS_JA = {"月", "火", "水", "木", "金", "土", "日"};

    private final ScheduleService scheduleService;
    private final ScheduleEventRepository scheduleEventRepository;
    private final AppUserRepository appUserRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;

    public AdminScheduleService(ScheduleService scheduleService,
                                 ScheduleEventRepository scheduleEventRepository,
                                 AppUserRepository appUserRepository,
                                 DepartmentRepository departmentRepository,
                                 DepartmentService departmentService) {
        this.scheduleService = scheduleService;
        this.scheduleEventRepository = scheduleEventRepository;
        this.appUserRepository = appUserRepository;
        this.departmentRepository = departmentRepository;
        this.departmentService = departmentService;
    }

    // ------------------------------------------------------------------
    // 部署ごと・会社全体のスケジュール一覧(①②)。表示期間+対象部署で絞り込んでから
    // 1回のクエリで取得する(㉒ パフォーマンス対策。社員数分クエリを繰り返さない)。
    // ------------------------------------------------------------------

    /** @param departmentId 指定時はその部署の従業員のみ対象にする(nullなら全部署=会社全体) */
    @Transactional(readOnly = true)
    public AdminScheduleListDto getMonth(Long companyId, int year, int month, Long departmentId) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<AppUser> users = usersInScope(companyId, departmentId);
        String departmentName = departmentId == null ? null : departmentService.requireOwned(companyId, departmentId).getName();

        if (users.isEmpty()) {
            return AdminScheduleListDto.builder()
                    .year(year).month(month)
                    .departmentId(departmentId).departmentName(departmentName)
                    .events(List.of())
                    .build();
        }

        Map<String, AppUser> byEmployeeId = new HashMap<>();
        for (AppUser u : users) {
            byEmployeeId.put(u.effectiveEmployeeId(), u);
        }
        Map<Long, String> departmentNames = departmentNameMap(companyId);

        List<ScheduleEvent> events = scheduleEventRepository
                .findByEmployeeIdInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(byEmployeeId.keySet(), from, to);

        List<AdminScheduleRowDto> rows = new ArrayList<>();
        for (ScheduleEvent e : events) {
            AppUser owner = byEmployeeId.get(e.getEmployeeId());
            if (owner == null) continue; // 理論上発生しないが、念のため範囲外の従業員は除外する
            rows.add(AdminScheduleRowDto.builder()
                    .id(e.getId())
                    .userId(owner.getId())
                    .fullName(owner.getFullName())
                    .departmentId(owner.getDepartmentId())
                    .departmentName(owner.getDepartmentId() == null ? "未設定" : departmentNames.get(owner.getDepartmentId()))
                    .eventDate(e.getEventDate())
                    .dayOfWeekLabel(WEEKDAY_LABELS_JA[e.getEventDate().getDayOfWeek().getValue() - 1])
                    .startTime(e.getStartTime())
                    .endTime(e.getEndTime())
                    .title(e.getTitle())
                    .category(e.getCategory())
                    .categoryLabel(e.getCategory().getLabel())
                    .memo(e.getMemo())
                    .location(e.getLocation())
                    .build());
        }

        return AdminScheduleListDto.builder()
                .year(year).month(month)
                .departmentId(departmentId).departmentName(departmentName)
                .events(rows)
                .build();
    }

    // ------------------------------------------------------------------
    // 管理者によるスケジュール登録・編集・削除(⑫⑬⑭)。
    // ScheduleServiceのCRUD(所有者チェック含む)をそのまま再利用する。
    // ------------------------------------------------------------------

    /** 特定の従業員1人に対してスケジュールを登録する */
    @Transactional
    public ScheduleEventDto createForEmployee(Long companyId, Long userId, ScheduleEventDto dto) {
        AppUser user = findOwnedUser(companyId, userId);
        return scheduleService.create(user.effectiveEmployeeId(), dto);
    }

    /** 部署に所属する全従業員に対して、同じ内容のスケジュールを一括登録する(⑬複数人のスケジュール) */
    @Transactional
    public List<ScheduleEventDto> createForDepartment(Long companyId, Long departmentId, ScheduleEventDto dto) {
        departmentService.requireOwned(companyId, departmentId);
        List<AppUser> users = appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(companyId, departmentId);
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定の部署に従業員が所属していません");
        }
        List<ScheduleEventDto> created = new ArrayList<>();
        for (AppUser user : users) {
            created.add(scheduleService.create(user.effectiveEmployeeId(), dto));
        }
        return created;
    }

    /** 従業員のスケジュールを編集する */
    @Transactional
    public ScheduleEventDto updateForEmployee(Long companyId, Long userId, Long eventId, ScheduleEventDto dto) {
        AppUser user = findOwnedUser(companyId, userId);
        return scheduleService.update(user.effectiveEmployeeId(), eventId, dto);
    }

    /** 従業員のスケジュールを削除する */
    @Transactional
    public void deleteForEmployee(Long companyId, Long userId, Long eventId) {
        AppUser user = findOwnedUser(companyId, userId);
        scheduleService.delete(user.effectiveEmployeeId(), eventId);
    }

    // ------------------------------------------------------------------

    /** departmentId指定時は「本当に自社の部署か」を確認したうえで絞り込む(⑨⑩) */
    private List<AppUser> usersInScope(Long companyId, Long departmentId) {
        if (departmentId == null) {
            return appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId);
        }
        departmentService.requireOwned(companyId, departmentId);
        return appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(companyId, departmentId);
    }

    private Map<Long, String> departmentNameMap(Long companyId) {
        Map<Long, String> map = new HashMap<>();
        for (Department d : departmentRepository.findByCompanyIdOrderByNameAsc(companyId)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    /** 他社ユーザーへ到達させないための共通ルックアップ(AdminAttendanceServiceと同じ方式) */
    private AppUser findOwnedUser(Long companyId, Long userId) {
        return appUserRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の従業員が見つかりません"));
    }
}
