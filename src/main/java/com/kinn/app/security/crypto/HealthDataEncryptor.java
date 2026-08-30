package com.kinn.app.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 健康管理データ(体重・血圧・睡眠時間・メモ等の要配慮個人情報)をDBへ保存する前に
 * 暗号化するための共通コンポーネント(セキュリティレビュー③対応)。
 *
 * AES-256-GCMを使用する。鍵は環境変数 {@code APP_HEALTH_ENCRYPTION_KEY}
 * (Base64エンコードされた32バイト。例: {@code openssl rand -base64 32} で生成)から取得する。
 * 未設定の場合はこのクラス内蔵の固定キー(ローカル開発専用)にフォールバックし、起動のたびに
 * 警告ログを出す。本番・共有環境では必ず環境変数を設定すること
 * (DB_PASSWORD・ANTHROPIC_API_KEYと同じ「環境変数から注入し、コードには直書きしない」方針)。
 *
 * 暗号文の先頭には固定プレフィックス{@link #ENC_PREFIX}を付与する。これにより:
 * <ul>
 *   <li>復号を試みずに「暗号化済みかどうか」を判定できる
 *       ({@link #isEncrypted(String)}。HealthDataEncryptionMigrationRunnerが
 *       移行済み/未移行を見分けるのに使う)</li>
 *   <li>将来アルゴリズム・バージョンを変える場合の見分けにも使える</li>
 * </ul>
 */
@Component
public class HealthDataEncryptor {

    private static final Logger log = LoggerFactory.getLogger(HealthDataEncryptor.class);

    /** 暗号化済みであることの目印。このプレフィックスが無い値は移行前の平文とみなす */
    static final String ENC_PREFIX = "enc1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // ローカル開発でAPP_HEALTH_ENCRYPTION_KEYが未設定でもすぐに動かせるようにするための
    // 固定フォールバックキー(Base64で32バイト=AES-256)。このリポジトリを見れば誰でも
    // 分かる値のため、本番・共有環境では絶対に使わないこと(未設定のまま起動すると
    // 下のwarnログが毎回出る)。
    private static final String INSECURE_DEFAULT_KEY_BASE64 = "kJ3n9s0m7Vb2xQeYcRt4uWzApL8dFhTgN1oIiZ6ySfM=";

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public HealthDataEncryptor(@Value("${app.security.health-encryption.key:}") String base64Key) {
        boolean usingDefault = base64Key == null || base64Key.isBlank();
        String effectiveKey = usingDefault ? INSECURE_DEFAULT_KEY_BASE64 : base64Key.trim();

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(effectiveKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "APP_HEALTH_ENCRYPTION_KEY はBase64エンコードされた値を指定してください", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "APP_HEALTH_ENCRYPTION_KEY はデコード後32バイト(AES-256)である必要があります(実際: "
                            + keyBytes.length + "バイト)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");

        if (usingDefault) {
            log.warn("APP_HEALTH_ENCRYPTION_KEY が未設定のため、健康管理データの暗号化にローカル開発専用の"
                    + "固定キーを使用しています。本番・共有環境では必ず環境変数 APP_HEALTH_ENCRYPTION_KEY に"
                    + "十分な強度の鍵(例: openssl rand -base64 32 で生成したBase64文字列)を設定してください。"
                    + "設定しないまま運用すると、このリポジトリを閲覧できる誰もが健康データを復号できてしまいます。");
        }
    }

    /** 平文をENC_PREFIX付きのBase64文字列へ暗号化する。nullはnullのまま返す */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(ivAndCipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("健康管理データの暗号化に失敗しました", e);
        }
    }

    /**
     * ENC_PREFIX付きの暗号文を復号する。ENC_PREFIXが無い値(移行前の平文データ)はそのまま返す
     * (HealthDataEncryptionMigrationRunnerが移行し終えるまでの間、既存データを読めなくして
     * アプリを止めてしまわないための後方互換措置。移行完了後は実質発生しない)。
     * nullはnullのまま返す。
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(ENC_PREFIX)) {
            return stored; // 移行前の平文データ
        }
        try {
            byte[] ivAndCipherText = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCipherText, 0, iv, 0, iv.length);
            byte[] cipherText = new byte[ivAndCipherText.length - iv.length];
            System.arraycopy(ivAndCipherText, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("健康管理データの復号に失敗しました(鍵が変わった可能性があります)", e);
        }
    }

    /** 移行ランナー・Converterが「既に暗号化済みか」を復号を試みずに判定するために使う */
    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(ENC_PREFIX);
    }
}
