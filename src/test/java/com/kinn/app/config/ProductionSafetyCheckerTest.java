package com.kinn.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本番プロファイルでの安全確認(セキュリティレビュー④対応)の単体テスト。
 * DB・Springコンテキストは使わず、Environmentはモックで差し替える
 * (HealthDataEncryptorTest等、他のユニットテストと同じ方針)。
 */
class ProductionSafetyCheckerTest {

    @Test
    void devProfile_defaultsAreAllowed() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ProductionSafetyChecker checker = new ProductionSafetyChecker(env, "postgres", "");

        assertDoesNotThrow(() -> checker.run());
    }

    @Test
    void noProfile_defaultsAreAllowed() {
        MockEnvironment env = new MockEnvironment();
        ProductionSafetyChecker checker = new ProductionSafetyChecker(env, "postgres", "");

        assertDoesNotThrow(() -> checker.run());
    }

    @Test
    void prodProfile_defaultDbPasswordAndMissingEncryptionKey_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyChecker checker = new ProductionSafetyChecker(env, "postgres", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, checker::run);
        assertHasMessageContaining(ex, "DB_PASSWORD");
        assertHasMessageContaining(ex, "APP_HEALTH_ENCRYPTION_KEY");
    }

    @Test
    void prodProfile_onlyEncryptionKeyMissing_throwsWithThatReasonOnly() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyChecker checker = new ProductionSafetyChecker(env, "s3cur3-real-password", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class, checker::run);
        assertHasMessageContaining(ex, "APP_HEALTH_ENCRYPTION_KEY");
        assertFalse(ex.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void prodProfile_bothOverridden_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyChecker checker = new ProductionSafetyChecker(
                env, "s3cur3-real-password", "kJ3n9s0m7Vb2xQeYcRt4uWzApL8dFhTgN1oIiZ6ySfM=");

        assertDoesNotThrow(() -> checker.run());
    }

    private void assertHasMessageContaining(Exception e, String expected) {
        assertTrue(e.getMessage().contains(expected),
                () -> "メッセージに \"" + expected + "\" が含まれていません: " + e.getMessage());
    }
}
