package com.xiyu.bid.support;

import com.xiyu.bid.platform.util.PasswordEncryptionUtil;

/**
 * Test double for password encryption to avoid environment coupling in tests.
 */
public class TestPasswordEncryptionUtil extends PasswordEncryptionUtil {

    @Override
    public void initialize() {
        // No-op for tests.
    }

    @Override
    public String encrypt(String plainPassword) {
        return plainPassword;
    }

    @Override
    public String decrypt(String encryptedPassword) {
        return encryptedPassword;
    }

    @Override
    public boolean isKeyValid() {
        return true;
    }
}
