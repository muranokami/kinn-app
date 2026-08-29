package com.kinn.app.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 日本の国民の祝日を計算する(振替休日・国民の休日を含む)、DBやネットワークに依存しない
 * 純粋なロジッククラス。
 *
 * ・ハッピーマンデー(成人の日・海の日・敬老の日・スポーツの日)、春分の日・秋分の日(近似式)、
 *   固定日の祝日をすべてこの1クラスに閉じている。
 * ・法改正による一時的な移動(2020年東京オリンピック特例など)までは追随できないため、
 *   そうした例外は {@link HolidayCalendarService} が参照する {@code holiday_override}
 *   テーブルで個別に補正する(=「祝日カレンダーを将来更新できる構造」の実体)。
 * ・春分・秋分の近似式は1980〜2099年の範囲で有効なものを採用している。
 */
public final class JapaneseHolidayCalculator {

    private JapaneseHolidayCalculator() {
    }

    /** 指定年の祝日を「日付→祝日名」で返す(振替休日・国民の休日を反映済み) */
    public static Map<LocalDate, String> holidaysOf(int year) {
        Map<LocalDate, String> base = new TreeMap<>(fixedAndMovableHolidays(year));

        // 前年末・翌年初の祝日も、振替休日/国民の休日の判定(前後の日を見るため)に必要になることがあるので
        // 年境界のみ軽く補完する
        base.putAll(filterYear(fixedAndMovableHolidays(year - 1), year));
        base.putAll(filterYear(fixedAndMovableHolidays(year + 1), year));

        applyCitizensHoliday(base);
        applySubstituteHolidays(base);

        Map<LocalDate, String> result = new TreeMap<>();
        for (Map.Entry<LocalDate, String> e : base.entrySet()) {
            if (e.getKey().getYear() == year) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private static Map<LocalDate, String> filterYear(Map<LocalDate, String> map, int year) {
        Map<LocalDate, String> filtered = new TreeMap<>();
        map.forEach((d, n) -> {
            if (d.getYear() == year - 1 || d.getYear() == year + 1) filtered.put(d, n);
        });
        return filtered;
    }

    // ------------------------------------------------------------------
    // 固定日・移動祝日(振替休日・国民の休日を適用する前のベース)
    // ------------------------------------------------------------------

    private static Map<LocalDate, String> fixedAndMovableHolidays(int year) {
        Map<LocalDate, String> h = new LinkedHashMap<>();

        h.put(LocalDate.of(year, 1, 1), "元日");
        h.put(nthWeekdayOfMonth(year, 1, DayOfWeek.MONDAY, 2), "成人の日");
        h.put(LocalDate.of(year, 2, 11), "建国記念の日");
        if (year >= 2020) {
            h.put(LocalDate.of(year, 2, 23), "天皇誕生日");
        } else if (year <= 2018) {
            h.put(LocalDate.of(year, 12, 23), "天皇誕生日");
        }
        // 2019年(改元の年)は天皇誕生日が存在しない

        LocalDate shunbun = springEquinox(year);
        if (shunbun != null) h.put(shunbun, "春分の日");

        if (year >= 2007) h.put(LocalDate.of(year, 4, 29), "昭和の日");
        h.put(LocalDate.of(year, 5, 3), "憲法記念日");
        h.put(LocalDate.of(year, 5, 4), "みどりの日");
        h.put(LocalDate.of(year, 5, 5), "こどもの日");

        if (year >= 2003) {
            h.put(nthWeekdayOfMonth(year, 7, DayOfWeek.MONDAY, 3), "海の日");
        } else if (year >= 1996) {
            h.put(LocalDate.of(year, 7, 20), "海の日");
        }

        if (year >= 2016) h.put(LocalDate.of(year, 8, 11), "山の日");

        if (year >= 2003) {
            h.put(nthWeekdayOfMonth(year, 9, DayOfWeek.MONDAY, 3), "敬老の日");
        } else if (year >= 1966) {
            h.put(LocalDate.of(year, 9, 15), "敬老の日");
        }

        LocalDate shubun = autumnEquinox(year);
        if (shubun != null) h.put(shubun, "秋分の日");

        if (year >= 2020) {
            h.put(nthWeekdayOfMonth(year, 10, DayOfWeek.MONDAY, 2), "スポーツの日");
        } else if (year >= 2000) {
            h.put(nthWeekdayOfMonth(year, 10, DayOfWeek.MONDAY, 2), "体育の日");
        }

        h.put(LocalDate.of(year, 11, 3), "文化の日");
        h.put(LocalDate.of(year, 11, 23), "勤労感謝の日");

        return h;
    }

    /** 月の第n◯曜日を返す(例: 1月の第2月曜日) */
    private static LocalDate nthWeekdayOfMonth(int year, int month, DayOfWeek dow, int nth) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate firstMatch = first.with(TemporalAdjusters.firstInMonth(dow));
        return firstMatch.plusWeeks(nth - 1L);
    }

    /** 春分の日(近似式。1980〜2099年で有効) */
    private static LocalDate springEquinox(int year) {
        if (year < 1980 || year > 2099) return null;
        int day = (int) Math.floor(20.8431 + 0.242194 * (year - 1980)) - (int) Math.floor((year - 1980) / 4.0);
        return LocalDate.of(year, 3, day);
    }

    /** 秋分の日(近似式。1980〜2099年で有効) */
    private static LocalDate autumnEquinox(int year) {
        if (year < 1980 || year > 2099) return null;
        int day = (int) Math.floor(23.2488 + 0.242194 * (year - 1980)) - (int) Math.floor((year - 1980) / 4.0);
        return LocalDate.of(year, 9, day);
    }

    // ------------------------------------------------------------------
    // 国民の休日: 祝日と祝日に挟まれた「祝日でない日」を祝日とする(前後が日曜でない場合)
    // ------------------------------------------------------------------
    private static void applyCitizensHoliday(Map<LocalDate, String> holidays) {
        Map<LocalDate, String> additions = new LinkedHashMap<>();
        for (LocalDate d : holidays.keySet()) {
            LocalDate between = d.plusDays(1);
            if (holidays.containsKey(between)) continue; // 既に祝日
            if (between.getDayOfWeek() == DayOfWeek.SUNDAY) continue; // 日曜は対象外(振替休日側で処理)
            LocalDate afterBetween = between.plusDays(1);
            if (holidays.containsKey(afterBetween)) {
                additions.put(between, "国民の休日");
            }
        }
        holidays.putAll(additions);
    }

    // ------------------------------------------------------------------
    // 振替休日: 祝日が日曜日の場合、その後の最初の「祝日でない日」を休日とする
    // ------------------------------------------------------------------
    private static void applySubstituteHolidays(Map<LocalDate, String> holidays) {
        Map<LocalDate, String> additions = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, String> e : new TreeMap<>(holidays).entrySet()) {
            LocalDate d = e.getKey();
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) continue;
            LocalDate substitute = d.plusDays(1);
            while (holidays.containsKey(substitute) || additions.containsKey(substitute)) {
                substitute = substitute.plusDays(1);
            }
            additions.put(substitute, "振替休日");
        }
        holidays.putAll(additions);
    }
}
