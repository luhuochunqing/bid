package com.xiyu.bid.platform.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for PasswordEncryptionUtil with Spring context.
 * This test verifies the bean works correctly in a real Spring environment.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PasswordEncryptionUtil Integration Test")
class PasswordEncryptionUtilIntegrationTest {

    @Autowired(required = false)
    private PasswordEncryptionUtil passwordEncryptionUtil;

    @Test
    @DisplayName("Should initialize correctly in test environment")
    void shouldInitializeInTestEnvironment() {
        // Assert
        assertNotNull(passwordEncryptionUtil,
            "PasswordEncryptionUtil should be autowired in test environment");
        assertTrue(passwordEncryptionUtil.isKeyValid(),
            "Key should be valid in test environment (using fallback)");
    }

    @Test
    @DisplayName("Should encrypt and decrypt passwords correctly")
    void shouldEncryptDecrypt() {
        // Arrange
        String originalPassword = "TestPassword123!@#";

        // Act
        String encrypted = passwordEncryptionUtil.encrypt(originalPassword);
        String decrypted = passwordEncryptionUtil.decrypt(encrypted);

        // Assert
        assertNotNull(encrypted);
        assertNotNull(decrypted);
        assertNotEquals(originalPassword, encrypted);
        assertEquals(originalPassword, decrypted);
    }

    @Test
    @DisplayName("Should handle null values")
    void shouldHandleNullValues() {
        // Act & Assert
        assertNull(passwordEncryptionUtil.encrypt(null));
        assertNull(passwordEncryptionUtil.decrypt(null));
    }
}
