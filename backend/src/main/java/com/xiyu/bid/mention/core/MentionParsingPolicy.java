// Input: raw user-authored content containing @[name](id) tokens
// Output: ParsedContent record (plainText + distinct mentioned ids, capped)
// Pos: Pure Core/提及解析策略
package com.xiyu.bid.mention.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure policy that extracts @[name](id) mention tokens from raw content.
 *
 * <p>Never performs IO, never throws for business input; returns a value.
 */
public final class MentionParsingPolicy {

    private static final Pattern MENTION = Pattern.compile("@\\[([^\\]]+)\\]\\((\\d+)\\)");
    private static final int MAX_MENTIONS = 20;

    private static final Set<String> ALLOWED_SOURCE_TYPES =
        Set.of("PROJECT", "DOCUMENT", "QUALIFICATION", "TENDER", "TASK", "COMMENT");

    private MentionParsingPolicy() {
    }

    public record ParsedContent(String plainText, List<Long> mentionedUserIds) {
    }

    public static boolean isAllowedSourceType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return true;
        }
        return ALLOWED_SOURCE_TYPES.contains(entityType.toUpperCase(java.util.Locale.ROOT));
    }

    public static Set<String> allowedSourceTypes() {
        return ALLOWED_SOURCE_TYPES;
    }

    public static ParsedContent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedContent("", List.of());
        }
        Matcher m = MENTION.matcher(raw);
        List<Long> ids = new ArrayList<>();
        while (m.find()) {
            Long parsed = tryParseLong(m.group(2));
            if (parsed == null) {
                continue;
            }
            if (!ids.contains(parsed)) {
                ids.add(parsed);
            }
            if (ids.size() >= MAX_MENTIONS) {
                break;
            }
        }
        String plainText = m.reset().replaceAll("@$1");
        return new ParsedContent(plainText, List.copyOf(ids));
    }

    private static Long tryParseLong(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return null;
            }
        }
        long value = 0L;
        for (int i = 0; i < raw.length(); i++) {
            value = value * 10 + (raw.charAt(i) - '0');
            if (value < 0) {
                return null;
            }
        }
        return value;
    }
}
