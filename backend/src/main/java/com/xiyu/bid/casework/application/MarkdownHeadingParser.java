package com.xiyu.bid.casework.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 标题解析与章节扫描器（纯函数，无框架依赖）。
 *
 * <p>按行解析 Markdown 中的标题层级（#~###### 及「第X章/节、数字编号」），
 * 构建章节层级树与祖先路径（" > " 分隔）。</p>
 *
 * <p>所有候选标题统一经过 {@link MarkdownTitleRules#isSentenceLikeTitle(String)} 门控：PDF→markdown 转换器
 * 会把加粗正文句输出成 {@code ## 正文句子}，若不拒绝会让整个片段归属到伪章节。</p>
 *
 * <p>层级树仍接受无编号的加粗小标题（它们可能是真实的排版层级），但<b>来源标识</b>另由
 * {@link MarkdownTitleRules#isChapterTitle(String)} 把门：只有"章节号 + 标题文字"形式的标题才可作为对外展示的
 * 章节来源，无编号标题一律不给来源（见 {@link MarkdownSourceSectionIndex#locate}）。</p>
 */
public final class MarkdownHeadingParser {

    private static final Pattern MD_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private static final Pattern CHINESE_CHAPTER_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千万0-9]+[章篇卷部分](\\s+.*|$)");

    private static final Pattern CHINESE_SECTION_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千万0-9]+[节条](\\s+.*|$)");

    private static final Pattern CHINESE_NUM_HEADING_PATTERN = Pattern.compile(
            "^[一二三四五六七八九十百]+[、.．](\\s+.*|$)");

    private static final Pattern CHINESE_PAREN_NUM_PATTERN = Pattern.compile(
            "^[（(][一二三四五六七八九十百]+[）)]\\s*(.+)");

    private static final Pattern HIERARCHICAL_DOT_NUM_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)+)[、.．\\s]?\\s*(.+)");

    private static final Pattern ARABIC_NUM_HEADING_PATTERN = Pattern.compile(
            "^(\\d+)[、.．]\\s*(.+)");

    private static final Pattern ARABIC_PAREN_NUM_PATTERN = Pattern.compile(
            "^[（(](\\d+)[）)]\\s*(.+)");

    private MarkdownHeadingParser() {}

    record ParsedHeading(int level, String title) {}

    record HeadingNode(int level, String title) {}

    private static class RawSection {
        final int index;
        final int level;
        final String title;
        final String path;
        final int startOffset;
        int endOffset;

        RawSection(int index, int level, String title, String path, int startOffset, int endOffset) {
            this.index = index;
            this.level = level;
            this.title = title;
            this.path = path;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    public static List<MarkdownSourceSectionIndex.Section> scanSections(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<RawSection> rawSections = new ArrayList<>();
        Deque<HeadingNode> stack = new ArrayDeque<>();
        int sectionCounter = 1;

        int currentOffset = 0;
        String[] lines = markdown.split("\n", -1);

        for (String line : lines) {
            int lineStart = currentOffset;
            int lineEnd = currentOffset + line.length();
            currentOffset = lineEnd + 1; // +1 for newline character

            ParsedHeading heading = parseHeadingLine(line, stack);
            if (heading == null) {
                continue;
            }

            while (!stack.isEmpty() && stack.peek().level >= heading.level) {
                stack.pop();
            }

            String path;
            if (stack.isEmpty()) {
                path = heading.title;
            } else {
                List<String> pathParts = new ArrayList<>();
                Iterator<HeadingNode> it = stack.descendingIterator();
                while (it.hasNext()) {
                    pathParts.add(it.next().title);
                }
                pathParts.add(heading.title);
                path = String.join(" > ", pathParts);
            }

            if (!rawSections.isEmpty()) {
                RawSection prev = rawSections.get(rawSections.size() - 1);
                prev.endOffset = lineStart;
            }

            RawSection rawSection = new RawSection(
                    sectionCounter++,
                    heading.level,
                    heading.title,
                    path,
                    lineStart,
                    markdown.length()
            );
            rawSections.add(rawSection);

            stack.push(new HeadingNode(heading.level, heading.title));
        }

        List<MarkdownSourceSectionIndex.Section> finalSections = new ArrayList<>(rawSections.size());
        for (RawSection raw : rawSections) {
            finalSections.add(new MarkdownSourceSectionIndex.Section(
                    raw.index,
                    raw.level,
                    raw.title,
                    raw.path,
                    raw.startOffset,
                    raw.endOffset
            ));
        }
        return finalSections;
    }

    static ParsedHeading parseHeadingLine(String line, Deque<HeadingNode> stack) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;

        Matcher mdMatcher = MD_HEADING_PATTERN.matcher(trimmed);
        if (mdMatcher.matches()) {
            int level = mdMatcher.group(1).length();
            String titleText = cleanTitle(mdMatcher.group(2));
            if (!titleText.isEmpty()) {
                return MarkdownTitleRules.isSentenceLikeTitle(titleText) ? null : new ParsedHeading(level, titleText);
            }
        }

        if (trimmed.length() > 120 || trimmed.length() < 2) {
            return null;
        }
        if (MarkdownTitleRules.isSentenceLikeTitle(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("|") || trimmed.startsWith("- ") || trimmed.startsWith("* ")
                || trimmed.startsWith("+ ") || trimmed.startsWith(">")) {
            return null;
        }
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (lastChar == '。' || lastChar == '；' || lastChar == '，' || lastChar == '?'
                || lastChar == '？' || lastChar == '!' || lastChar == '！') {
            return null;
        }
        if (trimmed.contains("详见") || trimmed.contains("参见")) {
            return null;
        }

        if (CHINESE_CHAPTER_PATTERN.matcher(trimmed).find()) {
            return new ParsedHeading(1, cleanTitle(trimmed));
        }

        if (CHINESE_NUM_HEADING_PATTERN.matcher(trimmed).find()) {
            int level = stackHasLevel(stack, 1) ? 2 : 1;
            return new ParsedHeading(level, cleanTitle(trimmed));
        }

        if (CHINESE_SECTION_PATTERN.matcher(trimmed).find()) {
            return new ParsedHeading(2, cleanTitle(trimmed));
        }

        Matcher dotMatcher = HIERARCHICAL_DOT_NUM_PATTERN.matcher(trimmed);
        if (dotMatcher.find()) {
            String numPart = dotMatcher.group(1);
            long dotCount = numPart.chars().filter(ch -> ch == '.').count();
            int base = stackHasLevel(stack, 2) ? 2 : (stackHasLevel(stack, 1) ? 1 : 1);
            int level = Math.min(6, (int) (base + dotCount));
            return new ParsedHeading(level, cleanTitle(trimmed));
        }

        if (CHINESE_PAREN_NUM_PATTERN.matcher(trimmed).find()) {
            int base = (stack == null || stack.isEmpty()) ? 1 : stack.peek().level;
            int level = Math.min(6, base >= 3 ? base + 1 : (stackHasLevel(stack, 1) ? 3 : 2));
            return new ParsedHeading(level, cleanTitle(trimmed));
        }

        if (ARABIC_NUM_HEADING_PATTERN.matcher(trimmed).find()) {
            int base = (stack == null || stack.isEmpty()) ? 1 : stack.peek().level;
            int level = Math.min(6, base >= 4 ? base + 1 : (base >= 2 ? base + 1 : 2));
            return new ParsedHeading(level, cleanTitle(trimmed));
        }

        if (ARABIC_PAREN_NUM_PATTERN.matcher(trimmed).find()) {
            int base = (stack == null || stack.isEmpty()) ? 1 : stack.peek().level;
            int level = Math.min(6, base + 1);
            return new ParsedHeading(level, cleanTitle(trimmed));
        }

        return null;
    }

    static boolean stackHasLevel(Deque<HeadingNode> stack, int targetLevel) {
        if (stack == null) return false;
        for (HeadingNode node : stack) {
            if (node.level == targetLevel) {
                return true;
            }
        }
        return false;
    }

    static String cleanTitle(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        cleaned = cleaned.replaceAll("\\s*#+\\s*$", "");
        if (cleaned.startsWith("**") && cleaned.endsWith("**") && cleaned.length() > 4) {
            cleaned = cleaned.substring(2, cleaned.length() - 2).trim();
        }
        cleaned = MarkdownTitleRules.stripTrailingPageNumber(cleaned);
        return cleaned;
    }
}
