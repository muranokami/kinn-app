package com.kinn.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 締め日(カレンダー開始日)を起点とした期間計算のテスト。
 * 要件(18. テスト)に挙げられているケースをそのまま検証する。
 */
class AttendancePeriodResolverTest {

    @Test
    void 締め日20日_基準日が期間内なら8月20日から9月19日になる() {
        LocalDate start = AttendancePeriodResolver.startContaining(LocalDate.of(2026, 8, 22), 20);
        LocalDate end = AttendancePeriodResolver.endFromStart(start, 20);
        assertEquals(LocalDate.of(2026, 8, 20), start);
        assertEquals(LocalDate.of(2026, 9, 19), end);
    }

    @Test
    void 締め日15日_8月15日から9月14日になる() {
        LocalDate start = AttendancePeriodResolver.startContaining(LocalDate.of(2026, 8, 20), 15);
        LocalDate end = AttendancePeriodResolver.endFromStart(start, 15);
        assertEquals(LocalDate.of(2026, 8, 15), start);
        assertEquals(LocalDate.of(2026, 9, 14), end);
    }

    @Test
    void 締め日1日_従来の暦月と一致する() {
        LocalDate start = AttendancePeriodResolver.startContaining(LocalDate.of(2026, 8, 22), 1);
        LocalDate end = AttendancePeriodResolver.endFromStart(start, 1);
        assertEquals(LocalDate.of(2026, 8, 1), start);
        assertEquals(LocalDate.of(2026, 8, 31), end);
    }

    @Test
    void 基準日が期間の締め日より前なら前月分の期間になる() {
        // 締め日20日で基準日が8/10なら、まだ8/20を迎えていないので 7/20〜8/19 の期間
        LocalDate start = AttendancePeriodResolver.startContaining(LocalDate.of(2026, 8, 10), 20);
        assertEquals(LocalDate.of(2026, 7, 20), start);
        assertEquals(LocalDate.of(2026, 8, 19), AttendancePeriodResolver.endFromStart(start, 20));
    }

    @Test
    void 前の期間へ移動すると1か月前の同じ締め日になる() {
        LocalDate current = LocalDate.of(2026, 8, 20);
        assertEquals(LocalDate.of(2026, 7, 20), AttendancePeriodResolver.previousStart(current, 20));
    }

    @Test
    void 次の期間へ移動すると1か月後の同じ締め日になる() {
        LocalDate current = LocalDate.of(2026, 8, 20);
        assertEquals(LocalDate.of(2026, 9, 20), AttendancePeriodResolver.nextStart(current, 20));
    }

    @Test
    void 締め日31日は月末が28日の月では28日に補正される() {
        // 2026年は平年なので2月は28日まで
        LocalDate start = AttendancePeriodResolver.startOfMonth(YearMonth.of(2026, 2), 31);
        assertEquals(LocalDate.of(2026, 2, 28), start);
    }

    @Test
    void 締め日31日_1月31日始まりの期間は2月27日で終わる() {
        // 1/31 始まり → 翌月(2月)の期間開始日(28日に補正)の前日 = 2/27
        LocalDate start = LocalDate.of(2026, 1, 31);
        LocalDate end = AttendancePeriodResolver.endFromStart(start, 31);
        assertEquals(LocalDate.of(2026, 2, 27), end);
    }

    @Test
    void 締め日31日でも期間は隙間なく連続する() {
        LocalDate p1Start = LocalDate.of(2026, 1, 31);
        LocalDate p1End = AttendancePeriodResolver.endFromStart(p1Start, 31);
        LocalDate p2Start = AttendancePeriodResolver.nextStart(p1Start, 31);
        assertEquals(p1End.plusDays(1), p2Start);

        LocalDate p2End = AttendancePeriodResolver.endFromStart(p2Start, 31);
        LocalDate p3Start = AttendancePeriodResolver.nextStart(p2Start, 31);
        assertEquals(p2End.plusDays(1), p3Start);
    }

    @Test
    void 締め日が範囲外だと400エラーになる() {
        assertThrows(ResponseStatusException.class, () -> AttendancePeriodResolver.normalizeClosingDay(0));
        assertThrows(ResponseStatusException.class, () -> AttendancePeriodResolver.normalizeClosingDay(32));
    }

    @Test
    void 締め日未指定は1日扱いになる() {
        assertEquals(1, AttendancePeriodResolver.normalizeClosingDay(null));
    }

    @Test
    void resolveRangeはstartDateとendDateを直接指定できる() {
        LocalDate[] range = AttendancePeriodResolver.resolveRange("2026-08-20", "2026-09-19", null, null);
        assertEquals(LocalDate.of(2026, 8, 20), range[0]);
        assertEquals(LocalDate.of(2026, 9, 19), range[1]);
    }

    @Test
    void resolveRangeは終了日が開始日より前だと400エラーになる() {
        assertThrows(ResponseStatusException.class,
                () -> AttendancePeriodResolver.resolveRange("2026-09-19", "2026-08-20", null, null));
    }

    @Test
    void resolveRangeはclosingDayとbaseDateから期間を計算できる() {
        LocalDate[] range = AttendancePeriodResolver.resolveRange(null, null, 20, "2026-08-22");
        assertEquals(LocalDate.of(2026, 8, 20), range[0]);
        assertEquals(LocalDate.of(2026, 9, 19), range[1]);
    }
}
