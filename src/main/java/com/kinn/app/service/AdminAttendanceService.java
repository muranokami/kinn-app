package com.kinn.app.service;

import com.kinn.app.dto.*;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.AttendanceAudit;
import com.kinn.app.entity.Company;
import com.kinn.app.entity.DayType;
import com.kinn.app.entity.Department;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.AttendanceAuditRepository;
import com.kinn.app.repository.CompanyRepository;
import com.kinn.app.repository.DepartmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * 管理者向け勤怠一覧・ダッシュボード・勤怠修正(監査ログ付き)。
 * 「休日勤務状況(管理者)」という休日時間の合計のみを表示する専用ダッシュボードは廃止済みで、
 * 通常の勤怠一覧・詳細の中で勤務区分・残業内訳(通常残業/所定休日残業/法定休日残業/総残業)を
 * 確認できるようにしている。
 *
 * 会社単位のアクセス制御: 従業員を特定するすべてのメソッドが companyId を引数に取り、
 * AppUserRepository#findByIdAndCompanyId で確認してから employeeId を解決するため、
 * URLの数値IDを書き換えても他社の勤怠には到達できない。部署による絞り込みも、
 * DepartmentService#requireOwned でその部署が同じ会社に属することを確認してから使う。
 *
 * 健康・食事データ(HealthProfile等)は一切参照しない(勤怠管理に必要な情報のみを扱う)。
 */
@Service
public class AdminAttendanceService {

    private final AttendanceService attendanceService;
    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;
    private final AttendanceAuditRepository attendanceAuditRepository;

    public AdminAttendanceService(AttendanceService attendanceService,
                                   AppUserRepository appUserRepository,
                                   CompanyRepository companyRepository,
                                   DepartmentRepository departmentRepository,
                                   DepartmentService departmentService,
                                   AttendanceAuditRepository attendanceAuditRepository) {
        this.attendanceService = attendanceService;
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.departmentService = departmentService;
        this.attendanceAuditRepository = attendanceAuditRepository;
    }

    // ------------------------------------------------------------------
    // 全社員(または部署で絞り込んだ)フラット一覧
    // ------------------------------------------------------------------

    /** @param departmentId 指定時はその部署の従業員のみ対象にする(nullなら全社員) */
    @Transactional(readOnly = true)
    public AdminAttendanceListDto getMonthlyList(Long companyId, int year, int month, Long departmentId) {
        List<AppUser> users = usersInScope(companyId, departmentId);
        Map<Long, String> departmentNames = departmentNameMap(companyId);

        List<AdminAttendanceRowDto> rows = new ArrayList<>();
        for (AppUser user : users) {
            String employeeId = user.effectiveEmployeeId();
            for (AttendanceRecordDto d : attendanceService.getMonth(employeeId, year, month).getDays()) {
                rows.add(AdminAttendanceRowDto.builder()
                        .employeeId(employeeId)
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .departmentId(user.getDepartmentId())
                        .departmentName(user.getDepartmentId() == null ? "未設定" : departmentNames.get(user.getDepartmentId()))
                        .workDate(d.getWorkDate())
                        .dayOfWeekLabel(d.getDayOfWeekLabel())
                        .dayType(d.getDayType())
                        .dayTypeLabel(d.getDayType() == null ? null : d.getDayType().getLabel())
                        .holidayName(d.getHolidayName())
                        .startTime(d.getStartTime())
                        .endTime(d.getEndTime())
                        .workMinutes(d.getWorkMinutes())
                        .overtimeMinutes(d.getOvertimeMinutes())
                        .scheduledHolidayOvertimeMinutes(d.getScheduledHolidayOvertimeMinutes())
                        .statutoryHolidayOvertimeMinutes(d.getStatutoryHolidayOvertimeMinutes())
                        .totalOvertimeMinutes(d.getTotalOvertimeMinutes())
                        .build());
            }
        }

        return AdminAttendanceListDto.builder().year(year).month(month).rows(rows).build();
    }

    // ------------------------------------------------------------------
    // 締め日を指定した任意期間での一覧・従業員別カレンダー(⑧⑨管理者画面での期間指定)。
    // 集計・日次データの組み立てはAttendanceService#getPeriodをそのまま再利用しており、
    // ここでは「会社・部署に属する従業員だけに絞り込む」役割のみを持つ(同じ計算ロジックを重複させない)。
    // ------------------------------------------------------------------

    /** @param departmentId 指定時はその部署の従業員のみ対象にする(nullなら全社員) */
    @Transactional(readOnly = true)
    public AdminAttendanceListDto getPeriodList(Long companyId, LocalDate from, LocalDate to, Long departmentId) {
        List<AppUser> users = usersInScope(companyId, departmentId);
        Map<Long, String> departmentNames = departmentNameMap(companyId);

        List<AdminAttendanceRowDto> rows = new ArrayList<>();
        for (AppUser user : users) {
            String employeeId = user.effectiveEmployeeId();
            for (AttendanceRecordDto d : attendanceService.getPeriod(employeeId, from, to).getDays()) {
                rows.add(AdminAttendanceRowDto.builder()
                        .employeeId(employeeId)
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .departmentId(user.getDepartmentId())
                        .departmentName(user.getDepartmentId() == null ? "未設定" : departmentNames.get(user.getDepartmentId()))
                        .workDate(d.getWorkDate())
                        .dayOfWeekLabel(d.getDayOfWeekLabel())
                        .dayType(d.getDayType())
                        .dayTypeLabel(d.getDayType() == null ? null : d.getDayType().getLabel())
                        .holidayName(d.getHolidayName())
                        .startTime(d.getStartTime())
                        .endTime(d.getEndTime())
                        .workMinutes(d.getWorkMinutes())
                        .overtimeMinutes(d.getOvertimeMinutes())
                        .scheduledHolidayOvertimeMinutes(d.getScheduledHolidayOvertimeMinutes())
                        .statutoryHolidayOvertimeMinutes(d.getStatutoryHolidayOvertimeMinutes())
                        .totalOvertimeMinutes(d.getTotalOvertimeMinutes())
                        .build());
            }
        }

        return AdminAttendanceListDto.builder()
                .year(from.getYear()).month(from.getMonthValue())
                .startDate(from).endDate(to)
                .rows(rows).build();
    }

    /** 従業員別の指定期間の勤怠(カレンダー表示・詳細確認用。⑩個人の勤怠集計を管理者から見る場合も同じ) */
    @Transactional(readOnly = true)
    public AttendancePeriodDto getEmployeePeriod(Long companyId, Long userId, LocalDate from, LocalDate to) {
        AppUser user = findOwned(companyId, userId);
        return attendanceService.getPeriod(user.effectiveEmployeeId(), from, to);
    }

    // ------------------------------------------------------------------
    // 従業員別の勤怠(日別・月間・カレンダー表示用)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MonthAttendanceDto getEmployeeMonth(Long companyId, Long userId, int year, int month) {
        AppUser user = findOwned(companyId, userId);
        return attendanceService.getMonth(user.effectiveEmployeeId(), year, month);
    }

    // ------------------------------------------------------------------
    // 勤怠修正(監査ログ付き)
    // ------------------------------------------------------------------

    @Transactional
    public AttendanceRecordDto editAttendance(Long companyId, Long userId, LocalDate workDate,
                                               AdminAttendanceEditRequestDto dto,
                                               String editedByEmployeeId, String editedByName) {
        AppUser user = findOwned(companyId, userId);
        String employeeId = user.effectiveEmployeeId();

        // 修正前の状態を監査ログ用に取得しておく(未登録日の場合はデフォルト解決された内容が「修正前」になる)
        AttendanceRecordDto before = attendanceService.getMonth(employeeId, workDate.getYear(), workDate.getMonthValue())
                .getDays().stream()
                .filter(d -> d.getWorkDate().equals(workDate))
                .findFirst()
                .orElse(null);

        AttendanceRecordDto toSave = AttendanceRecordDto.builder()
                .workDate(workDate)
                .dayType(dto.getDayType())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .breakMinutes(dto.getBreakMinutes())
                .lateOrEarly(dto.getLateOrEarly())
                .remarks(dto.getRemarks())
                .build();
        AttendanceRecordDto after = attendanceService.saveDay(employeeId, toSave);

        AttendanceAudit audit = AttendanceAudit.builder()
                .targetEmployeeId(employeeId)
                .workDate(workDate)
                .editedByEmployeeId(editedByEmployeeId)
                .editedByName(editedByName)
                .previousDayType(before == null ? null : before.getDayType())
                .previousStartTime(before == null ? null : before.getStartTime())
                .previousEndTime(before == null ? null : before.getEndTime())
                .previousBreakMinutes(before == null ? null : before.getBreakMinutes())
                .newDayType(after.getDayType())
                .newStartTime(after.getStartTime())
                .newEndTime(after.getEndTime())
                .newBreakMinutes(after.getBreakMinutes())
                .build();
        attendanceAuditRepository.save(audit);

        return after;
    }

    @Transactional(readOnly = true)
    public List<AttendanceAuditDto> getAuditHistory(Long companyId, Long userId) {
        AppUser user = findOwned(companyId, userId);
        return attendanceAuditRepository.findByTargetEmployeeIdOrderByEditedAtDesc(user.effectiveEmployeeId()).stream()
                .map(this::toAuditDto)
                .toList();
    }

    private AttendanceAuditDto toAuditDto(AttendanceAudit a) {
        return AttendanceAuditDto.builder()
                .workDate(a.getWorkDate())
                .editedByName(a.getEditedByName())
                .editedAt(a.getEditedAt())
                .previousDayType(a.getPreviousDayType())
                .previousStartTime(a.getPreviousStartTime())
                .previousEndTime(a.getPreviousEndTime())
                .previousBreakMinutes(a.getPreviousBreakMinutes())
                .newDayType(a.getNewDayType())
                .newStartTime(a.getNewStartTime())
                .newEndTime(a.getNewEndTime())
                .newBreakMinutes(a.getNewBreakMinutes())
                .build();
    }

    // ------------------------------------------------------------------
    // 管理者ダッシュボード(部署で絞り込むと、その部署単位の集計になる=⑤部署別集計の再利用)
    // ------------------------------------------------------------------

    /** @param departmentId 指定時はその部署のみを対象に集計する(nullなら会社全体) */
    @Transactional(readOnly = true)
    public AdminAttendanceDashboardDto getDashboard(Long companyId, Long departmentId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "会社情報が見つかりません"));
        String departmentName = null;
        if (departmentId != null) {
            departmentName = departmentService.requireOwned(companyId, departmentId).getName();
        }

        List<AppUser> users = usersInScope(companyId, departmentId);
        LocalDate today = LocalDate.now();

        int present = 0, absent = 0, holidayWork = 0, unclocked = 0;
        List<String> missingClockOut = new ArrayList<>();
        List<String> missingClockIn = new ArrayList<>();

        double totalWorkingMinutes = 0, totalOvertimeMinutes = 0;
        double regularOvertimeMinutes = 0, scheduledHolidayOvertimeMinutes = 0, statutoryHolidayOvertimeMinutes = 0;

        for (AppUser user : users) {
            String employeeId = user.effectiveEmployeeId();
            MonthAttendanceDto month = attendanceService.getMonth(employeeId, today.getYear(), today.getMonthValue());

            MonthlySummaryDto summary = month.getSummary();
            totalWorkingMinutes += (summary.getRegularHours() + summary.getScheduledHolidayWorkHours()
                    + summary.getStatutoryHolidayWorkHours()) * 60.0;
            totalOvertimeMinutes += summary.getTotalOvertimeHours() * 60.0;
            regularOvertimeMinutes += summary.getOvertimeHours() * 60.0;
            scheduledHolidayOvertimeMinutes += summary.getScheduledHolidayOvertimeHours() * 60.0;
            statutoryHolidayOvertimeMinutes += summary.getStatutoryHolidayOvertimeHours() * 60.0;

            AttendanceRecordDto todayRecord = month.getDays().stream()
                    .filter(d -> d.getWorkDate().equals(today))
                    .findFirst()
                    .orElse(null);
            if (todayRecord == null || todayRecord.getDayType() == null) continue;

            DayType type = todayRecord.getDayType();
            boolean clockedIn = todayRecord.getStartTime() != null;
            boolean clockedOut = todayRecord.getEndTime() != null;
            boolean isHolidayType = type == DayType.SCHEDULED_HOLIDAY || type == DayType.STATUTORY_HOLIDAY;

            if (clockedIn) present++;
            if (isHolidayType && clockedIn) holidayWork++;
            if (type == DayType.NORMAL && !clockedIn) {
                absent++;
                missingClockIn.add(user.getFullName());
            }
            if (clockedIn && !clockedOut) {
                unclocked++;
                missingClockOut.add(user.getFullName());
            }
        }

        return AdminAttendanceDashboardDto.builder()
                .companyName(company.getName())
                .departmentName(departmentName)
                .employeeCount(users.size())
                .presentTodayCount(present)
                .absentTodayCount(absent)
                .holidayWorkTodayCount(holidayWork)
                .unclockedCount(unclocked)
                .missingClockOutNames(missingClockOut)
                .missingClockInNames(missingClockIn)
                .monthTotalWorkingHours(round2(totalWorkingMinutes / 60.0))
                .monthTotalOvertimeHours(round2(totalOvertimeMinutes / 60.0))
                .monthRegularOvertimeHours(round2(regularOvertimeMinutes / 60.0))
                .monthScheduledHolidayOvertimeHours(round2(scheduledHolidayOvertimeMinutes / 60.0))
                .monthStatutoryHolidayOvertimeHours(round2(statutoryHolidayOvertimeMinutes / 60.0))
                .build();
    }

    // ------------------------------------------------------------------

    /** departmentId指定時は「本当に自社の部署か」を確認したうえで絞り込む */
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

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** 他社ユーザーへ到達させないための共通ルックアップ(AdminEmployeeServiceと同じ方式) */
    private AppUser findOwned(Long companyId, Long userId) {
        return appUserRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の従業員が見つかりません"));
    }
}
