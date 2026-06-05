package com.xiyu.bid.platform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PasswordEncryptionUtil cipher behavior tests")
class PasswordEncryptionUtilCipherBehaviorTest extends AbstractPasswordEncryptionUtilTest {

    @Test
    @DisplayName("Should successfully encrypt and decrypt password")
    void shouldEncryptAndDecryptPassword() {
        setupTestKey();
        String originalPassword = "MySecurePassword123!";

        String encrypted = passwordEncryptionUtil.encrypt(originalPassword);
        String decrypted = passwordEncryptionUtil.decrypt(encrypted);

        assertNotNull(encrypted);
        assertNotNull(decrypted);
        assertNotEquals(originalPassword, encrypted);
        assertEquals(originalPassword, decrypted);
    }

    @Test
    @DisplayName("Should generate different encrypted values for same password (due to random IV)")
    void shouldGenerateDifferentEncryptedValues() {
        setupTestKey();
        String password = "SamePassword123";

        String encrypted1 = passwordEncryptionUtil.encrypt(password);
        String encrypted2 = passwordEncryptionUtil.encrypt(password);

        assertNotEquals(encrypted1, encrypted2);
        assertEquals(password, passwordEncryptionUtil.decrypt(encrypted1));
        assertEquals(password, passwordEncryptionUtil.decrypt(encrypted2));
    }

    @Test
    @DisplayName("Should handle null password encryption")
    void shouldHandleNullPasswordEncryption() {
        setupTestKey();

        String encrypted = passwordEncryptionUtil.encrypt(null);

        assertNull(encrypted);
    }

    @Test
    @DisplayName("Should handle null password decryption")
    void shouldHandleNullPasswordDecryption() {
        setupTestKey();

        String decrypted = passwordEncryptionUtil.decrypt(null);

        assertNull(decrypted);
    }

    @Test
    @DisplayName("Should handle empty string password")
    void shouldHandleEmptyStringPassword() {
        setupTestKey();

        String encrypted = passwordEncryptionUtil.encrypt("");
        String decrypted = passwordEncryptionUtil.decrypt(encrypted);

        assertNotNull(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("Should handle special characters in password")
    void shouldHandleSpecialCharacters() {
        setupTestKey();
        String specialPassword = "P@ssw0rd!#$%^&*()_+-=[]{}|;':\",./<>?`~";

        String encrypted = passwordEncryptionUtil.encrypt(specialPassword);
        String decrypted = passwordEncryptionUtil.decrypt(encrypted);

        assertEquals(specialPassword, decrypted);
    }

    @Test
    @DisplayName("Should handle Unicode characters (emoji, Chinese, etc.)")
    void shouldHandleUnicodeCharacters() {
        setupTestKey();
        String unicodePassword = "密码🔑🔒测试Test测试123";

        String encrypted = passwordEncryptionUtil.encrypt(unicodePassword);
        String decrypted = passwordEncryptionUtil.decrypt(encrypted);

        assertEquals(unicodePassword, decrypted);
    }

    @Test
    @DisplayName("Should handle very long passwords")
    void shouldHandleVeryLongPasswords() {
        setupTestKey();
        String password = "a".repeat(10000);

        String encrypted = passwordEncryptionUtil.encrypt(password);
        String decrypted = passwordEncryptionUtil.decrypt(encrypted);

        assertEquals(password, decrypted);
    }

    @Test
    @DisplayName("Should throw exception when decrypting invalid Base64")
    void shouldThrowWhenDecryptingInvalidBase64() {
        setupTestKey();

        assertThrows(RuntimeException.class, () -> passwordEncryptionUtil.decrypt("not-valid-base64!!!"));
    }

    @Test
    @DisplayName("Should throw exception when decrypting with wrong key")
    void shouldThrowWhenDecryptingWithWrongKey() {
        setupTestKey();
        String encrypted = passwordEncryptionUtil.encrypt("TestPassword123");

        setupDifferentKey();

        assertThrows(RuntimeException.class, () -> passwordEncryptionUtil.decrypt(encrypted));
    }

    @Test
    @DisplayName("Should report key as valid after proper initialization")
    void shouldReportKeyValidAfterInitialization() {
        setupTestKey();

        assertTrue(passwordEncryptionUtil.isKeyValid());
    }

    @Test
    @DisplayName("Should report key as invalid before initialization")
    void shouldReportKeyInvalidBeforeInitialization() {
        assertFalse(passwordEncryptionUtil.isKeyValid());
    }
}
