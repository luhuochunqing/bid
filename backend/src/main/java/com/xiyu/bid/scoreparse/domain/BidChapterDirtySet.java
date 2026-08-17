package com.xiyu.bid.scoreparse.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按标题切开投标正文，对比哈希得到脏章。切不出标题则 chapters 为空。 */
public final class BidChapterDirtySet {

    private static final Pattern HEADING =
            Pattern.compile("(?m)^(?:#{1,3}\\s+|第[一二三四五六七八九十0-9]+[章节]\\s*)(.+)$");

    public record Chapter(String title, String body, String hash) {
    }

    private BidChapterDirtySet() {
    }

    public static List<Chapter> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = HEADING.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            titles.add(matcher.group(1).trim());
        }
        if (titles.isEmpty()) {
            return List.of();
        }
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int to = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String body = text.substring(starts.get(i), to);
            chapters.add(new Chapter(titles.get(i), body, sha256(body)));
        }
        return chapters;
    }

    public static List<String> dirtyTitles(List<Chapter> current, Map<String, String> previousHashes) {
        if (current.isEmpty() || previousHashes == null || previousHashes.isEmpty()) {
            return List.of();
        }
        List<String> dirty = new ArrayList<>();
        for (Chapter chapter : current) {
            String old = previousHashes.get(chapter.title());
            if (old == null || !old.equals(chapter.hash())) {
                dirty.add(chapter.title());
            }
        }
        return dirty;
    }

    public static Map<String, String> toHashMap(List<Chapter> chapters) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Chapter chapter : chapters) {
            map.put(chapter.title(), chapter.hash());
        }
        return map;
    }

    private static String sha256(String body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
