package com.xiyu.bid.scoreparse.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hashes an explicitly canonical representation; time and estimated scores are not inputs. */
public final class ScoreVersionFingerprint {
    private ScoreVersionFingerprint() {}
    public static String hash(String canonical) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
