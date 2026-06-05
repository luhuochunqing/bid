package com.xiyu.bid.marketinsight.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure core policy for extracting purchaser information from tender titles.
 * No state, no dependencies, no side effects.
 */
public final class PurchaserExtractionPolicy {

    private static final List<String> ORG_SUFFIXES = List.of(
            "分公司", "支公司", "子公司", "集团", "公司", "银行", "医院",
            "学校", "学院", "大学", "管委会", "机关", "委员会", "办公室",
            "局", "厅", "部", "中心", "研究所", "研究院", "设计院");

    private static final Pattern YEAR_PATTERN =
            Pattern.compile("20\\d{2}年");

    private static final List<String> PROCUREMENT_KEYWORDS =
            List.of("采购", "招标", "购置", "竞标", "邀标");

    private static final Pattern LEADING_QUOTES =
            Pattern.compile("^[「」『』【】《》\"'\\s]+");

    private static final Pattern TRAILING_NOISE =
            Pattern.compile("[\\s\\-—－·、:：集采]+$");

    private PurchaserExtractionPolicy() {
    }

    /**
     * Extract purchaser name from a tender title.
     * Strategy: find last org suffix, then fallback to year pattern,
     * then fallback to procurement keywords.
     *
     * @param title tender title to parse
     * @return ExtractionResult with found flag and purchaser details
     */
    public static ExtractionResult extractPurchaser(final String title) {
        if (title == null || title.isBlank()) {
            return ExtractionResult.notFound();
        }

        String purchaser = extractByOrgSuffix(title);
        if (purchaser == null) {
            purchaser = extractByYearPattern(title);
        }
        if (purchaser == null) {
            purchaser = extractByKeyword(title);
        }

        if (purchaser == null || purchaser.isBlank()) {
            return ExtractionResult.notFound();
        }

        purchaser = stripLeadingQuotes(purchaser);
        purchaser = trimTrailingNoise(purchaser);
        if (purchaser.isBlank()) {
            return ExtractionResult.notFound();
        }

        return ExtractionResult.found(
                purchaser, generatePurchaserHash(purchaser));
    }

    /**
     * Generate a deterministic hash for a purchaser name.
     * SHA-256 truncated to first 16 hex characters.
     *
     * @param purchaserName the purchaser name to hash
     * @return 16-character lowercase hex string
     */
    public static String generatePurchaserHash(final String purchaserName) {
        if (purchaserName == null || purchaserName.isEmpty()) {
            return "";
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashBytes = digest.digest(
                    purchaserName.getBytes(StandardCharsets.UTF_8));
            var hexBuilder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hexBuilder.append(String.format("%02x", hashBytes[i]));
            }
            return hexBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist in every JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String extractByOrgSuffix(final String title) {
        // Find the last position where any suffix ends; prefer longer suffixes
        // at the same position to avoid "公司" matching inside "分公司"
        int lastEndPos = -1;
        int lastStartPos = -1;
        for (int i = 0; i < title.length(); i++) {
            for (String suffix : ORG_SUFFIXES) {
                if (title.startsWith(suffix, i)) {
                    int endPos = i + suffix.length();
                    if (endPos > lastEndPos) {
                        lastEndPos = endPos;
                        lastStartPos = i;
                    }
                }
            }
        }
        if (lastEndPos > 0) {
            String raw = title.substring(0, lastEndPos);
            // Strip leading year patterns like "2024年"
            var yearMatcher = YEAR_PATTERN.matcher(raw);
            if (yearMatcher.find() && yearMatcher.start() < lastStartPos) {
                raw = raw.substring(yearMatcher.end());
            }
            return raw;
        }
        return null;
    }

    private static String extractByYearPattern(final String title) {
        var matcher = YEAR_PATTERN.matcher(title);
        if (matcher.find()) {
            String before = title.substring(0, matcher.start());
            if (!before.isBlank()) {
                return before;
            }
        }
        return null;
    }

    private static String extractByKeyword(final String title) {
        int firstIdx = Integer.MAX_VALUE;
        for (String keyword : PROCUREMENT_KEYWORDS) {
            int idx = title.indexOf(keyword);
            if (idx >= 0 && idx < firstIdx) {
                firstIdx = idx;
            }
        }
        if (firstIdx > 0 && firstIdx < Integer.MAX_VALUE) {
            String raw = title.substring(0, firstIdx);
            // Strip leading year patterns
            var yearMatcher = YEAR_PATTERN.matcher(raw);
            if (yearMatcher.find() && yearMatcher.start() < firstIdx) {
                raw = raw.substring(yearMatcher.end());
            }
            // Try to find org suffix within extracted text
            int lastEndPos = -1;
            for (int i = 0; i < raw.length(); i++) {
                for (String suffix : ORG_SUFFIXES) {
                    if (raw.startsWith(suffix, i)) {
                        int endPos = i + suffix.length();
                        if (endPos > lastEndPos) {
                            lastEndPos = endPos;
                        }
                    }
                }
            }
            if (lastEndPos > 0) {
                return raw.substring(0, lastEndPos);
            }
            return raw;
        }
        return null;
    }

    private static String stripLeadingQuotes(final String text) {
        return LEADING_QUOTES.matcher(text).replaceAll("");
    }

    private static String trimTrailingNoise(final String text) {
        return TRAILING_NOISE.matcher(text).replaceAll("");
    }

    /**
     * Result of purchaser extraction.
     *
     * @param found         whether a purchaser was identified
     * @param purchaserName extracted purchaser name (empty if not found)
     * @param purchaserHash deterministic hash of the name (empty if not found)
     */
    public record ExtractionResult(boolean found, String purchaserName,
                                   String purchaserHash) {

        /** Create a found result with name and hash.
         *
         * @param name purchaser name
         * @param hash purchaser hash
         * @return found extraction result
         */
        public static ExtractionResult found(final String name,
                                             final String hash) {
            return new ExtractionResult(true, name, hash);
        }

        /** Create a not-found result.
         *
         * @return not-found extraction result
         */
        public static ExtractionResult notFound() {
            return new ExtractionResult(false, "", "");
        }
    }
}
