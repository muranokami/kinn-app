package com.kinn.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kinn.app.dto.AiRawRecipeDto;
import com.kinn.app.dto.AiRawSuggestionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link AiMealClient}の実装。AI API(Anthropic Messages API)呼び出しを担当する薄いクライアント。
 * 用途ごとにAPIキー・モデル等を分けず、同じ設定を共用する(どちらも「同じAIアカウントによる
 * 文章生成」という点で本質的に同じ処理のため)。
 *
 * ・APIキーはソースコードに埋め込まず、環境変数(ANTHROPIC_API_KEY)経由でのみ受け取る
 *   (application.properties の app.ai.meal.api-key で ${ANTHROPIC_API_KEY:} として注入)。
 */
@Component
public class AnthropicMealClient implements AiMealClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicMealClient.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int maxTokens;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AnthropicMealClient(
            @Value("${app.ai.meal.api-key:}") String apiKey,
            @Value("${app.ai.meal.api-url:https://api.anthropic.com/v1/messages}") String apiUrl,
            @Value("${app.ai.meal.model:claude-sonnet-4-5}") String model,
            @Value("${app.ai.meal.max-tokens:1024}") int maxTokens,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Optional<AiRawSuggestionDto> generate(String prompt) {
        return callApi(prompt, "AI献立提案", AiRawSuggestionDto.class);
    }

    @Override
    public Optional<AiRawRecipeDto> generateRecipe(String prompt) {
        return callApi(prompt, "AIレシピ生成", AiRawRecipeDto.class);
    }

    /**
     * AI API呼び出しの共通処理。generate()/generateRecipe()の両方から使う
     * (HTTPリクエストの組み立て・送信・JSON抽出はレスポンスの型に依らず同じため)。
     *
     * 失敗時はいずれもOptional.emptyを返す(呼び出し元は献立提案ならルールベースへフォールバック、
     * レシピ生成ならエラーメッセージを表示する。⑯㉒画面を無反応にしないための既存方針)が、
     * 「なぜ失敗したか」はここで種類ごとに区別してログへ記録する(⑯非常に重要。原因調査を
     * ユーザーからの問い合わせ頼みにせず、サーバーログだけで特定できるようにするため)。
     */
    private <T> Optional<T> callApi(String prompt, String logLabel, Class<T> responseType) {
        if (!isConfigured()) {
            // ここには通常来ない(呼び出し元がisConfigured()を先にチェックする設計のため)が、
            // 直接呼ばれた場合に備えて念のため区別してログを残す
            log.warn("{}: APIキー未設定のためAI呼び出しをスキップしました(app.ai.meal.api-key / 環境変数ANTHROPIC_API_KEYを確認してください)。",
                    logLabel);
            return Optional.empty();
        }
        HttpResponse<String> response;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", new Object[]{
                            Map.of("role", "user", "content", prompt)
                    }
            );
            String requestJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.warn("{}: AI APIへの接続がタイムアウトしました(url={}, timeout=30秒): {}", logLabel, apiUrl, e.toString());
            return Optional.empty();
        } catch (IOException e) {
            // HttpTimeoutExceptionもIOExceptionのサブクラスのため、必ず上のcatchを先に書く。
            // 接続拒否・名前解決失敗・TLSエラーなど、AI APIサーバーに到達すらできなかったケース。
            log.warn("{}: AI APIとの通信中にエラーが発生しました(url={}): {}", logLabel, apiUrl, e.toString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("{}: AI APIへのリクエスト送信に失敗しました: {}", logLabel, e.toString());
            return Optional.empty();
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            log.warn("{}: AI APIの認証に失敗しました(status={})。APIキーが無効・失効している可能性があります。",
                    logLabel, response.statusCode());
            return Optional.empty();
        }
        if (response.statusCode() == 429) {
            log.warn("{}: AI APIのレート制限に達しました(status=429)。しばらく時間をおいて再試行してください。", logLabel);
            return Optional.empty();
        }
        if (response.statusCode() >= 500) {
            log.warn("{}: AI APIサーバー側でエラーが発生しました(status={})。", logLabel, response.statusCode());
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("{}: AI APIへのリクエストが拒否されました(status={}, body先頭200文字={})。",
                    logLabel, response.statusCode(), truncate(response.body(), 200));
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentArray = root.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                log.warn("{}: AI APIのレスポンスに content が含まれていませんでした。", logLabel);
                return Optional.empty();
            }
            String text = contentArray.get(0).path("text").asText("");
            return parseJson(text, logLabel, responseType);
        } catch (Exception e) {
            log.warn("{}: AI APIレスポンス本体のJSON構造解析に失敗しました: {}", logLabel, e.toString());
            return Optional.empty();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private <T> Optional<T> parseJson(String text, String logLabel, Class<T> responseType) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        // AIがコードフェンス(```json ... ```)付きで返してくることがあるため、
        // 最初の { から最後の } までのJSON本体だけを抜き出してから解析する。
        Matcher matcher = JSON_BLOCK.matcher(text);
        String jsonText = matcher.find() ? matcher.group() : text;
        try {
            return Optional.of(objectMapper.readValue(jsonText, responseType));
        } catch (Exception e) {
            log.warn("{}のレスポンスJSON解析に失敗しました: {}", logLabel, e.toString());
            return Optional.empty();
        }
    }
}
