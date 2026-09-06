// Input: 文档分页列表 PageContent + 关键词/章节线索
// Output: Optional<EvidenceHit>——命中返回真实页码与原文摘录，未命中 empty（禁止 pageNumber=1 兜底）
// Pos: compliance/domain/policy/ - Evidence 真实定位纯函数（spec 049 US4 / T040，research.md R5）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Evidence 真实页码/原文定位器（纯函数，spec 049 第四步）.
 *
 * <p>遍历 {@link DocumentContent.PageContent} 查找首个包含任一关键词的页，
 * 返回该页真实页码与命中位置上下文摘录；无法定位时返回 {@link Optional#empty()}，
 * 由调用方映射 page=null（禁止填 1）.
 */
public final class EvidenceLocator {

    /** quote 上下文总长度上限. */
    static final int MAX_QUOTE_LENGTH = 120;

    private EvidenceLocator() {
    }

    /**
     * 在分页文档中定位首个命中关键词的页.
     *
     * @param pages 文档分页内容（可为 null/空）
     * @param keywords 关键词/章节线索（可为 null/空；空词跳过）
     * @return 命中结果；未命中或入参不足返回 empty
     */
    public static Optional<EvidenceHit> locate(
            final List<DocumentContent.PageContent> pages,
            final List<String> keywords) {
        if (pages == null || pages.isEmpty() || keywords == null || keywords.isEmpty()) {
            return Optional.empty();
        }
        for (DocumentContent.PageContent page : pages) {
            if (page.text() == null || page.text().isEmpty()) {
                continue;
            }
            for (String keyword : keywords) {
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                int idx = page.text().indexOf(keyword);
                if (idx >= 0) {
                    return Optional.of(new EvidenceHit(
                            page.pageNumber(), extractQuote(page.text(), idx, keyword.length())));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 在分页文档中定位首个命中关键词的非目录正文页.
     *
     * <p>目录索引页（含点线/大纲）优先命中会造成证据假定位：裁决逻辑已在各 policy 内过滤目录页，
     * 展示用证据也须穿透至同一正文页；若仅目录页命中则返回 {@link Optional#empty()}（诚实未定位）.
     *
     * @param pages 文档分页内容（可为 null/空）
     * @param keywords 关键词/章节线索（可为 null/空；空词跳过）
     * @return 正文页命中结果；无正文页命中返回 empty
     */
    public static Optional<EvidenceHit> locateSkippingCatalog(
            final List<DocumentContent.PageContent> pages,
            final List<String> keywords) {
        if (pages == null || pages.isEmpty() || keywords == null || keywords.isEmpty()) {
            return Optional.empty();
        }
        List<DocumentContent.PageContent> bodyPages = new ArrayList<>();
        for (DocumentContent.PageContent page : pages) {
            if (page.text() == null || page.text().isEmpty()
                    || BidLetterCheckPolicy.isCatalogOrOutlinePage(page.text())) {
                continue;
            }
            bodyPages.add(page);
        }
        return locate(bodyPages, keywords);
    }

    /**
     * 截取命中位置上下文：总长不超过 {@link #MAX_QUOTE_LENGTH}，保证为原文子串.
     */
    static String extractQuote(final String text, final int hitIndex, final int keywordLength) {
        if (text.length() <= MAX_QUOTE_LENGTH) {
            return text;
        }
        int before = (MAX_QUOTE_LENGTH - keywordLength) / 2;
        int start = Math.max(0, hitIndex - before);
        int end = Math.min(text.length(), start + MAX_QUOTE_LENGTH);
        start = Math.max(0, end - MAX_QUOTE_LENGTH);
        return text.substring(start, end);
    }
}
