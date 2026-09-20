package com.xiyu.bid.casework.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 来源章节索引与定位器（Application 层纯函数，无框架依赖）。
 *
 * <p>构建章节层级树与祖先路径（" > " 分隔），并支持对应答片段做空白归一化精确匹配：
 * <ul>
 *   <li>唯一命中且命中章节是<b>已验证章节</b>：写五个来源字段（标题、路径、文档名、页码、序号）</li>
 *   <li>零命中 / 跨章节多命中 / 解析失败 / 仅命中评分项标题 / <b>命中章节无章节号</b>：返回 null（调用方写 NULL）</li>
 * </ul>
 *
 * <p><b>已验证章节</b>由 {@link MarkdownTitleRules#isChapterTitle(String)} 把门：层级树仍接受无编号的
 * 加粗小标题（它们可能是真实排版层级，用于构建 path），但只有"章节号 + 标题文字"形式的标题才可充当
 * 对外展示/写回的「原文所在标题」。无编号标题没有序号可核对，不足以自证是标书真实章节，宁可不给来源
 * （调用方展示为「暂无来源标题」），也不拿评分项标题或正文句子冒充。</p>
 */
public final class MarkdownSourceSectionIndex {

    private static final Pattern PAGE_MARKER_PATTERN = Pattern.compile(
            "(?:<!--\\s*page[\\s_:]*(\\d+)\\s*-->|(?:\\[|【)Page\\s*(\\d+)(?:\\]|】))",
            Pattern.CASE_INSENSITIVE);

    public record Section(
            int index,
            int level,
            String title,
            String path,
            int startOffset,
            int endOffset
    ) {
    }

    public record Provenance(
            String title,
            String path,
            Integer index,
            Integer pageNumber
    ) {
    }

    private final String originalMarkdown;
    private final List<Section> sections;
    private final NormalizedText normalizedMarkdown;

    private MarkdownSourceSectionIndex(String originalMarkdown, List<Section> sections, NormalizedText normalizedMarkdown) {
        this.originalMarkdown = originalMarkdown != null ? originalMarkdown : "";
        this.sections = Collections.unmodifiableList(sections);
        this.normalizedMarkdown = normalizedMarkdown;
    }

    public List<Section> getSections() {
        return sections;
    }

    /**
     * 解析 Markdown 文本，提取所有标题及其层级路径。
     */
    public static MarkdownSourceSectionIndex parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new MarkdownSourceSectionIndex("", List.of(), new NormalizedText("", new int[0]));
        }

        List<Section> finalSections = MarkdownHeadingParser.scanSections(markdown);
        NormalizedText normMd = NormalizedText.build(markdown);
        return new MarkdownSourceSectionIndex(markdown, finalSections, normMd);
    }

    /**
     * 在原文中定位片段的来源章节。
     */
    public Provenance locate(String matchedSnippet) {
        return locate(matchedSnippet, null);
    }

    /**
     * 在原文中定位片段的来源章节（带评分项标题过滤）。
     */
    public Provenance locate(String matchedSnippet, String scoringPointTitle) {
        if (matchedSnippet == null || matchedSnippet.isBlank() || sections.isEmpty()) {
            return null;
        }

        String normSnippet = normalize(matchedSnippet);
        if (normSnippet.isBlank()) {
            return null;
        }

        // 仅命中评分项标题时 provenance 记为空
        if (scoringPointTitle != null && !scoringPointTitle.isBlank()) {
            String normScoringTitle = normalize(scoringPointTitle);
            if (normSnippet.equalsIgnoreCase(normScoringTitle)) {
                return null;
            }
        }

        String normText = normalizedMarkdown.text();
        int[] mapping = normalizedMarkdown.mapping();
        if (normText.isEmpty() || mapping.length == 0) {
            return null;
        }

        List<Integer> origOffsets = new ArrayList<>();
        int searchIdx = 0;
        while ((searchIdx = normText.indexOf(normSnippet, searchIdx)) != -1) {
            if (searchIdx < mapping.length) {
                origOffsets.add(mapping[searchIdx]);
            }
            searchIdx += Math.max(1, normSnippet.length());
        }

        if (origOffsets.isEmpty()) {
            return null;
        }

        Set<String> distinctPaths = new LinkedHashSet<>();
        Section firstMatchedSection = null;
        int firstOrigOffset = -1;

        for (int origOffset : origOffsets) {
            Section sec = findSectionForOffset(origOffset);
            if (sec == null) {
                // Matched before any section -> cannot prove valid section
                return null;
            }
            distinctPaths.add(sec.path());
            if (firstMatchedSection == null) {
                firstMatchedSection = sec;
                firstOrigOffset = origOffset;
            }
        }

        // 跨章节多命中 -> 返回 null
        if (distinctPaths.size() != 1 || firstMatchedSection == null) {
            return null;
        }

        // 命中章节无章节号 -> 不是可核对的真实章节，返回 null（宁可不给来源）
        if (!MarkdownTitleRules.isChapterTitle(firstMatchedSection.title())) {
            return null;
        }

        Integer pageNumber = findPageNumberBefore(firstOrigOffset);

        return new Provenance(
                firstMatchedSection.title(),
                firstMatchedSection.path(),
                firstMatchedSection.index(),
                pageNumber
        );
    }

    private Section findSectionForOffset(int origOffset) {
        for (Section sec : sections) {
            if (origOffset >= sec.startOffset() && origOffset < sec.endOffset()) {
                return sec;
            }
        }
        return null;
    }

    private Integer findPageNumberBefore(int offset) {
        if (offset <= 0 || originalMarkdown.isEmpty()) {
            return null;
        }
        String prefix = originalMarkdown.substring(0, Math.min(offset, originalMarkdown.length()));
        Matcher matcher = PAGE_MARKER_PATTERN.matcher(prefix);
        Integer lastPage = null;
        while (matcher.find()) {
            String p1 = matcher.group(1);
            String p2 = matcher.group(2);
            String pageStr = p1 != null ? p1 : p2;
            if (pageStr != null) {
                try {
                    lastPage = Integer.parseInt(pageStr);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return lastPage;
    }

    private static String normalize(String str) {
        if (str == null) return "";
        return str.trim().replaceAll("\\s+", " ");
    }

    public record NormalizedText(String text, int[] mapping) {
        public static NormalizedText build(String original) {
            if (original == null || original.isEmpty()) {
                return new NormalizedText("", new int[0]);
            }
            StringBuilder sb = new StringBuilder(original.length());
            int[] mapping = new int[original.length()];
            boolean inWhitespace = false;
            int normLen = 0;

            for (int i = 0; i < original.length(); i++) {
                char c = original.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (!inWhitespace && sb.length() > 0) {
                        sb.append(' ');
                        mapping[normLen++] = i;
                        inWhitespace = true;
                    }
                } else {
                    sb.append(c);
                    mapping[normLen++] = i;
                    inWhitespace = false;
                }
            }

            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                sb.setLength(sb.length() - 1);
                normLen--;
            }

            int[] finalMapping = Arrays.copyOf(mapping, normLen);
            return new NormalizedText(sb.toString(), finalMapping);
        }
    }
}
