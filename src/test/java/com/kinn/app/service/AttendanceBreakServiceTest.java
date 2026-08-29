package com.kinn.app.service;

import com.kinn.app.dto.BreakStatus;
import com.kinn.app.dto.BreakStatusDto;
import com.kinn.app.entity.AttendanceRecord;
import com.kinn.app.entity.DayType;
import com.kinn.app.repository.AttendanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttendanceBreakService(トップページの自動休憩機能②〜⑥⑪〜⑮⑳)のユニットテスト。
 * DBは使わず、休憩開始・終了・二重休憩防止・複数回に分けて取る休憩(1日の合計は上限を超えない)・
 * 60分経過時のサーバー側自動終了(⑭)を検証する。
 * テストは休憩予算を1分に短縮して実行する(㉕。本番はapplication.propertiesのデフォルト60分)。
 *
 * 出勤時刻の入力は休憩開始の前提にしない: 当日の勤怠レコードが無くても・出勤時刻が未入力でも
 * [休憩開始]を押せる。
 */
class AttendanceBreakServiceTest {

    private static final String EMPLOYEE_ID = "1|yamada";
    private AttendanceRecordRepository repository;
    private AttendanceService attendanceService;
    private AttendanceBreakService service;

    @BeforeEach
    void setUp() {
        repository = mock(AttendanceRecordRepository.class);
        // AttendanceServiceはMockitoのinlineモック化がこの環境のJavaバージョンと相性が悪いため、
        // 他のテストと同じく実体+モック化したHolidayOverrideRepositoryの組み合わせで用意する
        // (resolveDefaultDayTypeは祝日判定を通すだけの薄いラッパーなので、実体を使っても
        // 純粋な単体テストの独立性は損なわれない)。
        com.kinn.app.repository.HolidayOverrideRepository overrideRepository =
                mock(com.kinn.app.repository.HolidayOverrideRepository.class);
        when(overrideRepository.findByDate(any())).thenReturn(Optional.empty());
        attendanceService = new AttendanceService(repository, new HolidayCalendarService(overrideRepository));
        service = new AttendanceBreakService(repository, attendanceService, 1); // テスト用に1分に短縮(㉕)
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AttendanceRecord workingRecord() {
        return AttendanceRecord.builder()
                .id(1L).employeeId(EMPLOYEE_ID).workDate(LocalDate.now())
                .dayType(DayType.NORMAL).startTime(LocalTime.of(9, 0))
                .breakMinutes(0)
                .build();
    }

    @Test
    void 勤怠レコードが無くても休憩を開始できる() {
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.empty());

        BreakStatusDto dto = service.start(EMPLOYEE_ID);

        assertEquals(BreakStatus.ON_BREAK, dto.getStatus());
        assertNotNull(dto.getBreakStartTime());
        verify(repository).save(any());
    }

    @Test
    void 出勤時刻が未入力でも休憩を開始できる() {
        AttendanceRecord record = AttendanceRecord.builder()
                .id(1L).employeeId(EMPLOYEE_ID).workDate(LocalDate.now())
                .dayType(DayType.NORMAL).startTime(null) // 出勤時刻未入力
                .breakMinutes(0)
                .build();
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.start(EMPLOYEE_ID);

        assertEquals(BreakStatus.ON_BREAK, dto.getStatus());
        assertNotNull(dto.getScheduledEndTime());
        assertEquals(1, dto.getBreakDurationMinutes());
        assertTrue(dto.getRemainingSeconds() > 0 && dto.getRemainingSeconds() <= 60);
    }

    @Test
    void 出勤済みで勤務中でも休憩を開始できる() {
        AttendanceRecord record = workingRecord();
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.start(EMPLOYEE_ID);

        assertEquals(BreakStatus.ON_BREAK, dto.getStatus());
        assertNotNull(dto.getBreakStartTime());
    }

    @Test
    void 既に休憩中の場合は二重に休憩を開始できない() {
        AttendanceRecord record = workingRecord();
        record.setBreakStartTime(LocalTime.now());
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.start(EMPLOYEE_ID));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("すでに休憩中"));
    }

    @Test
    void 既に退勤している場合は休憩を開始できない() {
        AttendanceRecord record = workingRecord();
        record.setEndTime(LocalTime.of(18, 0));
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.start(EMPLOYEE_ID));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("退勤"));
    }

    @Test
    void 休憩中でなければ手動終了できない() {
        AttendanceRecord record = workingRecord();
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.end(EMPLOYEE_ID));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 手動で休憩を終了すると実測分数がbreakMinutesとautoBreakMinutesの両方に保存される() {
        AttendanceRecord record = workingRecord();
        record.setBreakStartTime(LocalTime.now().minusSeconds(30));
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.end(EMPLOYEE_ID);

        assertEquals(BreakStatus.WORKING, dto.getStatus()); // 予算がまだ残っているのでWORKINGに戻る
        assertNotNull(dto.getBreakEndTime());
        assertEquals(0, record.getBreakMinutes()); // 30秒 = 0分(切り捨て)
        assertEquals(0, record.getAutoBreakMinutes());
        assertNotNull(dto.getMessage());
    }

    @Test
    void 休憩開始から残り予算を経過しているとstatus取得時にサーバー側で自動終了する() {
        AttendanceRecord record = workingRecord();
        record.setBreakStartTime(LocalTime.now().minusMinutes(5)); // 1分設定なのですでに超過
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.BREAK_EXHAUSTED, dto.getStatus());
        assertEquals(1, record.getBreakMinutes()); // 設定どおり1分ぶんだけ休憩時間として確定する
        assertEquals(1, record.getAutoBreakMinutes());
        verify(repository).save(record);
    }

    /**
     * ⑯の確認(非常に重要): 勤怠画面で手動入力された休憩時間(break_minutes)は、
     * この機能の1日の予算判定には一切影響しない。これが無いと、あらかじめ昼休憩を
     * 手入力していたユーザーが、この機能を一度も使っていないのに「休憩を使い切った」と
     * 判定され、[休憩開始]ボタンが機能しなくなってしまう(実際に発生した不具合)。
     */
    @Test
    void 勤怠画面で手動入力された休憩時間は自動休憩機能の予算判定に影響しない() {
        AttendanceRecord record = workingRecord();
        record.setBreakMinutes(60); // 勤怠画面で昼休憩60分をあらかじめ手入力していた想定
        // autoBreakMinutesは0のまま(この機能はまだ一度も使っていない)
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        // 1分設定のserviceでも、autoBreakMinutesが0であれば休憩を開始できる
        BreakStatusDto dto = service.start(EMPLOYEE_ID);

        assertEquals(BreakStatus.ON_BREAK, dto.getStatus());
        assertEquals(1, dto.getRemainingBudgetMinutes()); // 手動入力の60分とは無関係に、まるまる1分残っている
    }

    @Test
    void 手動入力の休憩時間があってもstatus取得はBREAK_EXHAUSTEDにならずWORKINGになる() {
        AttendanceRecord record = workingRecord();
        record.setBreakMinutes(60); // 手動入力のみ。autoBreakMinutesは0
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.WORKING, dto.getStatus());
        assertEquals(0, dto.getUsedMinutesToday());
        assertEquals(1, dto.getRemainingBudgetMinutes());
    }

    @Test
    void 予算内であればstatus取得時に自動終了しない() {
        AttendanceRecord record = workingRecord();
        record.setBreakStartTime(LocalTime.now().minusSeconds(10)); // 1分設定に対してまだ超過していない
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.ON_BREAK, dto.getStatus());
        assertTrue(dto.getRemainingSeconds() > 0);
        verify(repository, never()).save(any());
    }

    @Test
    void 勤怠記録がまだない場合は休憩を開始できる状態になる() {
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.empty());

        BreakStatusDto dto = service.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.WORKING, dto.getStatus());
        assertEquals(1, dto.getRemainingBudgetMinutes());
    }

    @Test
    void 退勤済みならCLOCKED_OUTになる() {
        AttendanceRecord record = workingRecord();
        record.setEndTime(LocalTime.of(18, 0));
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.CLOCKED_OUT, dto.getStatus());
    }

    // ------------------------------------------------------------------
    // 休憩を複数回(刻んで)取るケース。1日の合計が上限(ここでは60分)を超えないことを検証する
    // ------------------------------------------------------------------

    @Test
    void 一度休憩を終えても予算が残っていれば再度開始できる() {
        AttendanceBreakService fullMinuteService = new AttendanceBreakService(repository, attendanceService, 60);
        AttendanceRecord record = workingRecord();
        // 1回目でこの機能により既に20分使用済み(残り40分)という状態を模す
        record.setBreakMinutes(20);
        record.setAutoBreakMinutes(20);
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        // 1回終えた後でも、予算が残っていれば2回目を開始できる(刻んで取る運用に対応)
        BreakStatusDto secondStart = fullMinuteService.start(EMPLOYEE_ID);
        assertEquals(BreakStatus.ON_BREAK, secondStart.getStatus());
        assertEquals(40, secondStart.getRemainingBudgetMinutes()); // 60 - 20 = 残り40分がこの回の予算になる

        // 休憩中はさらに追加で開始できない(⑬二重休憩防止は複数回運用でも維持する)
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fullMinuteService.start(EMPLOYEE_ID));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 本日の休憩予算を使い切ると再度開始できない() {
        AttendanceRecord record = workingRecord();
        record.setBreakMinutes(1);
        record.setAutoBreakMinutes(1); // 1分設定に対してこの機能ですでに使い切っている想定
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.start(EMPLOYEE_ID));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("使い切りました"));
    }

    @Test
    void 使い切った状態のstatusはBREAK_EXHAUSTEDで合計時間を返す() {
        AttendanceRecord record = workingRecord();
        record.setBreakMinutes(1);
        record.setAutoBreakMinutes(1);
        record.setBreakStartTime(LocalTime.of(10, 0));
        record.setBreakEndTime(LocalTime.of(10, 1));
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = service.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.BREAK_EXHAUSTED, dto.getStatus());
        assertEquals(1, dto.getActualBreakMinutes());
        assertEquals(0, dto.getRemainingBudgetMinutes());
    }

    @Test
    void 二回目の休憩は一回目で使った分を差し引いた残り予算で自動終了する() {
        AttendanceBreakService fullMinuteService = new AttendanceBreakService(repository, attendanceService, 60);
        // workDateとbreakStartTimeを同じ基準時刻から算出する(日付をLocalDate.now()に固定したまま
        // 時刻だけLocalTime.now().minusMinutes(N)にすると、実行時刻が深夜0時から15分未満の場合に
        // 「前日の時刻」へラップアラウンドしてbreakStartTimeが未来時刻扱いになってしまうため)
        LocalDateTime start = LocalDateTime.now().minusMinutes(15); // 2回目が15分経過 → 残り10分を超過
        AttendanceRecord record = AttendanceRecord.builder()
                .id(1L).employeeId(EMPLOYEE_ID).workDate(start.toLocalDate())
                .dayType(DayType.NORMAL).startTime(LocalTime.of(9, 0))
                .breakMinutes(50)
                .autoBreakMinutes(50) // 1回目でこの機能により50分使用済み(残り10分)
                .breakStartTime(start.toLocalTime())
                .build();
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        BreakStatusDto dto = fullMinuteService.status(EMPLOYEE_ID);

        assertEquals(BreakStatus.BREAK_EXHAUSTED, dto.getStatus());
        assertEquals(60, record.getBreakMinutes()); // 50 + 残り予算10 = 60(上限ちょうど)
        assertEquals(60, record.getAutoBreakMinutes());
    }

    /**
     * ⑮の確認: 休憩終了で確定したbreakMinutesが、既存のAttendanceService(実働・残業計算)に
     * そのまま正しく反映されることを検証する(新しい計算ロジックを追加していないことの裏付け)。
     * 09:00〜18:00勤務(拘束9時間)から60分休憩(自動休憩機能で60分に設定)を引くと実働8時間になる。
     */
    @Test
    void 確定した休憩時間が既存の実働時間計算に正しく反映される() {
        AttendanceBreakService fullMinuteService = new AttendanceBreakService(repository, attendanceService, 60);
        // workDateとbreakStartTimeを同じ基準時刻から算出する(日付をLocalDate.now()に固定したまま
        // 時刻だけLocalTime.now().minusMinutes(N)にすると、深夜0時から70分未満での実行時に
        // 「前日の時刻」へラップアラウンドしてbreakStartTimeが未来時刻扱いになってしまうため)
        LocalDateTime start = LocalDateTime.now().minusMinutes(70); // 60分設定に対して超過済み
        AttendanceRecord record = AttendanceRecord.builder()
                .id(1L).employeeId(EMPLOYEE_ID).workDate(start.toLocalDate())
                .dayType(DayType.NORMAL).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .breakMinutes(0)
                .breakStartTime(start.toLocalTime())
                .build();
        when(repository.findByEmployeeIdAndWorkDate(eq(EMPLOYEE_ID), any())).thenReturn(Optional.of(record));

        fullMinuteService.status(EMPLOYEE_ID); // サーバー側で自動終了 → breakMinutes=60が確定

        assertEquals(60, record.getBreakMinutes());

        com.kinn.app.repository.HolidayOverrideRepository overrideRepository =
                mock(com.kinn.app.repository.HolidayOverrideRepository.class);
        when(overrideRepository.findByDate(any())).thenReturn(Optional.empty());
        HolidayCalendarService holidayCalendarService = new HolidayCalendarService(overrideRepository);
        AttendanceRecordRepository attendanceRepo = mock(AttendanceRecordRepository.class);
        when(attendanceRepo.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(any(), any(), any()))
                .thenReturn(java.util.List.of(record));
        AttendanceService realAttendanceService = new AttendanceService(attendanceRepo, holidayCalendarService);

        var period = realAttendanceService.getPeriod(EMPLOYEE_ID, record.getWorkDate(), record.getWorkDate());
        var day = period.getDays().get(0);

        assertEquals(480, day.getWorkMinutes()); // 9時間拘束 - 60分休憩 = 実働8時間(480分)
        assertEquals(0, day.getOvertimeMinutes()); // 残業なし
    }
}
