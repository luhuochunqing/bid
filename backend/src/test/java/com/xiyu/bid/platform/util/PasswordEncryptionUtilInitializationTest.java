package com.xiyu.bid.platform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PasswordEncryptionUtil initialization tests")
class PasswordEncryptionUtilInitializationTest extends AbstractPasswordEncryptionUtilTest {

    @Test
    @DisplayName("Should successfully initialize when PLATFORM_ENCRYPTION_KEY is set")
    void shouldInitializeWithEnvironmentVariable() {
        System.setProperty("PLATFORM_ENCRYPTION_KEY", "test-encryption-key-32-chars-long-for-security");

        assertDoesNotThrow(() -> {
            ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey", null);
            passwordEncryptionUtil.initialize();
        });

        assertTrue(passwordEncryptionUtil.isKeyValid());
    }

    @Test
    @DisplayName("Should read from application property platform.account.encryption-key")
    void shouldReadFromApplicationProperty() {
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey",
                "test-encryption-key-from-property-32-chars");

        assertDoesNotThrow(() -> passwordEncryptionUtil.initialize());
        assertTrue(passwordEncryptionUtil.isKeyValid());
    }

    @Test
    @DisplayName("Should prioritize application property over environment variable")
    void shouldPrioritizeApplicationPropertyOverEnvironmentVariable() {
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey",
                "property-key-32-chars-long-for-security");
        System.setProperty("PLATFORM_ENCRYPTION_KEY", "env-key-32-chars-long-for-security-different");

        assertDoesNotThrow(() -> passwordEncryptionUtil.initialize());

        String encrypted = passwordEncryptionUtil.encrypt("test-password");
        assertTrue(encrypted != null && !encrypted.equals("test-password"));
    }

    @Test
    @DisplayName("Should fail initialization when PLATFORM_ENCRYPTION_KEY is missing in production")
    void shouldFailWhenEnvironmentVariableMissingInProduction() {
        MockEnvironment productionEnvironment = new MockEnvironment();
        productionEnvironment.setActiveProfiles("prod");
        ReflectionTestUtils.setField(passwordEncryptionUtil, "environment", productionEnvironment);
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey", null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> passwordEncryptionUtil.initialize()
        );

        assertTrue(exception.getMessage().contains("PLATFORM_ENCRYPTION_KEY"));
    }

    @Test
    @DisplayName("Should fail initialization when PLATFORM_ENCRYPTION_KEY is empty in production")
    void shouldFailWhenEnvironmentVariableIsEmptyInProduction() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "prod");
        System.setProperty("PLATFORM_ENCRYPTION_KEY", "   ");
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey", null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> passwordEncryptionUtil.initialize()
        );

        assertTrue(exception.getMessage().contains("PLATFORM_ENCRYPTION_KEY"));
    }

    @Test
    @DisplayName("Should use fallback key in development environment when env var is missing")
    void shouldUseFallbackInDevelopmentEnvironment() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "dev");
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey", null);

        assertDoesNotThrow(() -> passwordEncryptionUtil.initialize());
        assertTrue(passwordEncryptionUtil.isKeyValid());
    }

    @Test
    @DisplayName("Should use fallback key in test environment")
    void shouldUseFallbackInTestEnvironment() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "test");
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey", null);

        assertDoesNotThrow(() -> passwordEncryptionUtil.initialize());
        assertTrue(passwordEncryptionUtil.isKeyValid());
    }

    @Test
    @DisplayName("Should fail initialization when key is too short (< 16 characters)")
    void shouldFailWhenKeyIsTooShort() {
        System.setProperty("SPRING_PROFILES_ACTIVE", "prod");
        System.setProperty("PLATFORM_ENCRYPTION_KEY", "short");
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey", null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> passwordEncryptionUtil.initialize()
        );

        assertTrue(exception.getMessage().contains("at least 16 characters"));
    }
}
