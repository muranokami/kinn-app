package com.kinn.app.controller;

import com.kinn.app.dto.AttendancePeriodDto;
import com.kinn.app.dto.AttendanceRecordDto;
import com.kinn.app.dto.BreakStatusDto;
import com.kinn.app.dto.MonthAttendanceDto;
import com.kinn.app.service.AttendanceBreakService;
import com.kinn.app.service.AttendancePeriodResolver;
import com.kinn.app.service.AttendanceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 勤怠管理API。ログイン中のユーザー(Authentication#getName() = 実効employeeId
 * "companyId|loginId")のデータのみを参照・更新する。クライアントから employeeId を
 * 受け取る方式は廃止した(URLやパラメータを書き換えて他ユーザーのデータへアクセス
 * できないようにするため)。
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceBreakService attendanceBreakService;

    public AttendanceController(AttendanceService attendanceService, AttendanceBreakService attendanceBreakService) {
        this.attendanceService = attendanceService;
        this.attendanceBreakService = attendanceBreakService;
    }

    /** 指定した年月の日次データ+集計を取得 */
    @GetMapping("/{year}/{month}")
    public MonthAttendanceDto getMonth(
            @PathVariable int year,
            @PathVariable int month,
            Authentication authentication) {
        return attendanceService.getMonth(authentication.getName(), year, month);
    }

    /** 指定した年月の日次データをまとめて保存(登録・更新) */
    @PostMapping("/{year}/{month}")
    public MonthAttendanceDto saveMonth(
            @PathVariable int year,
            @PathVariable int month,
            @RequestBody List<AttendanceRecordDto> days,
            Authentication authentication) {
        return attendanceService.saveMonth(authentication.getName(), year, month, days);
    }

    /** 1日分だけ保存 */
    @PutMapping("/day")
    public AttendanceRecordDto saveDay(
            @RequestBody AttendanceRecordDto dto,
            Authentication authentication) {
        return attendanceService.saveDay(authentication.getName(), dto);
    }

    /** 1日分を削除(未入力状態に戻す) */
    @DeleteMapping("/day/{date}")
    public void deleteDay(
            @PathVariable String date,
            Authentication authentication) {
        attendanceService.deleteDay(authentication.getName(), LocalDate.parse(date));
    }

    // ------------------------------------------------------------------
    // 締め日(カレンダー開始日)を指定した任意期間の勤怠(⑦)。
    // startDate/endDateを指定すればその期間をそのまま使い、指定がなければ
    // closingDay(締め日1〜31。省略時は1日=暦月)とbaseDate(基準日。省略時は本日)から
    // その基準日を含む期間を計算する。期間計算はAttendancePeriodResolverに一元化しており、
    // 文字列操作ではなくLocalDate/YearMonthのみで行う。
    // ------------------------------------------------------------------

    /** 指定期間(または締め日+基準日から計算した期間)の日次データ+集計を取得 */
    @GetMapping("/period")
    public AttendancePeriodDto getPeriod(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer closingDay,
            @RequestParam(required = false) String baseDate,
            Authentication authentication) {
        LocalDate[] range = AttendancePeriodResolver.resolveRange(startDate, endDate, closingDay, baseDate);
        return attendanceService.getPeriod(authentication.getName(), range[0], range[1], closingDay);
    }

    /** 指定期間の日次データをまとめて保存(登録・更新)。暦月をまたぐ期間にも対応する */
    @PostMapping("/period")
    public AttendancePeriodDto savePeriod(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestBody List<AttendanceRecordDto> days,
            Authentication authentication) {
        LocalDate from = LocalDate.parse(startDate);
        LocalDate to = LocalDate.parse(endDate);
        return attendanceService.savePeriod(authentication.getName(), from, to, days);
    }

    /** 指定期間の勤怠明細をCSVでダウンロード(downloadCsvの期間版) */
    @GetMapping("/period/csv")
    public ResponseEntity<byte[]> downloadPeriodCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer closingDay,
            @RequestParam(required = false) String baseDate,
            Authentication authentication) throws IOException {
        LocalDate[] range = AttendancePeriodResolver.resolveRange(startDate, endDate, closingDay, baseDate);
        String employeeId = authentication.getName();
        String csv = attendanceService.buildPeriodCsv(employeeId, range[0], range[1]);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF); out.write(0xBB); out.write(0xBF);
        out.write(csv.getBytes(StandardCharsets.UTF_8));

        String filename = String.format("attendance_%s_%s.csv", range[0], range[1]);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(out.toByteArray());
    }

    /** 指定月の勤怠明細をCSVでダウンロード(勤務日区分・祝日名・休日勤務時間などを含む) */
    @GetMapping("/{year}/{month}/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @PathVariable int year,
            @PathVariable int month,
            Authentication authentication) throws IOException {
        String employeeId = authentication.getName();
        String csv = attendanceService.buildMonthlyCsv(employeeId, year, month);

        // Excelでの文字化けを避けるためUTF-8 BOM付きで出力する
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF); out.write(0xBB); out.write(0xBF);
        out.write(csv.getBytes(StandardCharsets.UTF_8));

        String filename = String.format("attendance_%04d%02d.csv", year, month);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(out.toByteArray());
    }

    // ------------------------------------------------------------------
    // トップページの自動休憩機能(①〜⑥⑪〜⑮⑳)。他のエンドポイントと同じく
    // Authentication#getName()(実効employeeId)のみを対象にし、userIdなどをクライアントから
    // 受け取らない(⑧㉒。他ユーザーの休憩をURL改ざん等で操作できないようにするため)。
    // ------------------------------------------------------------------

    /** 現在の休憩状態を取得する(⑪⑫⑲。ページ読み込み時・タイマー終了時に呼ぶ) */
    @GetMapping("/break/status")
    public BreakStatusDto getBreakStatus(Authentication authentication) {
        return attendanceBreakService.status(authentication.getName());
    }

    /** 休憩を開始する(②) */
    @PostMapping("/break/start")
    public BreakStatusDto startBreak(Authentication authentication) {
        return attendanceBreakService.start(authentication.getName());
    }

    /** 休憩を終了する(④。60分経過を待たずに早めに戻った場合の手動終了) */
    @PostMapping("/break/end")
    public BreakStatusDto endBreak(Authentication authentication) {
        return attendanceBreakService.end(authentication.getName());
    }
}
