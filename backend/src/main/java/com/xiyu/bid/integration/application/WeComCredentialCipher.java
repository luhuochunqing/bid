package com.xiyu.bid.integration.application;

import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thin facade over PasswordEncryptionUtil for WeCom credential encryption.
 * Does NOT re-implement cryptography — delegates entirely to the platform utility.
 */
@Component
@RequiredArgsConstructor
public class WeComCredentialCipher {

    private final PasswordEncryptionUtil encryptionUtil;

    public String encrypt(String plainSecret) {
        return encryptionUtil.encrypt(plainSecret);
    }

    public String decrypt(String encryptedSecret) {
        return encryptionUtil.decrypt(encryptedSecret);
    }
}
