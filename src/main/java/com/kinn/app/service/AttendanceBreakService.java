package com.kinn.app.service;

import com.kinn.app.dto.BreakStatus;
import com.kinn.app.dto.BreakStatusDto;
import com.kinn.app.entity.AttendanceRecord;
import com.kinn.app.repository.AttendanceRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * トップページの自動休憩機能(①〜⑥⑪〜⑮⑳)を扱うサービス。
 *
 * 既存の勤怠DB構造(AttendanceRecord)をそのまま拡張して使う(⑦。新しいテーブルは作らない)。
 * 「休憩開始/終了」は当日のAttendanceRecordのbreak_start_time/break_end_time列に記録し、
 * 休憩終了が確定するたびに、この機能専用の集計列 auto_break_minutes と、既存の
 * break_minutes列(実働時間計算が既に参照している合計値)の両方へその回の休憩時間を積み増す。
 *
 * ⑯ 手動入力との非競合(重要): 1日の休憩予算(既定60分)を使い切ったかどうかの判定は、
 * 必ず auto_break_minutes(この機能だけで消費した分)を基準に行い、break_minutes
 * (勤怠画面での手動入力を含む合計)は基準にしない。break_minutesだけを見て判定すると、
 * 勤怠画面であらかじめ昼休憩などを手入力していたユーザーが、この機能を一度も使っていないのに
 * 「本日の休憩は使い切りました」と判定され、[休憩開始]ボタンが出せなくなってしまう
 * (実際にこの不具合が発生していたため、集計列を分離した)。
 * break_minutes自体は既存の実働時間計算(AttendanceService#computeWorkedMinutes)への
 * 反映のため、手動入力分にこの機能で取った分をそのまま積み増す形で維持する(⑮)。
 *
 * 休憩は1日に1回とは限らない(利用者からのフィードバックにより変更): 1日の休憩予算を、
 * 複数回に分けて("刻んで")消費してよい。break_start_time/break_end_timeは常に
 * 「直近(または進行中)の1回分」を表す。ある回の休憩を開始した時点で残っている予算
 * (breakDurationMinutes − その時点のauto_break_minutes)が、その回に使える上限になる
 * (必ずしも毎回満額のタイマーになるわけではない)。これにより「1日の合計は必ず上限まで」
 * という保証を保ちながら、何回でも分割できる。
 *
 * 出勤時刻の入力は前提にしない: 当日の勤怠レコードが無い状態でも[休憩開始]を押せば、
 * その場でレコードを作成して休憩開始時刻を記録する。出勤時刻(start_time)はここでは
 * 一切設定しない(休憩と出退勤は別々の入力のまま共存する)。
 *
 * サーバー側の権限(⑧㉒): このサービスのメソッドはすべてemployeeId(Authentication#getName()
 * 由来。呼び出し元のControllerで解決済み)だけを受け取り、クライアントからuserIdやdate等の
 * 対象指定は一切受け取らない。操作対象は常に「ログインユーザー本人の当日分」に固定する。
 *
 * サーバー権威のタイマー(⑪⑫⑭): 休憩の残り時間・終了判定はすべてサーバー側の現在時刻と
 * DBに保存された休憩開始時刻から計算する。ブラウザのタイマーはあくまで見た目の表示用であり、
 * その回の残り予算を使い切ったことの確定(休憩終了・勤怠反映)は必ずこのサービスが行う。
 */
@Service
public class AttendanceBreakService {

    private final AttendanceRecordRepository repository;
    private final AttendanceService attendanceService;
    private final int breakDurationMinutes;

    public AttendanceBreakService(AttendanceRecordRepository repository,
                                   AttendanceService attendanceService,
                                   @Value("${app.attendance.break-minutes:60}") int breakDurationMinutes) {
        this.repository = repository;
        this.attendanceService = attendanceService;
        this.breakDurationMinutes = breakDurationMinutes;
    }

    /** 現在の休憩状態を取得する。進行中の休憩がその回の残り予算を使い切っていれば、ここで自動的に終了処理を行う(⑭⑲) */
    @Transactional
    public BreakStatusDto status(String employeeId) {
        LocalDate today = LocalDate.now();
        AttendanceRecord record = repository.findByEmployeeIdAndWorkDate(employeeId, today).orElse(null);
        autoFinishIfExpired(record);
        return toDto(record, null);
    }

    /**
     * 休憩を開始する(②)。出勤時刻の入力は不要。休憩は1日に何度でも分けて開始できるが、
     * この機能での本日の休憩予算(既定60分)を使い切っている場合、既に退勤している場合、
     * 既に休憩中の場合は拒否する(⑬二重休憩防止)。勤怠画面での手動入力(⑯)の有無は判定に含めない。
     */
    @Transactional
    public BreakStatusDto start(String employeeId) {
        LocalDate today = LocalDate.now();
        AttendanceRecord record = repository.findByEmployeeIdAndWorkDate(employeeId, today)
                .orElseGet(() -> AttendanceRecord.builder()
                        .employeeId(employeeId)
                        .workDate(today)
                        .dayType(attendanceService.resolveDefaultDayType(today))
                        .breakMinutes(0)
                        .autoBreakMinutes(0)
                        .build());

        autoFinishIfExpired(record);

        if (record.getEndTime() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "既に退勤しています。");
        }
        if (record.getBreakStartTime() != null && record.getBreakEndTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "現在すでに休憩中です。");
        }
        int usedAutoMinutes = autoMinutes(record);
        if (usedAutoMinutes >= breakDurationMinutes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "本日の休憩時間(" + breakDurationMinutes + "分)は既に使い切りました。");
        }

        // 秒未満(ナノ秒)まで保存すると、一部環境のPostgreSQL JDBCドライバ+Hibernateの
        // 組み合わせで読み戻し時に "Invalid value for NanoOfSecond" 例外が発生することがある
        // (休憩の秒未満精度はどのみち使っていないため、保存前に秒単位へ切り捨てる)。
        record.setBreakStartTime(LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
        record.setBreakEndTime(null);
        repository.save(record);
        return toDto(record, null);
    }

    /** 休憩を終了する(④。その回の残り予算を使い切るのを待たずに早めに戻った場合の手動終了にも使う) */
    @Transactional
    public BreakStatusDto end(String employeeId) {
        LocalDate today = LocalDate.now();
        AttendanceRecord record = repository.findByEmployeeIdAndWorkDate(employeeId, today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "休憩中ではありません。"));

        if (record.getBreakStartTime() == null || record.getBreakEndTime() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "休憩中ではありません。");
        }

        // start側と同じ理由で秒単位に切り捨てる
        finishBreak(record, LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
        repository.save(record);

        int remaining = Math.max(0, breakDurationMinutes - autoMinutes(record));
        String message = remaining > 0
                ? "休憩を終了しました。本日はあと" + remaining + "分、休憩を取ることができます。"
                : "休憩を終了しました。本日の休憩時間(" + breakDurationMinutes + "分)を使い切りました。";
        return toDto(record, message);
    }

    /**
     * 進行中の休憩が、その回に使える残り予算(開始時点でのbreakDurationMinutes − この機能での
     * 使用済み分)を経過していれば、ここでサーバー側の時刻を基準に終了処理を行う(⑫⑭
     * 「ブラウザを閉じている間に経過していた場合」も含む)。
     */
    private void autoFinishIfExpired(AttendanceRecord record) {
        if (record == null) return;
        if (record.getBreakStartTime() == null || record.getBreakEndTime() != null) return;

        // 休憩開始時点で残っていた予算(=このセグメントに使える上限)。開始後もauto_break_minutesは
        // このセグメント分をまだ加算していないため、現在値がそのまま「開始時点の使用済み分」になる
        int remainingBudgetForSegment = Math.max(0, breakDurationMinutes - autoMinutes(record));

        LocalDateTime startDateTime = LocalDateTime.of(record.getWorkDate(), record.getBreakStartTime());
        long elapsedSeconds = Duration.between(startDateTime, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds >= remainingBudgetForSegment * 60L) {
            // 実際の「今」ではなく、開始+残り予算ちょうどを終了時刻とする(休憩時間を常に
            // 予算どおりの長さで確定させるため。放置していた時間まで休憩扱いにはしない)
            LocalTime scheduledEnd = record.getBreakStartTime().plusMinutes(remainingBudgetForSegment);
            finishBreak(record, scheduledEnd);
            repository.save(record);
        }
    }

    private void finishBreak(AttendanceRecord record, LocalTime endTime) {
        record.setBreakEndTime(endTime);
        int segmentMinutes = (int) (Duration.between(record.getBreakStartTime(), endTime).getSeconds() / 60);
        if (segmentMinutes < 0) segmentMinutes += 24 * 60; // 日をまたぐ場合の保険(通常は発生しない)

        // この機能専用の集計(⑬二重休憩防止・予算判定にのみ使う。上限を超えない)
        int newAutoTotal = Math.min(breakDurationMinutes, autoMinutes(record) + segmentMinutes);
        record.setAutoBreakMinutes(newAutoTotal);

        // ⑮ 既存の実働時間計算がそのまま参照する合計列には、手動入力分を壊さずそのまま積み増す
        // (この機能の60分という上限とは無関係。実際に取った休憩時間として反映するため)
        int existingTotal = record.getBreakMinutes() == null ? 0 : record.getBreakMinutes();
        record.setBreakMinutes(existingTotal + segmentMinutes);
    }

    private int autoMinutes(AttendanceRecord record) {
        return record.getAutoBreakMinutes() == null ? 0 : record.getAutoBreakMinutes();
    }

    /**
     * @param record nullの場合(その日の勤怠レコードが未作成)も「休憩を開始できる」状態として扱う
     *               (出勤時刻の入力を前提にしないため)。
     */
    private BreakStatusDto toDto(AttendanceRecord record, String message) {
        LocalDate today = LocalDate.now();
        int usedMinutes = record == null ? 0 : autoMinutes(record);
        int remainingBudget = Math.max(0, breakDurationMinutes - usedMinutes);

        if (record != null && record.getEndTime() != null) {
            return BreakStatusDto.builder()
                    .status(BreakStatus.CLOCKED_OUT)
                    .workDate(today)
                    .breakStartTime(record.getBreakStartTime())
                    .breakEndTime(record.getBreakEndTime())
                    .breakDurationMinutes(breakDurationMinutes)
                    .remainingSeconds(0)
                    .usedMinutesToday(usedMinutes)
                    .remainingBudgetMinutes(remainingBudget)
                    .build();
        }
        if (record != null && record.getBreakStartTime() != null && record.getBreakEndTime() == null) {
            LocalDateTime startDateTime = LocalDateTime.of(today, record.getBreakStartTime());
            long elapsedSeconds = Duration.between(startDateTime, LocalDateTime.now()).getSeconds();
            long segmentRemaining = Math.max(0, remainingBudget * 60L - elapsedSeconds);
            return BreakStatusDto.builder()
                    .status(BreakStatus.ON_BREAK)
                    .workDate(today)
                    .breakStartTime(record.getBreakStartTime())
                    .scheduledEndTime(record.getBreakStartTime().plusMinutes(remainingBudget))
                    .breakDurationMinutes(breakDurationMinutes)
                    .remainingSeconds((int) segmentRemaining)
                    .usedMinutesToday(usedMinutes)
                    .remainingBudgetMinutes(remainingBudget)
                    .build();
        }
        if (usedMinutes >= breakDurationMinutes) {
            return BreakStatusDto.builder()
                    .status(BreakStatus.BREAK_EXHAUSTED)
                    .workDate(today)
                    .breakStartTime(record.getBreakStartTime())
                    .breakEndTime(record.getBreakEndTime())
                    .breakDurationMinutes(breakDurationMinutes)
                    .remainingSeconds(0)
                    .actualBreakMinutes(usedMinutes)
                    .usedMinutesToday(usedMinutes)
                    .remainingBudgetMinutes(0)
                    .message(message)
                    .build();
        }
        // 休憩は始まっていない(または前回分が終わっている)が、本日の予算はまだ残っている
        // ⇒ 何度でも休憩を開始できる
        return BreakStatusDto.builder()
                .status(BreakStatus.WORKING)
                .workDate(today)
                .breakStartTime(record == null ? null : record.getBreakStartTime())
                .breakEndTime(record == null ? null : record.getBreakEndTime())
                .breakDurationMinutes(breakDurationMinutes)
                .remainingSeconds(0)
                .actualBreakMinutes(usedMinutes)
                .usedMinutesToday(usedMinutes)
                .remainingBudgetMinutes(remainingBudget)
                .message(message)
                .build();
    }
}
