package com.kinn.app.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IPアドレス単位の簡易レート制限(固定ウィンドウ方式)。ログイン・新規登録APIを対象に、
 * 短時間の大量リクエスト(ブルートフォース・アカウント列挙)を抑制する。
 *
 * Bucket4j等の専用ライブラリの導入も検討したが、今回は単一インスタンス運用を前提に
 * 追加の依存ライブラリなしで実装した(将来、複数インスタンスでの運用やより厳密な
 * アルゴリズムが必要になった場合は、この {@link #tryAcquire} の実装だけを
 * Bucket4j/Redis等に差し替えれば良い構造にしている。呼び出し側=AuthControllerは
 * 「バケット名・上限・ウィンドウ秒」だけを知っていればよく、アルゴリズムの詳細に依存しない)。
 *
 * ログイン試行制限(アカウント単位のロック。LoginAttemptListener参照)とは目的が異なり、
 * こちらはアカウントの存在有無に関係なく「同一IPからの短時間の大量アクセス」を抑える
 * (存在しない会社名・ユーザーIDへの総当たりもここで抑制できる)。
 */
@Component
public class RateLimiter {

    /** バケット名+IPごとの「ウィンドウ開始時刻」と「そのウィンドウ内の回数」 */
    private record Window(AtomicLong windowStartEpochSeconds, AtomicLong count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param bucket        制限の種類を表すキー(例: "login", "register")
     * @param clientKey     IPアドレスなど、制限対象を識別するキー
     * @param maxRequests   ウィンドウ内の最大リクエスト数
     * @param windowSeconds ウィンドウの長さ(秒)
     * @return trueなら許可、falseなら制限超過
     */
    public boolean tryAcquire(String bucket, String clientKey, int maxRequests, long windowSeconds) {
        String key = bucket + "|" + clientKey;
        long now = Instant.now().getEpochSecond();

        Window window = windows.computeIfAbsent(key,
                k -> new Window(new AtomicLong(now), new AtomicLong(0)));

        synchronized (window) {
            long windowStart = window.windowStartEpochSeconds().get();
            if (now - windowStart >= windowSeconds) {
                // ウィンドウが経過していたらリセットする
                window.windowStartEpochSeconds().set(now);
                window.count().set(0);
            }
            long current = window.count().incrementAndGet();
            return current <= maxRequests;
        }
    }
}
