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
 * 响应性评审与偏离表规范性核验策略（纯核心，<= 300 行）.
 */
public final class ResponsivenessReviewPolicy {

    private static final int HEADER_CONTEXT_RADIUS = 15;

    private static final List<String> DEVIATION_KEYWORDS = List.of(
            "商务偏离表", "技术偏离表", "商务规格偏离表", "技术规格偏离表",
            "条款偏离表", "商务及技术偏离表", "偏离表");

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

        // 1. 结构化偏离表提取
        DeviationTableExtractor.ExtractionResult extraction = DeviationTableExtractor.extract(bidDoc);

        // 2. 偏离表内占位符未填反假拦截（硬 FAIL）
        if (!extraction.allPlaceholders().isEmpty()) {
            String placeholder = extraction.allPlaceholders().get(0);
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表占位留白", List.of(placeholder));
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "偏离表中存在占位模板或空白留白未填（" + placeholder + "）",
                    "请在偏离表中明确说明偏离情况（如'无偏离'或具体偏离项），不得保留模板占位符",
                    List.of(evidence));
        }

        // 3. 结构化负偏离行精准拦截（硬 FAIL）
        if (!extraction.negativeRows().isEmpty()) {
            DeviationTableExtractor.DeviationRow neg = extraction.negativeRows().get(0);
            String detail = (!neg.clause().isBlank())
                    ? "条款 " + neg.clause() + "（" + neg.description() + "）"
                    : neg.description();
            QualityEvidence evidence = EvidenceAssembler.locateBid(
                    bidDoc, "偏离表负偏离条款", List.of("负偏离", "不满足", "负偏差", neg.clause()));
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "商务或技术条款中存在实质性负偏离：" + detail,
                    "请核减负偏离项或补充合理解释，避免触发招标文件实质性偏离否决条款",
                    List.of(evidence));
        }

        // 4. 非表格形式明确自述实质性负偏离拦截（对齐全量 NEGATIVES 词表，排除表头选项说明）
        String fullText = bidDoc.fullText();
        for (String negKw : DeviationTableExtractor.NEGATIVES) {
            if (containsDeclaredNegative(fullText, negKw)) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表负偏离条款", List.of(negKw));
                return QualityCheckItemResult.fail(
                        BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "商务或技术条款中存在实质性负偏离（" + negKw + "）！",
                        "请核减负偏离项或补充合理解释，避免触发招标文件实质性偏离否决条款",
                        List.of(evidence));
            }
        }

        List<DocumentContent.PageContent> candidatePages = findCandidatePages(bidDoc);
        if (candidatePages.isEmpty() && extraction.tables().isEmpty()) {
            return null;
        }

        // 5. 目录页过滤：若候选页全为目录点线且无真实提取表格，判定 FAIL
        boolean allCatalog = !candidatePages.isEmpty() && candidatePages.stream().allMatch(p -> isCatalogDotLine(p.text()));
        if (allCatalog && extraction.tables().isEmpty()) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表条目（目录）", DEVIATION_KEYWORDS);
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "投标文件仅在目录中列出偏离表条目，未检索到实质性偏离表正文章节",
                    "请在标书中完整提供商务偏离表与技术偏离表正文，切勿仅在目录中列出标题",
                    List.of(evidence));
        }

        // 6. 检查正文候选页文本是否存在未填写的模板占位符
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

        // 7. 结构化偏离表：严格全表无偏离（纯 NONE）直通 PASS；存在自述正偏离或 UNKNOWN 行存疑交 LLM 深度核验
        if (!extraction.tables().isEmpty()) {
            if (extraction.positiveRows().isEmpty()
                    && extraction.tables().stream().allMatch(DeviationTableExtractor.DeviationTable::allNoneOrPositive)) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表正文", DEVIATION_KEYWORDS);
                return QualityCheckItemResult.pass(
                        BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "商务与技术条款全面响应，偏离表声明无实质性负偏离",
                        List.of(evidence));
            }
            // 包含投标人自述正偏离（防假阳性免审漏洞）或 UNKNOWN 行，存疑移交 LLM/专家复核
            return null;
        }

        // 8. 非表格形式自述无偏离/全部响应直通 PASS
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) {
                continue;
            }
            String text = page.text();
            boolean hasNoDeviation = text.contains("无偏离") || text.contains("无任何偏离")
                    || text.contains("全部响应") || text.contains("完全响应") || text.contains("零偏离")
                    || text.contains("无负偏离") || text.contains("无实质性负偏离");
            if (hasNoDeviation) {
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

    private static boolean containsDeclaredNegative(final String text, final String keyword) {
        int from = 0;
        int idx;
        while ((idx = text.indexOf(keyword, from)) >= 0) {
            if (!hasNegationPrefix(text, idx) && !isHeaderChoiceOption(text, idx, keyword)) {
                return true;
            }
            from = idx + keyword.length();
        }
        return false;
    }

    private static boolean hasNegationPrefix(final String text, final int idx) {
        int start = Math.max(0, idx - 18);
        for (int i = idx - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == '。' || c == '；' || c == '、' || c == '\n' || c == '|' || c == ',' || c == ';' || c == '.') {
                start = i + 1;
                break;
            }
        }
        String prefix = text.substring(start, idx).trim();
        for (String negation : DeviationTableExtractor.NEGATIONS) {
            if (prefix.contains(negation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeaderChoiceOption(final String text, final int idx, final String keyword) {
        int start = Math.max(0, idx - HEADER_CONTEXT_RADIUS);
        int end = Math.min(text.length(), idx + keyword.length() + HEADER_CONTEXT_RADIUS);
        String context = text.substring(start, end);
        return context.contains("正偏离/负偏离") || context.contains("/负偏离/")
                || context.contains("正偏离、负偏离") || context.contains("正偏离或负偏离");
    }
}
