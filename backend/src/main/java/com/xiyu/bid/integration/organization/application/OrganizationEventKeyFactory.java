package com.xiyu.bid.integration.organization.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class OrganizationEventKeyFactory {
    private OrganizationEventKeyFactory() {
    }

    static String build(String sourceApp, String traceId, String topic, String payload) {
        return hash(String.join("|", blankToEmpty(sourceApp), blankToEmpty(traceId), blankToEmpty(topic), hash(blankToEmpty(payload))));
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
