package com.kinn.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 勤怠・祝日カレンダー・管理者(勤怠/従業員/部署)API専用のエラーハンドラ。方針は他のExceptionHandlerと同じ。 */
@RestControllerAdvice(assignableTypes = {
        AttendanceController.class, AttendanceHolidayController.class,
        AdminAttendanceController.class, AdminEmployeeController.class, AdminDepartmentController.class})
public class AttendanceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("入力内容を確認してください");
        log.warn("勤怠APIの入力チェックに失敗: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateParse(DateTimeParseException ex) {
        log.warn("勤怠APIへの日付指定が不正です: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("日付の形式が正しくありません"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("勤怠APIリクエストが処理できません: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody(ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("勤怠APIの処理中に予期しないエラーが発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("勤怠情報の処理に失敗しました。時間をおいて再度お試しください"));
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
