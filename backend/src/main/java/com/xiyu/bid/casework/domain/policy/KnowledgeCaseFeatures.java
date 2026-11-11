package com.xiyu.bid.casework.domain.policy;

/**
 * 纯核心：案例入库时要写上的子主题和质量分。
 *
 * <p>子主题复用 {@link CaseSubTopicPolicy#inferSubTopic}。质量分与历史切片抽取脚本同一套字数规则：
 * 不足 120 字为 0，否则从 50 分起按字数、非空行数、条目符号和数字承诺加分，上限 100。</p>
 */
public final class KnowledgeCaseFeatures {

    static final int MIN_TEXT_LENGTH = 120;

    private KnowledgeCaseFeatures() {}

    public record Assigned(String subTopic, int qualityScore) {}

    public static Assigned assign(String title, String text) {
        String subTopic = CaseSubTopicPolicy.inferSubTopic(title, text).name();
        return new Assigned(subTopic, qualityScore(text));
    }

    static int qualityScore(String text) {
        if (text == null) {
            return 0;
        }
        String body = text.strip();
        if (body.length() < MIN_TEXT_LENGTH) {
            return 0;
        }
        int score = 50 + lengthScore(body.length()) + paragraphScore(nonBlankLineCount(body));
        if (body.matches("(?s).*([一二三四五]、|\\d+\\.|\\(\\d+\\)|•|—).*")) {
            score += 10;
        }
        if (body.matches("(?is).*(\\d+小时|\\d+%|\\d+天|7x24|7\\*24).*")) {
            score += 5;
        }
        return Math.min(100, score);
    }

    private static int lengthScore(int length) {
        if (length >= 1500) {
            return 25;
        }
        if (length >= 600) {
            return 20;
        }
        if (length >= 300) {
            return 15;
        }
        return 10;
    }

    private static int paragraphScore(int paragraphs) {
        if (paragraphs >= 4) {
            return 10;
        }
        if (paragraphs >= 2) {
            return 5;
        }
        return 0;
    }

    private static int nonBlankLineCount(String body) {
        int count = 0;
        for (String line : body.split("\\R")) {
            if (!line.isBlank()) {
                count++;
            }
        }
        return count;
    }
}
