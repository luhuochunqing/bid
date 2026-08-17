package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/** 评分项是否与脏章节相关：引用含章标题，或维度/名称与标题有字面重叠；不确定则算相关。 */
public final class ScoreItemChapterMatch {

    private ScoreItemChapterMatch() {
    }

    public static boolean related(String dim, String detail, String quote, String evidence, List<String> dirtyTitles) {
        if (dirtyTitles == null || dirtyTitles.isEmpty()) {
            return true;
        }
        for (String title : dirtyTitles) {
            if (title == null || title.isBlank()) {
                continue;
            }
            if (contains(quote, title) || contains(evidence, title) || contains(dim, title) || contains(detail, title)
                    || contains(title, dim) || contains(title, detail)) {
                return true;
            }
        }
        return blank(quote) && blank(evidence);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null && !needle.isBlank() && haystack.contains(needle);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
