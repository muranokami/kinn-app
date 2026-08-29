package com.kinn.app.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 「締め日(1〜31日)」を起点とする1か月単位の勤怠期間(開始日〜終了日)を計算する
 * ユーティリティ。日付計算は文字列操作ではなく {@link LocalDate}/{@link YearMonth} のみで行う。
 *
 * 期間の考え方(要件どおり):
 *   開始日 = 締め日のN日目
 *   終了日 = 翌月のN-1日目
 *   例: 締め日20日 → 8/20 〜 9/19
 *   締め日1日   → 1日 〜 月末 (従来の暦月と完全に一致する)
 *
 * 存在しない日の扱い(採用ルール):
 *   締め日が31日など、対象月に存在しない日の場合は「その月の末日に補正する」。
 *   例: 締め日31日、2月が28日までしかない → その期間の開始日/終了日は28日に補正される。
 *   このルールにより、どんな締め日を選んでも隙間なく連続した期間の並びになる
 *   (前の期間の終了日の翌日が必ず次の期間の開始日になる)。
 */
public final class AttendancePeriodResolver {

    public static final int MIN_CLOSING_DAY = 1;
    public static final int MAX_CLOSING_DAY = 31;
    /** 締め日未指定時のデフォルト(=暦月、従来の1日〜月末と同じ) */
    public static final int DEFAULT_CLOSING_DAY = 1;

    private AttendancePeriodResolver() {
    }

    /** 締め日の値を検証し、未指定ならデフォルト値を返す */
    public static int normalizeClosingDay(Integer closingDay) {
        if (closingDay == null) {
            return DEFAULT_CLOSING_DAY;
        }
        if (closingDay < MIN_CLOSING_DAY || closingDay > MAX_CLOSING_DAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "締め日は1〜31の範囲で指定してください");
        }
        return closingDay;
    }

    /**
     * 指定した年月における期間開始日。
     * その年月に締め日の日付が存在しない場合(例: 2月31日)は月末日に補正する。
     */
    public static LocalDate startOfMonth(YearMonth yearMonth, int closingDay) {
        int day = Math.min(closingDay, yearMonth.lengthOfMonth());
        return yearMonth.atDay(day);
    }

    /** 期間開始日から、その期間の終了日(翌月の期間開始日の前日)を求める */
    public static LocalDate endFromStart(LocalDate start, int closingDay) {
        YearMonth nextMonth = YearMonth.from(start).plusMonths(1);
        return startOfMonth(nextMonth, closingDay).minusDays(1);
    }

    /** 指定した日(reference)を含む期間の開始日を求める */
    public static LocalDate startContaining(LocalDate reference, int closingDay) {
        YearMonth ym = YearMonth.from(reference);
        LocalDate startThisMonth = startOfMonth(ym, closingDay);
        if (!reference.isBefore(startThisMonth)) {
            return startThisMonth;
        }
        return startOfMonth(ym.minusMonths(1), closingDay);
    }

    /** 1つ前の期間の開始日 */
    public static LocalDate previousStart(LocalDate currentStart, int closingDay) {
        return startOfMonth(YearMonth.from(currentStart).minusMonths(1), closingDay);
    }

    /** 1つ次の期間の開始日(現在の期間の終了日の翌日と一致する) */
    public static LocalDate nextStart(LocalDate currentStart, int closingDay) {
        return endFromStart(currentStart, closingDay).plusDays(1);
    }

    /**
     * リクエストパラメータから期間(開始日・終了日)を解決する。
     * startDate/endDateが両方指定されていればそれをそのまま使う(⑬のAPI形式)。
     * 指定がなければ closingDay(締め日) と baseDate(基準日。省略時は本日) から
     * その基準日を含む期間を計算する。
     *
     * @return [開始日, 終了日]
     */
    public static LocalDate[] resolveRange(String startDateStr, String endDateStr,
                                            Integer closingDay, String baseDateStr) {
        try {
            if (startDateStr != null && endDateStr != null) {
                LocalDate from = LocalDate.parse(startDateStr);
                LocalDate to = LocalDate.parse(endDateStr);
                if (to.isBefore(from)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "終了日は開始日以降にしてください");
                }
                return new LocalDate[]{from, to};
            }
            int cd = normalizeClosingDay(closingDay);
            LocalDate base = baseDateStr != null ? LocalDate.parse(baseDateStr) : LocalDate.now();
            LocalDate from = startContaining(base, cd);
            LocalDate to = endFromStart(from, cd);
            return new LocalDate[]{from, to};
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日付の形式が正しくありません");
        }
    }
}
