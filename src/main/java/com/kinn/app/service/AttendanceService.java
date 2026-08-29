package com.kinn.app.service;

import com.kinn.app.dto.AttendanceDailyStatDto;
import com.kinn.app.dto.AttendancePeriodDto;
import com.kinn.app.dto.AttendanceRangeStatsDto;
import com.kinn.app.dto.AttendanceRecordDto;
import com.kinn.app.dto.MonthAttendanceDto;
import com.kinn.app.dto.MonthlySummaryDto;
import com.kinn.app.entity.AttendanceRecord;
import com.kinn.app.entity.DayType;
import com.kinn.app.repository.AttendanceRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 勤怠の集計ロジックを一元管理するサービス。
 *
 * 前提(会社カレンダーに合わせて変更可能):
 *  - 所定労働時間: 09:00〜18:00 (休憩を除き実働8時間 = 480分)
 *  - 深夜帯: 22:00〜翌5:00
 *  - 休日区分のデフォルト: 土曜日=所定休日, 日曜日=法定休日(固定。会社ごとの変更UIは廃止済み)
 *  - 日本の祝日は {@link HolidayCalendarService} で判定し、通常勤務日に重なる場合のみ
 *    所定休日として扱う(祝日を自動的に法定休日にはしない。既に法定休日/所定休日の
 *    曜日ルールに当たる日はそのまま=祝日情報は表示用に保持するだけで区分は変えない)。
 *
 * 残業時間は「通常残業(通常勤務日)」「所定休日残業」「法定休日残業」の3種類に分けて
 * 計算する。いずれも既存の所定労働時間(8時間=480分)を基準に、実働のうちこれを
 * 超えた部分を残業として扱う、という同じ考え方で計算しており、休日区分によって
 * 矛盾する計算ルールを新設してはいない。
 *
 * 集計値(実働・残業・深夜など)はAttendanceRecordには保存せず、常にこのクラスで
 * 都度計算する、という既存の設計方針を踏襲している(entity側のJavadoc参照)。
 */
@Service
public class AttendanceService {

    private static final int STANDARD_WORK_MINUTES = 8 * 60; // 8時間
    private static final int NIGHT_START_MIN = 22 * 60;       // 22:00
    private static final int NIGHT_END_MIN = 29 * 60;         // 翌5:00 (24:00 + 5:00)

    private final AttendanceRecordRepository repository;
    private final HolidayCalendarService holidayCalendarService;

    private static final String[] WEEKDAY_LABELS_JA = {"月", "火", "水", "木", "金", "土", "日"};

    public AttendanceService(AttendanceRecordRepository repository,
                              HolidayCalendarService holidayCalendarService) {
        this.repository = repository;
        this.holidayCalendarService = holidayCalendarService;
    }

    @Transactional(readOnly = true)
    public MonthAttendanceDto getMonth(String employeeId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<AttendanceRecord> records =
                repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);

        // 月の全日分のDTOを用意し、既存レコードがあれば値を埋める(祝日・休日区分もここで解決する)
        List<AttendanceRecordDto> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            AttendanceRecord found = records.stream()
                    .filter(r -> r.getWorkDate().equals(date))
                    .findFirst()
                    .orElse(null);
            days.add(toDto(found, date));
        }

        MonthlySummaryDto summary = summarize(year, month, days);

        return MonthAttendanceDto.builder()
                .year(year)
                .month(month)
                .days(days)
                .summary(summary)
                .build();
    }

    @Transactional
    public AttendanceRecordDto saveDay(String employeeId, AttendanceRecordDto dto) {
        AttendanceRecord entity = repository
                .findByEmployeeIdAndWorkDate(employeeId, dto.getWorkDate())
                .orElseGet(AttendanceRecord::new);

        entity.setEmployeeId(employeeId);
        entity.setWorkDate(dto.getWorkDate());
        entity.setDayType(dto.getDayType() == null ? defaultDayType(dto.getWorkDate()) : dto.getDayType());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setBreakMinutes(dto.getBreakMinutes() == null ? 0 : dto.getBreakMinutes());
        entity.setLateOrEarly(Boolean.TRUE.equals(dto.getLateOrEarly()));
        entity.setPaidLeaveUnit(dto.getPaidLeaveUnit() == null ? 1.0 : dto.getPaidLeaveUnit());
        entity.setRemarks(dto.getRemarks());

        AttendanceRecord saved = repository.save(entity);
        return toDto(saved, saved.getWorkDate());
    }

    @Transactional
    public MonthAttendanceDto saveMonth(String employeeId, int year, int month, List<AttendanceRecordDto> dtoList) {
        for (AttendanceRecordDto dto : dtoList) {
            saveDay(employeeId, dto);
        }
        return getMonth(employeeId, year, month);
    }

    @Transactional
    public void deleteDay(String employeeId, LocalDate date) {
        repository.findByEmployeeIdAndWorkDate(employeeId, date)
                .ifPresent(repository::delete);
    }

    // ------------------------------------------------------------------
    // 締め日を起点とした任意期間の勤怠(⑦ カレンダー開始日の指定機能)。
    // 期間の開始日・終了日は AttendancePeriodResolver が計算し(会社カレンダーに
    // 合わせて変更可能・DBには何も保存しない)、ここでは既存の日次DTO組み立て
    // (toDto)・集計(summarize)・保存(saveDay)・CSV出力(csvRow)をそのまま再利用する。
    // getMonth/saveMonth/buildMonthlyCsv(暦月固定)は既存の挙動のまま変更していない。
    // ------------------------------------------------------------------

    /** 指定した開始日〜終了日の日次データ+集計を取得する(暦月をまたいでもよい) */
    @Transactional(readOnly = true)
    public AttendancePeriodDto getPeriod(String employeeId, LocalDate from, LocalDate to) {
        return getPeriod(employeeId, from, to, null);
    }

    @Transactional(readOnly = true)
    public AttendancePeriodDto getPeriod(String employeeId, LocalDate from, LocalDate to, Integer closingDay) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "終了日は開始日以降にしてください");
        }
        List<AttendanceRecord> records =
                repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);
        Map<LocalDate, AttendanceRecord> byDate = new HashMap<>();
        for (AttendanceRecord r : records) {
            byDate.put(r.getWorkDate(), r);
        }

        List<AttendanceRecordDto> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add(toDto(byDate.get(date), date));
        }

        // summarize()の year/month は「期間開始日の年月」を代表値として渡す(集計値そのものには影響しない)
        MonthlySummaryDto summary = summarize(from.getYear(), from.getMonthValue(), days);

        return AttendancePeriodDto.builder()
                .startDate(from)
                .endDate(to)
                .closingDay(closingDay)
                .days(days)
                .summary(summary)
                .build();
    }

    /** 指定した開始日〜終了日の日次データをまとめて保存する(saveMonthの期間版) */
    @Transactional
    public AttendancePeriodDto savePeriod(String employeeId, LocalDate from, LocalDate to, List<AttendanceRecordDto> dtoList) {
        for (AttendanceRecordDto dto : dtoList) {
            saveDay(employeeId, dto);
        }
        return getPeriod(employeeId, from, to);
    }

    /** 指定した開始日〜終了日の勤怠明細をCSV文字列として組み立てる(buildMonthlyCsvの期間版) */
    @Transactional(readOnly = true)
    public String buildPeriodCsv(String employeeId, LocalDate from, LocalDate to) {
        AttendancePeriodDto period = getPeriod(employeeId, from, to);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_HEADER)).append("\n");
        for (AttendanceRecordDto d : period.getDays()) {
            sb.append(csvRow(d)).append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 健康管理連携用(勤怠×健康分析・健康アラート判定で利用)。既存の挙動のまま変更していない。
    // ------------------------------------------------------------------

    /**
     * 指定期間の勤怠を簡易集計する(通常勤務の日のみを対象とした勤務時間・残業時間)。
     * 健康×勤怠の連携分析、および残業超過の健康アラート判定で使用する。
     */
    @Transactional(readOnly = true)
    public AttendanceRangeStatsDto getRangeStats(String employeeId, LocalDate from, LocalDate to) {
        List<AttendanceRecord> records =
                repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);

        double workingDays = 0;
        double workingMinutes = 0;
        double overtimeMinutes = 0;
        for (AttendanceRecord r : records) {
            boolean worked = r.getStartTime() != null && r.getEndTime() != null;
            if (!worked || r.getDayType() != DayType.NORMAL) continue;
            int workedMinutes = computeWorkedMinutes(r);
            workingDays += 1;
            workingMinutes += workedMinutes;
            overtimeMinutes += Math.max(0, workedMinutes - STANDARD_WORK_MINUTES);
        }

        return AttendanceRangeStatsDto.builder()
                .workingDays(workingDays)
                .workingHours(round2(workingMinutes / 60.0))
                .overtimeHours(round2(overtimeMinutes / 60.0))
                .build();
    }

    /**
     * 指定期間の勤怠を日別の実働・残業時間(分)に変換する。
     * 健康チェックの記録日と突き合わせるための、日付をキーにした一覧。
     * 出退勤の入力がない日(休日など)は含まない。
     */
    @Transactional(readOnly = true)
    public List<AttendanceDailyStatDto> getDailyStats(String employeeId, LocalDate from, LocalDate to) {
        List<AttendanceRecord> records =
                repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to);

        List<AttendanceDailyStatDto> result = new ArrayList<>();
        for (AttendanceRecord r : records) {
            boolean worked = r.getStartTime() != null && r.getEndTime() != null;
            if (!worked) continue;
            int workedMinutes = computeWorkedMinutes(r);
            int overtime = (r.getDayType() == DayType.NORMAL)
                    ? Math.max(0, workedMinutes - STANDARD_WORK_MINUTES)
                    : 0;
            result.add(AttendanceDailyStatDto.builder()
                    .date(r.getWorkDate())
                    .workMinutes(workedMinutes)
                    .overtimeMinutes(overtime)
                    .build());
        }
        return result;
    }

    /** 管理者向け勤怠一覧で全社員を横断表示するために使う */
    @Transactional(readOnly = true)
    public List<String> getAllEmployeeIds() {
        return repository.findDistinctEmployeeIds();
    }

    // ------------------------------------------------------------------
    // CSV出力
    // ------------------------------------------------------------------

    private static final String[] CSV_HEADER = {
            "勤務日", "曜日", "祝日名", "勤務日区分", "出勤時刻", "退勤時刻", "休憩時間(分)",
            "実働時間(h)", "通常勤務時間(h)", "通常残業(h)", "所定休日勤務時間(h)", "所定休日残業(h)",
            "法定休日勤務時間(h)", "法定休日残業(h)", "総残業時間(h)", "深夜勤務時間(h)"
    };

    /** 指定月の勤怠明細をCSV文字列として組み立てる(勤務日区分・祝日名・残業内訳を含む) */
    @Transactional(readOnly = true)
    public String buildMonthlyCsv(String employeeId, int year, int month) {
        MonthAttendanceDto month0 = getMonth(employeeId, year, month);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_HEADER)).append("\n");
        for (AttendanceRecordDto d : month0.getDays()) {
            sb.append(csvRow(d)).append("\n");
        }
        return sb.toString();
    }

    private String csvRow(AttendanceRecordDto d) {
        List<String> cols = new ArrayList<>();
        cols.add(d.getWorkDate().toString());
        cols.add(d.getDayOfWeekLabel());
        cols.add(csvEscape(d.getHolidayName()));
        cols.add(d.getDayType() == null ? "" : d.getDayType().getLabel());
        cols.add(d.getStartTime() == null ? "" : d.getStartTime().toString());
        cols.add(d.getEndTime() == null ? "" : d.getEndTime().toString());
        cols.add(String.valueOf(d.getBreakMinutes() == null ? 0 : d.getBreakMinutes()));
        cols.add(hoursOf(d.getWorkMinutes()));
        cols.add(hoursOf(d.getRegularWorkMinutes()));
        cols.add(hoursOf(d.getOvertimeMinutes()));
        cols.add(hoursOf(d.getCompanyHolidayWorkMinutes()));
        cols.add(hoursOf(d.getScheduledHolidayOvertimeMinutes()));
        cols.add(hoursOf(d.getStatutoryHolidayWorkMinutes()));
        cols.add(hoursOf(d.getStatutoryHolidayOvertimeMinutes()));
        cols.add(hoursOf(d.getTotalOvertimeMinutes()));
        cols.add(hoursOf(d.getNightMinutes()));
        return String.join(",", cols);
    }

    private String hoursOf(Integer minutes) {
        return minutes == null ? "0.00" : String.format(Locale.JAPAN, "%.2f", minutes / 60.0);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ------------------------------------------------------------------
    // 集計ロジック(月全体。祝日・休日区分を解決済みの days を対象に集計する)
    // ------------------------------------------------------------------

    private MonthlySummaryDto summarize(int year, int month, List<AttendanceRecordDto> days) {
        double workingDays = 0;
        double workingMinutes = 0;
        double overtimeMinutes = 0;
        double regularMinutes = 0;
        double nightOvertimeMinutes = 0;
        int lateOrEarlyCount = 0;
        double absenceDays = 0;
        double scheduledHolidayWorkDays = 0;
        double scheduledHolidayWorkMinutes = 0;
        double scheduledHolidayOvertimeMinutes = 0;
        double scheduledHolidayNightMinutes = 0;
        double paidLeaveDays = 0;
        double substituteOrCompensatoryDays = 0;
        double statutoryHolidayWorkDays = 0;
        double statutoryHolidayWorkMinutes = 0;
        double statutoryHolidayOvertimeMinutes = 0;
        double statutoryHolidayNightMinutes = 0;
        double scheduledWorkingDays = 0; // カレンダー上、通常勤務日となる日数(実績でなく計画)

        for (AttendanceRecordDto d : days) {
            boolean worked = d.getStartTime() != null && d.getEndTime() != null;

            if (Boolean.TRUE.equals(d.getLateOrEarly())) {
                lateOrEarlyCount++;
            }

            switch (d.getDayType()) {
                case NORMAL -> {
                    scheduledWorkingDays += 1;
                    if (worked) {
                        workingDays += 1;
                        workingMinutes += d.getWorkMinutes();
                        overtimeMinutes += d.getOvertimeMinutes();
                        regularMinutes += d.getRegularWorkMinutes();
                        nightOvertimeMinutes += d.getNightMinutes();
                    }
                }
                case SCHEDULED_HOLIDAY -> {
                    if (worked) {
                        scheduledHolidayWorkDays += 1;
                        scheduledHolidayWorkMinutes += d.getCompanyHolidayWorkMinutes();
                        scheduledHolidayOvertimeMinutes += d.getScheduledHolidayOvertimeMinutes();
                        scheduledHolidayNightMinutes += d.getNightMinutes();
                    }
                }
                case STATUTORY_HOLIDAY -> {
                    if (worked) {
                        statutoryHolidayWorkDays += 1;
                        statutoryHolidayWorkMinutes += d.getStatutoryHolidayWorkMinutes();
                        statutoryHolidayOvertimeMinutes += d.getStatutoryHolidayOvertimeMinutes();
                        statutoryHolidayNightMinutes += d.getNightMinutes();
                    }
                }
                case PAID_LEAVE -> paidLeaveDays += (d.getPaidLeaveUnit() == null ? 1.0 : d.getPaidLeaveUnit());
                case SUBSTITUTE_HOLIDAY, COMPENSATORY_HOLIDAY -> substituteOrCompensatoryDays += 1;
                case ABSENCE -> absenceDays += 1;
            }
        }

        double totalNightMinutes = nightOvertimeMinutes + scheduledHolidayNightMinutes + statutoryHolidayNightMinutes;
        double totalActualMinutes = regularMinutes + overtimeMinutes + scheduledHolidayWorkMinutes + statutoryHolidayWorkMinutes;
        double totalOvertimeMinutes = overtimeMinutes + scheduledHolidayOvertimeMinutes + statutoryHolidayOvertimeMinutes;

        return MonthlySummaryDto.builder()
                .year(year)
                .month(month)
                .workingDays(workingDays)
                .workingHours(round2(workingMinutes / 60.0))
                .overtimeHours(round2(overtimeMinutes / 60.0))
                .nightOvertimeHours(round2(nightOvertimeMinutes / 60.0))
                .lateOrEarlyCount(lateOrEarlyCount)
                .absenceDays(absenceDays)
                .scheduledHolidayWorkDays(scheduledHolidayWorkDays)
                .scheduledHolidayNightHours(round2(scheduledHolidayNightMinutes / 60.0))
                .paidLeaveDays(paidLeaveDays)
                .substituteOrCompensatoryDays(substituteOrCompensatoryDays)
                .statutoryHolidayWorkDays(statutoryHolidayWorkDays)
                .statutoryHolidayNightHours(round2(statutoryHolidayNightMinutes / 60.0))
                .scheduledWorkingHours(round2(scheduledWorkingDays * STANDARD_WORK_MINUTES / 60.0))
                .regularHours(round2(regularMinutes / 60.0))
                .scheduledHolidayWorkHours(round2(scheduledHolidayWorkMinutes / 60.0))
                .statutoryHolidayWorkHours(round2(statutoryHolidayWorkMinutes / 60.0))
                .totalNightHours(round2(totalNightMinutes / 60.0))
                .totalActualWorkingHours(round2(totalActualMinutes / 60.0))
                .scheduledHolidayOvertimeHours(round2(scheduledHolidayOvertimeMinutes / 60.0))
                .statutoryHolidayOvertimeHours(round2(statutoryHolidayOvertimeMinutes / 60.0))
                .totalOvertimeHours(round2(totalOvertimeMinutes / 60.0))
                .build();
    }

    private int computeWorkedMinutes(AttendanceRecord r) {
        int span = minutesBetween(r.getStartTime(), r.getEndTime());
        int brk = r.getBreakMinutes() == null ? 0 : r.getBreakMinutes();
        return Math.max(0, span - brk);
    }

    private int computeNightMinutes(AttendanceRecord r) {
        int s = r.getStartTime().toSecondOfDay() / 60;
        int e = r.getEndTime().toSecondOfDay() / 60;
        if (e <= s) {
            e += 24 * 60; // 日をまたぐ場合
        }
        int overlap = Math.min(e, NIGHT_END_MIN) - Math.max(s, NIGHT_START_MIN);
        return Math.max(0, overlap);
    }

    private int minutesBetween(LocalTime start, LocalTime end) {
        int s = start.toSecondOfDay() / 60;
        int e = end.toSecondOfDay() / 60;
        if (e <= s) {
            e += 24 * 60;
        }
        return e - s;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // 休日区分の解決(固定の休日ルール + 日本の祝日カレンダー)
    // ------------------------------------------------------------------

    /**
     * 区分未設定の日のデフォルト区分。
     * 1. 曜日から仮決定(土曜日=所定休日, 日曜日=法定休日, 平日=通常勤務日。固定値)
     * 2. その仮決定が「通常勤務日」で、かつ当日が日本の祝日であれば「所定休日」に格上げする
     *    (祝日だからといって法定休日には絶対に変えない。既に法定休日/所定休日の曜日は
     *    そのまま=祝日情報は holidayName 側で表示するだけに留める)
     */
    private DayType defaultDayType(LocalDate date) {
        DayType weekdayDefault = weekdayDefaultDayType(date);
        if (weekdayDefault == DayType.NORMAL && holidayCalendarService.isPublicHoliday(date)) {
            return DayType.SCHEDULED_HOLIDAY;
        }
        return weekdayDefault;
    }

    /**
     * 未入力の日の既定の勤務区分を解決する(公開版)。休憩機能(AttendanceBreakService)が、
     * 出勤時刻を入力していない日でも休憩を開始できるようにする際、その日の勤怠レコードを
     * 新規作成するために使う(既存のdefaultDayTypeロジックをそのまま再利用し、
     * 休日区分の判定を重複実装しないため)。
     */
    public DayType resolveDefaultDayType(LocalDate date) {
        return defaultDayType(date);
    }

    private DayType weekdayDefaultDayType(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return DayType.STATUTORY_HOLIDAY;
        }
        if (dow == DayOfWeek.SATURDAY) {
            return DayType.SCHEDULED_HOLIDAY;
        }
        return DayType.NORMAL;
    }

    private AttendanceRecordDto toDto(AttendanceRecord r, LocalDate date) {
        String holidayName = holidayCalendarService.getHolidayName(date).orElse(null);
        boolean isPublicHoliday = holidayName != null;
        String dayOfWeekLabel = WEEKDAY_LABELS_JA[date.getDayOfWeek().getValue() - 1];

        if (r == null) {
            return AttendanceRecordDto.builder()
                    .workDate(date)
                    .dayType(defaultDayType(date))
                    .breakMinutes(0)
                    .breakStartTime(null)
                    .breakEndTime(null)
                    .lateOrEarly(false)
                    .paidLeaveUnit(1.0)
                    .workMinutes(0)
                    .overtimeMinutes(0)
                    .nightMinutes(0)
                    .regularWorkMinutes(0)
                    .companyHolidayWorkMinutes(0)
                    .statutoryHolidayWorkMinutes(0)
                    .scheduledHolidayOvertimeMinutes(0)
                    .statutoryHolidayOvertimeMinutes(0)
                    .totalOvertimeMinutes(0)
                    .isPublicHoliday(isPublicHoliday)
                    .holidayName(holidayName)
                    .dayOfWeekLabel(dayOfWeekLabel)
                    .build();
        }
        boolean worked = r.getStartTime() != null && r.getEndTime() != null;
        int workMinutes = worked ? computeWorkedMinutes(r) : 0;
        int nightMinutes = worked ? computeNightMinutes(r) : 0;

        // 実働のうち、所定労働時間(8時間=480分)を超えた部分を残業とする。
        // この考え方は通常勤務日・所定休日・法定休日のいずれでも同じ(休日区分ごとに
        // 集計先のバケツが変わるだけで、計算ロジックそのものは矛盾なく共通化している)。
        int overtimeOverStandard = Math.max(0, workMinutes - STANDARD_WORK_MINUTES);

        int overtime = (r.getDayType() == DayType.NORMAL) ? overtimeOverStandard : 0;
        int regularWorkMinutes = (r.getDayType() == DayType.NORMAL) ? (workMinutes - overtime) : 0;

        int companyHolidayWorkMinutes = (r.getDayType() == DayType.SCHEDULED_HOLIDAY) ? workMinutes : 0;
        int statutoryHolidayWorkMinutes = (r.getDayType() == DayType.STATUTORY_HOLIDAY) ? workMinutes : 0;

        int scheduledHolidayOvertimeMinutes = (r.getDayType() == DayType.SCHEDULED_HOLIDAY) ? overtimeOverStandard : 0;
        int statutoryHolidayOvertimeMinutes = (r.getDayType() == DayType.STATUTORY_HOLIDAY) ? overtimeOverStandard : 0;
        int totalOvertimeMinutes = overtime + scheduledHolidayOvertimeMinutes + statutoryHolidayOvertimeMinutes;

        return AttendanceRecordDto.builder()
                .id(r.getId())
                .workDate(r.getWorkDate())
                .dayType(r.getDayType())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .breakMinutes(r.getBreakMinutes())
                .breakStartTime(r.getBreakStartTime())
                .breakEndTime(r.getBreakEndTime())
                .lateOrEarly(r.getLateOrEarly())
                .paidLeaveUnit(r.getPaidLeaveUnit())
                .remarks(r.getRemarks())
                .workMinutes(workMinutes)
                .overtimeMinutes(overtime)
                .nightMinutes(nightMinutes)
                .regularWorkMinutes(regularWorkMinutes)
                .companyHolidayWorkMinutes(companyHolidayWorkMinutes)
                .statutoryHolidayWorkMinutes(statutoryHolidayWorkMinutes)
                .scheduledHolidayOvertimeMinutes(scheduledHolidayOvertimeMinutes)
                .statutoryHolidayOvertimeMinutes(statutoryHolidayOvertimeMinutes)
                .totalOvertimeMinutes(totalOvertimeMinutes)
                .isPublicHoliday(isPublicHoliday)
                .holidayName(holidayName)
                .dayOfWeekLabel(dayOfWeekLabel)
                .build();
    }
}
