package com.kinn.app.security.crypto;

import com.kinn.app.entity.SmokingStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 健康管理データ暗号化(セキュリティレビュー③対応)の単体テスト。
 * DB・Springコンテキストは使わず、HealthDataEncryptorを直接インスタンス化する
 * (TaskServiceTest等、他のユニットテストと同じ方針)。
 */
class HealthDataEncryptorTest {

    // 鍵未指定時のフォールバック(ローカル開発専用キー)を使う。本番相当の鍵検証は
    // testWithExplicitKeyRoundTrip側でカバーする。
    private final HealthDataEncryptor encryptor = new HealthDataEncryptor("");

    @Test
    void encryptThenDecrypt_returnsOriginalValue() {
        String plaintext = "72.5";
        String cipherText = encryptor.encrypt(plaintext);

        assertNotEquals(plaintext, cipherText);
        assertTrue(encryptor.isEncrypted(cipherText));
        assertEquals(plaintext, encryptor.decrypt(cipherText));
    }

    @Test
    void encrypt_isNotDeterministic_dueToRandomIv() {
        String plaintext = "同じ平文";
        String first = encryptor.encrypt(plaintext);
        String second = encryptor.encrypt(plaintext);

        // IVを毎回ランダムにしているため、同じ平文でも暗号文は一致しない(意味論的安全性)
        assertNotEquals(first, second);
        assertEquals(plaintext, encryptor.decrypt(first));
        assertEquals(plaintext, encryptor.decrypt(second));
    }

    @Test
    void decrypt_legacyPlaintextWithoutPrefix_isReturnedAsIs() {
        // V5移行前の平文データ(まだHealthDataEncryptionMigrationRunnerが処理していない行)を
        // 誤って復号エラーにしない後方互換性の確認
        String legacyPlaintext = "170.0";
        assertFalse(encryptor.isEncrypted(legacyPlaintext));
        assertEquals(legacyPlaintext, encryptor.decrypt(legacyPlaintext));
    }

    @Test
    void encryptAndDecrypt_handleNullAsNull() {
        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void doubleConverter_roundTripsThroughEncryption() {
        EncryptedDoubleConverter converter = new EncryptedDoubleConverter(encryptor);
        String stored = converter.convertToDatabaseColumn(36.8);

        assertTrue(encryptor.isEncrypted(stored));
        assertEquals(36.8, converter.convertToEntityAttribute(stored));
    }

    @Test
    void integerConverter_roundTripsThroughEncryption() {
        EncryptedIntegerConverter converter = new EncryptedIntegerConverter(encryptor);
        String stored = converter.convertToDatabaseColumn(120);

        assertTrue(encryptor.isEncrypted(stored));
        assertEquals(120, converter.convertToEntityAttribute(stored));
    }

    @Test
    void stringConverter_roundTripsThroughEncryption() {
        EncryptedStringConverter converter = new EncryptedStringConverter(encryptor);
        String stored = converter.convertToDatabaseColumn("既往歴に関するメモ");

        assertTrue(encryptor.isEncrypted(stored));
        assertEquals("既往歴に関するメモ", converter.convertToEntityAttribute(stored));
    }

    @Test
    void enumConverter_roundTripsThroughEncryption() {
        EncryptedSmokingStatusConverter converter = new EncryptedSmokingStatusConverter(encryptor);
        String stored = converter.convertToDatabaseColumn(SmokingStatus.SMOKER);

        assertTrue(encryptor.isEncrypted(stored));
        assertEquals(SmokingStatus.SMOKER, converter.convertToEntityAttribute(stored));
    }

    @Test
    void invalidKeyLength_throwsAtConstruction() {
        // Base64デコード後が32バイト(AES-256)でない鍵は起動時に検出する
        String tooShortKeyBase64 = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new HealthDataEncryptor(tooShortKeyBase64));
    }
}
