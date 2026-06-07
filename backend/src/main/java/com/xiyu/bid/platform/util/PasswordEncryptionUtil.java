package com.xiyu.bid.platform.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password Encryption Utility
 * Provides AES-256-GCM encryption for password storage
 *
 * Security Requirements:
 * - Production environment: PLATFORM_ENCRYPTION_KEY environment variable is REQUIRED
 * - Development/Test environment: Fallback key is used for convenience
 * - Minimum key length: 16 characters
 * - Key derivation: SHA-256 to generate 256-bit key
 */
@Component
@Slf4j
public class PasswordEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Fallback key for development/test environments only
    private static final String DEV_FALLBACK_KEY = "dev-fallback-encryption-key-32-chars";

    private byte[] encryptionKey;

    @Value("${platform.account.encryption-key:}")
    private String configuredKey;

    @Autowired(required = false)
    private Environment environment;

    @PostConstruct
    public void initialize() {
        // Priority order:
        // 1. Application property (platform.account.encryption-key)
        // 2. Environment variable (PLATFORM_ENCRYPTION_KEY)
        // 3. Fallback key (dev/test only)
        String keyFromEnv = resolveEncryptionKey();

        // Check if we're in a non-development environment
        boolean isProductionOrStaging = isProductionOrStagingEnvironment();

        if (keyFromEnv == null || keyFromEnv.trim().isEmpty()) {
            if (isProductionOrStaging) {
                // Production/staging environments MUST have the encryption key
                String errorMsg = "PLATFORM_ENCRYPTION_KEY environment variable is required in " +
                                getActiveProfiles() + " environment. " +
                                "This is a security requirement - hardcoded keys are not allowed. " +
                                "Please set PLATFORM_ENCRYPTION_KEY environment variable with at least 16 characters.";
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            } else {
                // Development/test environments use fallback key
                log.warn("PLATFORM_ENCRYPTION_KEY not set in {} environment. Using fallback key for development only. " +
                        "This should NOT be used in production!",
                        getActiveProfiles());
                keyFromEnv = DEV_FALLBACK_KEY;
            }
        }

        // Validate minimum key length for security
        if (keyFromEnv.length() < 16) {
            String errorMsg = "PLATFORM_ENCRYPTION_KEY must be at least 16 characters for secure encryption. " +
                           "Current length: " + keyFromEnv.length();
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // Ensure key is exactly 32 bytes (256 bits) for AES-256
        this.encryptionKey = deriveKey(keyFromEnv);
        log.info("PasswordEncryptionUtil initialized with AES-256-GCM in {} environment", getActiveProfiles());
    }

    private String resolveEncryptionKey() {
        if (configuredKey != null && !configuredKey.trim().isEmpty()) {
            return configuredKey;
        }
        String keyFromSystemProperty = System.getProperty("PLATFORM_ENCRYPTION_KEY");
        if (keyFromSystemProperty != null) {
            return keyFromSystemProperty;
        }
        if (environment != null) {
            return environment.getProperty("PLATFORM_ENCRYPTION_KEY");
        }
        return System.getenv("PLATFORM_ENCRYPTION_KEY");
    }

    /**
     * Check if the current environment is production or staging
     * @return true if running in production or staging, false otherwise
     */
    private boolean isProductionOrStagingEnvironment() {
        if (environment == null) {
            // If we can't determine the environment, check system property
            String springProfile = System.getProperty("SPRING_PROFILES_ACTIVE");
            if (springProfile != null) {
                return springProfile.toLowerCase().contains("prod") ||
                       springProfile.toLowerCase().contains("staging");
            }
            // Default to treating null environment as non-production (for test convenience)
            // This allows unit tests to work without Spring context
            return false;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            // No active profiles, check default profiles
            String[] defaultProfiles = environment.getDefaultProfiles();
            for (String profile : defaultProfiles) {
                if ("prod".equalsIgnoreCase(profile) || "staging".equalsIgnoreCase(profile)) {
                    return true;
                }
            }
            return false;
        }

        // Check active profiles
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) || "staging".equalsIgnoreCase(profile)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get a string representation of active Spring profiles for logging
     * @return comma-separated list of active profiles
     */
    private String getActiveProfiles() {
        if (environment == null) {
            return "unknown";
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            String[] defaultProfiles = environment.getDefaultProfiles();
            return defaultProfiles.length > 0
                ? String.join(",", defaultProfiles) + " (default)"
                : "none";
        }

        return String.join(",", activeProfiles);
    }

    /**
     * Encrypt a plain text password
     * @param plainPassword the plain text password to encrypt
     * @return Base64 encoded encrypted password, or null if input is null
     */
    public String encrypt(String plainPassword) {
        if (plainPassword == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Encrypt the password
            byte[] encryptedData = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedData.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);

            // Return Base64 encoded result
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (GeneralSecurityException e) {
            log.error("Failed to encrypt password", e);
            throw new RuntimeException("Password encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted password
     * @param encryptedPassword the Base64 encoded encrypted password
     * @return the decrypted plain text password, or null if input is null
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null) {
            return null;
        }

        try {
            // Decode Base64
            byte[] decodedData = Base64.getDecoder().decode(encryptedPassword);

            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt the data
            byte[] decryptedData = cipher.doFinal(encryptedData);

            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("Failed to decrypt password", e);
            throw new RuntimeException("Password decryption failed", e);
        }
    }

    /**
     * Derive a 256-bit key from the provided key string using SHA-256
     * @param keyString the key string
     * @return a 32-byte array suitable for AES-256
     */
    private byte[] deriveKey(String keyString) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(keyString.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validate the encryption key
     * @return true if the key is properly initialized
     */
    public boolean isKeyValid() {
        return encryptionKey != null && encryptionKey.length == 32;
    }
}
