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

/**
 * 残業申請API専用のエラーハンドラ(画面が無反応にならないよう、必ずJSONでメッセージを返す)。
 * 方針はAnnouncementExceptionHandlerなど他のExceptionHandlerと同じ。
 */
@RestControllerAdvice(assignableTypes = {OvertimeRequestController.class, AdminOvertimeRequestController.class})
public class OvertimeRequestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OvertimeRequestExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("入力内容を確認してください");
        log.warn("残業申請APIの入力チェックに失敗: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateParse(DateTimeParseException ex) {
        log.warn("残業申請APIへの日時指定が不正です: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("日時の形式が正しくありません"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        // 存在しないステータス文字列など、Enum変換失敗時にSpringが投げる例外もここで拾う
        log.warn("残業申請APIへの入力値が不正です: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("入力内容を確認してください"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("残業申請APIリクエストが処理できません: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody(ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("残業申請APIの処理中に予期しないエラーが発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("残業申請情報の処理に失敗しました。時間をおいて再度お試しください"));
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
