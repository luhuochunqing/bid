package com.xiyu.bid.compliance.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 解析后的标书文档结构化内容（含物理分页与全文哈希）.
 *
 * <p>分页来源优先级：Sidecar 结构化 pages（PDF 物理页）→ {@code \f} 分页符切分 →
 * {@link #virtualPages(String)} 虚拟分页兜底（docx 等无分页符场景）.
 */
public record DocumentContent(
        String fileName,
        String fileHash,
        String fullText,
        List<PageContent> pages
) implements Serializable {

    public DocumentContent {
        if (pages == null) {
            pages = Collections.emptyList();
        } else {
            pages = Collections.unmodifiableList(new ArrayList<>(pages));
        }
        if (fullText == null) {
            fullText = "";
        }
        if (fileHash == null) {
            fileHash = "";
        }
        if (fileName == null) {
            fileName = "";
        }
    }

    /**
     * 单页物理切片内容.
     */
    public record PageContent(
            int pageNumber,
            String text,
            List<String> headings
    ) implements Serializable {
        public PageContent {
            if (text == null) {
                text = "";
            }
            if (headings == null) {
                headings = Collections.emptyList();
            } else {
                headings = Collections.unmodifiableList(new ArrayList<>(headings));
            }
        }
    }

    /**
     * 空文档内容.
     */
    public static DocumentContent empty() {
        return new DocumentContent("", "", "", Collections.emptyList());
    }

    /**
     * 从单文本构建简单分页文档（兼容测试与纯文本输入）.
     */
    public static DocumentContent fromText(
            final String fileName, final String fileHash, final String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        // 若文本中含有分页符 \f，按分页符切分
        String[] splitPages = text.split("\f");
        List<PageContent> pageList = new ArrayList<>();
        for (int i = 0; i < splitPages.length; i++) {
            pageList.add(new PageContent(i + 1, splitPages[i], Collections.emptyList()));
        }
        return new DocumentContent(fileName, fileHash, text, pageList);
    }

    /** 触发虚拟分页的单页文本长度下限：低于此值不切块（短文档无分页价值）. */
    public static final int VIRTUAL_PAGE_MIN_TEXT_LENGTH = 4_000;
    /** 虚拟分页单块长度硬上限（单段超限时按此硬切）. */
    public static final int VIRTUAL_PAGE_MAX_CHARS = 6_000;

    /**
     * 虚拟分页纯函数：无物理分页符的长文本按内容边界切块.
     *
     * <p>背景：docx 经 markitdown/mammoth 转换后不携带任何分页符，整篇文档会退化为
     * 单页，导致页数完整性判断失效、证据定位恒为第 1 页、语义检索预算失控。
     * 本方法以 markdown 标题行为优先切块边界（章节语义），块内超下限时按行累积至
     * {@link #VIRTUAL_PAGE_MIN_TEXT_LENGTH} 断块；无换行的超长单段按
     * {@link #VIRTUAL_PAGE_MAX_CHARS} 硬切兜底。切块不丢字符，页码连续。
     *
     * @param fullText 全文文本（可为 null/空白）
     * @return 切块后的页内容列表；短文本返回单页，空白返回空列表
     */
    public static List<PageContent> virtualPages(final String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return Collections.emptyList();
        }
        if (fullText.length() <= VIRTUAL_PAGE_MIN_TEXT_LENGTH) {
            return List.of(new PageContent(1, fullText, Collections.emptyList()));
        }
        List<String> chunks = new ArrayList<>();
        for (String block : splitByHeadings(fullText)) {
            if (block.length() <= VIRTUAL_PAGE_MIN_TEXT_LENGTH) {
                chunks.add(block);
            } else {
                chunks.addAll(splitByLineBudget(block));
            }
        }
        List<PageContent> pages = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            if (chunk.isBlank()) {
                continue;
            }
            pages.add(new PageContent(pages.size() + 1, chunk, Collections.emptyList()));
        }
        return pages;
    }

    /** 按 markdown 标题行切块：标题行开启新块，首个标题前的内容（如封面）作为独立块. */
    private static List<String> splitByHeadings(final String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (isMarkdownHeadingLine(line) && current.length() > 0) {
                blocks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    /** markdown 标题行：1-6 个 # 后跟空白（与 CommonMark ATX heading 一致）. */
    private static final Pattern MARKDOWN_HEADING_LINE = Pattern.compile("^#{1,6}\\s.*$");

    private static boolean isMarkdownHeadingLine(final String line) {
        return MARKDOWN_HEADING_LINE.matcher(line.trim()).matches();
    }

    /** 按行累积切块：累积至 {@link #VIRTUAL_PAGE_MIN_TEXT_LENGTH} 断块；单行超硬上限时硬切. */
    private static List<String> splitByLineBudget(final String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.length() > VIRTUAL_PAGE_MAX_CHARS) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                for (int i = 0; i < line.length(); i += VIRTUAL_PAGE_MAX_CHARS) {
                    chunks.add(line.substring(i, Math.min(line.length(), i + VIRTUAL_PAGE_MAX_CHARS)));
                }
                continue;
            }
            current.append(line).append('\n');
            if (current.length() >= VIRTUAL_PAGE_MIN_TEXT_LENGTH) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
