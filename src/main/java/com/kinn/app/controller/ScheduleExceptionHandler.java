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

/** スケジュール管理API専用のエラーハンドラ。方針はAttendanceExceptionHandlerなど他のExceptionHandlerと同じ。 */
@RestControllerAdvice(assignableTypes = {ScheduleController.class, AdminScheduleController.class})
public class ScheduleExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ScheduleExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("入力内容を確認してください");
        log.warn("スケジュールAPIの入力チェックに失敗: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateParse(DateTimeParseException ex) {
        log.warn("スケジュールAPIへの日付指定が不正です: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("日付の形式が正しくありません"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("スケジュールAPIリクエストが処理できません: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody(ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("スケジュールAPIの処理中に予期しないエラーが発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("スケジュール情報の処理に失敗しました。時間をおいて再度お試しください"));
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
