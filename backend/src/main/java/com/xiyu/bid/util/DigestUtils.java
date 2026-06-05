// Pure Core / 摘要计算工具类
package com.xiyu.bid.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Pure Core utility for computing cryptographic digests.
 */
public final class DigestUtils {

    private DigestUtils() {
    }

    /**
     * Computes SHA-256 hexadecimal digest of the input string.
     *
     * @param input the string to hash
     * @return 64-character SHA-256 hex string
     */
    public static String sha256(final String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
