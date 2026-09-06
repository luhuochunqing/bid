// Input: 投标文件 DocumentContent
// Output: QualityCheckItemResult（PASS / FAIL）或 null（存疑移交 LLM lane）
// Pos: compliance/domain/policy/ - 响应性评审条款与偏离表核验策略（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应性评审与偏离表规范性核验策略（纯核心）.
 */
public final class ResponsivenessReviewPolicy {

    private static final List<String> DEVIATION_KEYWORDS = List.of(
            "商务偏离表", "技术偏离表", "商务规格偏离表", "技术规格偏离表",
            "条款偏离表", "商务及技术偏离表", "偏离表");

    private static final List<String> NEGATION_TERMS = List.of(
            "无任何", "不存在", "没有", "不含", "无", "未", "零");

    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|填写|输入|插入|待填|说明)[^】]{0,30}】|\\[(?:请|在此|填写|待填|说明)[^\\]]{0,30}\\]");

    private ResponsivenessReviewPolicy() {
    }

    /**
     * 核查投标文件响应性与偏离表填报.
     */
    // REUSE-OK: * — 质检偏离表响应核查入口，业务域独立
    public static QualityCheckItemResult check(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
            return null;
        }

        String fullText = bidDoc.fullText();

        // 1. 实质性负偏离硬拦截（若明确自述负偏离/不满足且非否定修饰）
        boolean declaredNegative = containsNonNegated(fullText, "负偏离") || containsNonNegated(fullText, "不满足");
        if (declaredNegative) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表负偏离条款", List.of("负偏离", "不满足"));
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "商务或技术条款中存在实质性负偏离！",
                    "请核减负偏离项或补充合理解释，避免触发招标文件实质性偏离否决条款",
                    List.of(evidence));
        }

        List<DocumentContent.PageContent> candidatePages = findCandidatePages(bidDoc);
        if (candidatePages.isEmpty()) {
            // 未提及偏离表关键字，留交 LLM 语义裁决
            return null;
        }

        // 2. 目录页过滤：若候选页全为目录点线，判定 FAIL
        boolean allCatalog = candidatePages.stream().allMatch(p -> isCatalogDotLine(p.text()));
        if (allCatalog) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表条目（目录）", DEVIATION_KEYWORDS);
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "投标文件仅在目录中列出偏离表条目，未检索到实质性偏离表正文章节",
                    "请在标书中完整提供商务偏离表与技术偏离表正文，切勿仅在目录中列出标题",
                    List.of(evidence));
        }

        // 3. 检查正文候选页是否存在未填写的模板占位符或空白留白
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) {
                continue;
            }
            String placeholder = findPlaceholder(page.text());
            if (placeholder != null) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表占位留白", List.of(placeholder));
                return QualityCheckItemResult.fail(
                        BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "偏离表中存在占位模板或空白留白未填（" + placeholder + "）",
                        "请在偏离表中明确说明偏离情况（如'无偏离'或具体偏离项），不得保留模板占位符",
                        List.of(evidence));
            }
        }

        // 4. 自述无偏离/全部响应直通 PASS
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) {
                continue;
            }
            String text = page.text();
            boolean hasNoDeviationDeclaration = text.contains("无偏离") || text.contains("无任何偏离")
                    || text.contains("全部响应") || text.contains("完全响应") || text.contains("零偏离")
                    || text.contains("无负偏离");
            if (hasNoDeviationDeclaration) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表正文", DEVIATION_KEYWORDS);
                return QualityCheckItemResult.pass(
                        BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "商务与技术条款全面响应，偏离表声明无实质性负偏离",
                        List.of(evidence));
            }
        }

        return null;
    }

    private static List<DocumentContent.PageContent> findCandidatePages(final DocumentContent bidDoc) {
        List<DocumentContent.PageContent> list = new ArrayList<>();
        if (bidDoc.pages() != null && !bidDoc.pages().isEmpty()) {
            for (DocumentContent.PageContent page : bidDoc.pages()) {
                if (page.text() == null) {
                    continue;
                }
                for (String kw : DEVIATION_KEYWORDS) {
                    if (page.text().contains(kw)) {
                        list.add(page);
                        break;
                    }
                }
            }
        }
        if (list.isEmpty() && bidDoc.fullText() != null) {
            for (String kw : DEVIATION_KEYWORDS) {
                if (bidDoc.fullText().contains(kw)) {
                    list.add(new DocumentContent.PageContent(1, bidDoc.fullText(), List.of()));
                    break;
                }
            }
        }
        return list;
    }

    private static boolean isCatalogDotLine(final String text) {
        if (text == null) {
            return false;
        }
        return text.contains("......") || text.contains(". . . .") || text.contains("……");
    }

    private static String findPlaceholder(final String text) {
        Matcher m1 = UNDERLINE_BLANK_PATTERN.matcher(text);
        if (m1.find()) {
            return m1.group();
        }
        Matcher m2 = BRACKET_PLACEHOLDER_PATTERN.matcher(text);
        if (m2.find()) {
            return m2.group();
        }
        return null;
    }

    private static boolean containsNonNegated(final String text, final String keyword) {
        int from = 0;
        int idx;
        while ((idx = text.indexOf(keyword, from)) >= 0) {
            if (!hasNegationPrefix(text, idx)) {
                return true;
            }
            from = idx + keyword.length();
        }
        return false;
    }

    private static boolean hasNegationPrefix(final String text, final int idx) {
        int start = Math.max(0, idx - 4);
        String prefix = text.substring(start, idx);
        for (String negation : NEGATION_TERMS) {
            if (prefix.endsWith(negation)) {
                return true;
            }
        }
        return false;
    }
}
