package com.xiyu.bid.platform.util;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

abstract class AbstractPasswordEncryptionUtilTest {

    protected PasswordEncryptionUtil passwordEncryptionUtil;

    @BeforeEach
    void setUpPasswordEncryptionUtil() {
        passwordEncryptionUtil = new PasswordEncryptionUtil();
        clearEnvironmentVariables();
    }

    protected void clearEnvironmentVariables() {
        System.clearProperty("PLATFORM_ENCRYPTION_KEY");
        System.clearProperty("SPRING_PROFILES_ACTIVE");
        System.clearProperty("platform.account.encryption-key");
    }

    protected void setupTestKey() {
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey",
                "test-encryption-key-32-chars-long-for-security");
        passwordEncryptionUtil.initialize();
    }

    protected void setupDifferentKey() {
        ReflectionTestUtils.setField(passwordEncryptionUtil, "configuredKey",
                "different-encryption-key-32-chars-long");
        passwordEncryptionUtil.initialize();
    }
}
