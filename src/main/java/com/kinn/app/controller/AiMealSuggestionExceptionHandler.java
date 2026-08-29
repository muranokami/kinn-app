package com.kinn.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI献立提案API専用のエラーハンドラ。
 *
 * 方針はMealExceptionHandlerと同じ: サーバーログには原因(例外の種類・スタックトレース)を
 * 必ず残す一方、画面には技術的でないメッセージだけを返す。AI APIキーやレスポンス本文などの
 * 秘密情報・内部情報はレスポンスに一切含めない。
 */
@RestControllerAdvice(assignableTypes = AiMealSuggestionController.class)
public class AiMealSuggestionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiMealSuggestionExceptionHandler.class);

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, String>> handleDateParse(DateTimeParseException ex) {
        log.warn("AI献立提案APIへの日付指定が不正です: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("日付の形式が正しくありません"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("AI献立提案APIリクエストが処理できません: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody(ex.getReason()));
    }

    /**
     * それ以外の想定外エラー(AI献立生成中の例外、DB例外など)。
     * AiMealClient自体はAI API呼び出し失敗を握りつぶさずルールベースにフォールバックするため、
     * ここに到達するのは「フォールバック含めた生成処理そのものが失敗した」ケース。
     * 原因はログにのみ出力し、画面には要求仕様どおりの文言のみ返す。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("AI献立提案の処理中に予期しないエラーが発生しました", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("献立の再提案に失敗しました。もう一度お試しください。"));
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }
}
