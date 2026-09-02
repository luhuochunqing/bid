package com.xiyu.bid.compliance.domain;

import com.xiyu.bid.util.DigestUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    /** 多册合并时文件名的连接符（报告 bidFileName 展示口径）. */
    public static final String MERGED_FILE_NAME_SEPARATOR = "、";

    /**
     * 合并文件名展示上限：对齐 bid_file_name 列宽 255（实体 @Column 无显式 length 的默认值）.
     * 截断必须发生在所有最终写点（trigger 落库 + 异步 updateFileMetadata 覆写）共用的本方法，
     * 否则异步链路会绕过 trigger 侧截断导致 data-too-long.
     */
    public static final int MERGED_FILE_NAME_MAX_LENGTH = 255;

    /**
     * 将多册投标文件（价格册/商务册/技术册等）合并为单个逻辑文档（纯函数）.
     *
     * <p>多文件质量核查的最小实现：合并在 Loader 边界完成，25 项引擎、证据链、
     * 报告链路继续消费单个 DocumentContent，零改动. 合并语义：
     * <ul>
     *   <li>页码跨册连续重编号（证据 pageNumber 契约不变，quote 仍是主锚点）；</li>
     *   <li>全文按册序以空行分隔拼接（不注入文件名，避免文件名误触正则规则）；</li>
     *   <li>文件名按册序以顿号连接，超长按 {@link #capMergedFileName} 截断；</li>
     *   <li>fileHash 为各册哈希按册序聚合的 SHA-256——同序确定性保证缓存命中稳定，
     *       任一册变化（重传/增删）即聚合哈希变化、缓存自然失效；</li>
     *   <li>单册原样返回（保持既有单文件项目的缓存哈希兼容）；空列表返回空文档.</li>
     * </ul>
     *
     * @param documents 按册序（上传顺序）排列的解析结果列表
     * @return 合并后的单个逻辑文档
     */
    public static DocumentContent merge(final List<DocumentContent> documents) {
        if (documents == null || documents.isEmpty()) {
            return empty();
        }
        if (documents.size() == 1) {
            return documents.get(0);
        }
        String joinedNames = documents.stream()
                .map(DocumentContent::fileName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining(MERGED_FILE_NAME_SEPARATOR));
        String mergedFullText = documents.stream()
                .map(DocumentContent::fullText)
                .collect(Collectors.joining("\n\n"));
        List<PageContent> mergedPages = new ArrayList<>();
        for (DocumentContent document : documents) {
            for (PageContent page : document.pages()) {
                mergedPages.add(new PageContent(mergedPages.size() + 1, page.text(), page.headings()));
            }
        }
        return new DocumentContent(
                capMergedFileName(joinedNames, documents.size()),
                aggregateFileHash(documents), mergedFullText, mergedPages);
    }

    /**
     * 合并文件名超长截断（纯函数）：保留前段并追加“等N册”标注总册数.
     * 按码点边界收尾，避免把代理对（emoji 等）切成孤立代理项落库.
     */
    public static String capMergedFileName(final String joinedNames, final int totalCount) {
        if (joinedNames == null || joinedNames.length() <= MERGED_FILE_NAME_MAX_LENGTH) {
            return joinedNames == null ? "" : joinedNames;
        }
        String suffix = "等" + totalCount + "册";
        String trimmed = joinedNames.substring(0, MERGED_FILE_NAME_MAX_LENGTH - suffix.length());
        int last = trimmed.length() - 1;
        if (Character.isHighSurrogate(trimmed.charAt(last))) {
            trimmed = trimmed.substring(0, last);
        }
        return trimmed + suffix;
    }

    /**
     * 聚合哈希：对各册哈希按册序拼接后整体做一次 SHA-256（小写十六进制）.
     */
    private static String aggregateFileHash(final List<DocumentContent> documents) {
        String joined = documents.stream()
                .map(DocumentContent::fileHash)
                .collect(Collectors.joining("|"));
        return DigestUtils.sha256(joined);
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
