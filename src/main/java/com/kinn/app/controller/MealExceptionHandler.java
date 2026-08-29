package com.kinn.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 * 食事管理API専用のエラーハンドラ。
 *
 * 方針: サーバーログには原因(例外の種類・スタックトレース・入力値)を必ず残す一方、
 * 画面には「〇〇に失敗しました」といった技術的でないメッセージだけを返す。
 * スタックトレースやSQL文をそのままレスポンスに含めない。
 */
@RestControllerAdvice(assignableTypes = MealController.class)
public class MealExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MealExceptionHandler.class);

    /** 入力チェック(必須項目未入力など) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("入力内容を確認してください");
        log.warn("食事記録の入力チェックに失敗: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    /** 日付の形式が不正(例: date=2026-13-40 など) */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateParse(DateTimeParseException ex) {
        log.warn("日付の形式が不正です: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("日付の形式が正しくありません"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("食事記録APIリクエストが不正: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody(ex.getReason()));
    }

    /**
     * DB制約違反(同じ日・同じ食事区分の同時登録が競合した場合など)。
     * 一意制約は同時保存のレースコンディション対策として DB 側にも設けている
     * (MealSchemaInitializer 参照)。通常操作では発生しないが、発生した場合は
     * 「もう一方の保存が先に反映された」ことを示すので、再読み込みを促す。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("食事記録の保存でDB制約違反が発生しました", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("同じ食事が別の操作で既に登録・更新されています。画面を再読み込みしてください"));
    }

    /** それ以外の想定外エラー。原因はログにのみ出力する */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("食事記録の処理中に予期しないエラーが発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("食事記録の処理に失敗しました。時間をおいて再度お試しください"));
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
