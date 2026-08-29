package com.kinn.app.service;

import com.kinn.app.dto.AttendancePeriodDto;
import com.kinn.app.dto.AttendanceRecordDto;
import com.kinn.app.entity.AttendanceRecord;
import com.kinn.app.entity.DayType;
import com.kinn.app.repository.AttendanceRecordRepository;
import com.kinn.app.repository.HolidayOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AttendanceService#getPeriod が、暦月をまたぐ期間でも既存の集計ロジック(summarize)・
 * 日次DTO組み立て(toDto)を再利用して正しく動くことを確認する。DBは使わず
 * リポジトリをモック化した純粋なユニットテスト。
 */
class AttendanceServiceGetPeriodTest {

    private AttendanceRecordRepository repository;
    private AttendanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(AttendanceRecordRepository.class);
        HolidayOverrideRepository overrideRepository = mock(HolidayOverrideRepository.class);
        when(overrideRepository.findByDate(any())).thenReturn(Optional.empty());
        HolidayCalendarService holidayCalendarService = new HolidayCalendarService(overrideRepository);
        service = new AttendanceService(repository, holidayCalendarService);
    }

    @Test
    void 締め日20日の期間は8月20日から9月19日の31日分になる() {
        when(repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AttendancePeriodDto period = service.getPeriod("e1",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 19));

        assertEquals(LocalDate.of(2026, 8, 20), period.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 19), period.getEndDate());
        assertEquals(31, period.getDays().size());
        assertEquals(LocalDate.of(2026, 8, 20), period.getDays().get(0).getWorkDate());
        assertEquals(LocalDate.of(2026, 9, 19), period.getDays().get(period.getDays().size() - 1).getWorkDate());
    }

    @Test
    void 期間内の実績が正しく反映され残業も既存ロジックで集計される() {
        // 2026-09-07(月)に10時間実働(8時→18時、休憩なし) = 通常残業2時間
        AttendanceRecord worked = AttendanceRecord.builder()
                .employeeId("e1")
                .workDate(LocalDate.of(2026, 9, 7))
                .dayType(DayType.NORMAL)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(18, 0))
                .breakMinutes(0)
                .build();
        when(repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(List.of(worked));

        AttendancePeriodDto period = service.getPeriod("e1",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 19));

        AttendanceRecordDto day = period.getDays().stream()
                .filter(d -> d.getWorkDate().equals(LocalDate.of(2026, 9, 7)))
                .findFirst().orElseThrow();
        assertEquals(600, day.getWorkMinutes());
        assertEquals(120, day.getOvertimeMinutes());
        assertTrue(period.getSummary().getOvertimeHours() >= 2.0);
    }

    @Test
    void 未入力の日は日曜が法定休日_土曜が所定休日のデフォルト区分になる() {
        when(repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AttendancePeriodDto period = service.getPeriod("e1",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 19));

        AttendanceRecordDto sunday = period.getDays().stream()
                .filter(d -> d.getWorkDate().equals(LocalDate.of(2026, 8, 23)))
                .findFirst().orElseThrow();
        AttendanceRecordDto saturday = period.getDays().stream()
                .filter(d -> d.getWorkDate().equals(LocalDate.of(2026, 8, 22)))
                .findFirst().orElseThrow();
        assertEquals(DayType.STATUTORY_HOLIDAY, sunday.getDayType());
        assertEquals(DayType.SCHEDULED_HOLIDAY, saturday.getDayType());
    }

    @Test
    void savePeriodは1日ずつsaveDayを呼びgetPeriodと同じ形で結果を返す() {
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AttendanceRecordDto toSave = AttendanceRecordDto.builder()
                .workDate(LocalDate.of(2026, 8, 20))
                .dayType(DayType.NORMAL)
                .build();

        AttendancePeriodDto result = service.savePeriod("e1",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 19), List.of(toSave));

        assertEquals(LocalDate.of(2026, 8, 20), result.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 19), result.getEndDate());
    }
}
