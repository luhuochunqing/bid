package com.xiyu.bid.scoreparse.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 投标字节哈希 + 清单指纹均与上次成功打分相同则跳过智能评估。 */
public final class BidScoreSkipPolicy {

    private BidScoreSkipPolicy() {
    }

    public static boolean shouldSkip(String bidHash, String itemSetHash, String lastBidHash, String lastItemSetHash) {
        return bidHash != null && itemSetHash != null
                && bidHash.equals(lastBidHash) && itemSetHash.equals(lastItemSetHash);
    }

    public static String hashBytes(byte[] content) {
        if (content == null) {
            return null;
        }
        return sha256(content);
    }

    /** 流式 SHA-256（spec 045）：OnDisk 临时文件与 hashBytes 同口径，同内容必同 hash。 */
    public static String hashFile(Path file) {
        if (file == null) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("投标文件哈希计算失败: " + file, exception);
        }
    }

    public static String hashItems(List<String> itemFingerprints) {
        if (itemFingerprints == null) {
            return null;
        }
        return sha256(String.join("\n", itemFingerprints).getBytes(StandardCharsets.UTF_8));
    }

    public static String itemFingerprint(Long id, Object weight, String detail) {
        return id + "|" + weight + "|" + (detail == null ? "" : detail);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
