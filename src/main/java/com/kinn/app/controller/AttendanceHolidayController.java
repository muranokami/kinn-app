package com.kinn.app.controller;

import com.kinn.app.dto.HolidayDto;
import com.kinn.app.dto.HolidayOverrideDto;
import com.kinn.app.service.HolidayCalendarService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 祝日カレンダーAPI。
 * 勤怠本体(AttendanceController)とは責務を分けている(祝日カレンダーは
 * employeeIdに紐づかない、会社/アプリ全体で共通の参照データのため)。
 *
 * 注意: 以前ここにあった「会社休日ルール(土曜日・日曜日のデフォルト区分を変更する設定)」
 * APIは、休日ルール設定画面の廃止にともない削除した。休日区分のデフォルトは
 * AttendanceService に固定値(土曜日=所定休日, 日曜日=法定休日)として一本化している。
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceHolidayController {

    private final HolidayCalendarService holidayCalendarService;

    public AttendanceHolidayController(HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    /** 指定年の日本の祝日一覧(カレンダー表示用。振替休日・国民の休日・手動オーバーライドを反映済み) */
    @GetMapping("/holidays/{year}")
    public List<HolidayDto> getHolidays(@PathVariable int year) {
        return holidayCalendarService.getHolidaysOfYear(year);
    }

    /** 祝日カレンダーの手動オーバーライド一覧(将来の法改正等への対応用) */
    @GetMapping("/holiday-overrides")
    public List<HolidayOverrideDto> getHolidayOverrides() {
        return holidayCalendarService.getOverrides();
    }

    /** 祝日オーバーライドの追加・更新(同じ日付が既にあれば上書き) */
    @PostMapping("/holiday-overrides")
    public HolidayOverrideDto saveHolidayOverride(@Valid @RequestBody HolidayOverrideDto dto) {
        return holidayCalendarService.saveOverride(dto);
    }

    @DeleteMapping("/holiday-overrides/{id}")
    public void deleteHolidayOverride(@PathVariable Long id) {
        holidayCalendarService.deleteOverride(id);
    }
}
