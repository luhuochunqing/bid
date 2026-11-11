// Input: 投标文件 DocumentContent；双参入口另接当前招标文档
// Output: QualityCheckItemResult（三态）；来源引用不足以证明通过，旧单参入口保持兼容
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
            "商务偏离表", "技术偏离表", "商务规格偏离表", "技术规格偏离表", "条款偏离表", "商务及技术偏离表", "偏离表", "商务偏离说明",
            "技术偏离说明", "偏离说明", "偏离情况", "商务偏离", "技术偏离", "偏差表", "商务和技术偏差表", "差异表", "差异说明", "差异情况", "差异表正文");

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
            QualityEvidence evidence = ResponsivenessRuleCitation.negativeRowEvidence(bidDoc, extraction, neg);
            String explanation = (detail != null && !detail.isBlank())
                    ? "商务或技术条款中存在实质性负偏离：" + detail
                    : "商务或技术条款中存在实质性负偏离（详见证据定位条款）";
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    explanation,
                    "请核减负偏离项或补充合理解释，避免触发招标文件实质性偏离否决条款",
                    List.of(evidence));
        }

        // 4. 结构化偏离表：严格全表无负偏离直通 PASS；存在自述正偏离移交复核；存在 UNKNOWN 行人工核验
        if (!extraction.tables().isEmpty()) {
            if (extraction.positiveRows().isEmpty()
                    && extraction.tables().stream().allMatch(DeviationTableExtractor.DeviationTable::allNoneOrPositive)) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表正文", DEVIATION_KEYWORDS);
                return QualityCheckItemResult.pass(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "商务与技术条款全面响应，偏离表声明无实质性负偏离", List.of(evidence));
            }
            // 未知/冲突需人工核验；正偏离仍交后续核验
            for (var table : extraction.tables()) {
                for (var row : table.rows()) {
                    if (row.type() == DeviationTableExtractor.DeviationType.UNKNOWN) {
                        return QualityCheckItemResult.manualReview(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                                "偏离状态与响应说明存在不确定或冲突，需人工核验",
                                "请对照招标要求和投标响应逐项确认", List.of(
                                        QualityEvidence.bid("偏离表待确认行", table.pageNumber(), row.rawLine())));
                    }
                }
            }
            return null;
        }

        // 5. 非表格偏离表候选页定位与校验（作用域严格收敛至 candidatePages，严禁扫描全文）
        List<DocumentContent.PageContent> candidatePages = findCandidatePages(bidDoc);
        if (candidatePages.isEmpty()) {
            return ruleOnlyReview(bidDoc);
        }

        // 5.1 目录页过滤：若候选页全为目录点线且无真实提取表格，判定 FAIL
        boolean allCatalog = candidatePages.stream().allMatch(p -> isCatalogDotLine(p.text()));
        if (allCatalog) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表条目（目录）", DEVIATION_KEYWORDS);
            return QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "投标文件仅在目录中列出偏离表条目，未检索到实质性偏离表正文章节",
                    "请在标书中完整提供商务偏离表与技术偏离表正文，切勿仅在目录中列出标题",
                    List.of(evidence));
        }

        // 5.2 候选页非表格自述实质性负偏离拦截（对齐全量 NEGATIVES 词表）
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) continue;
            for (String negKw : DeviationTableExtractor.NEGATIVES) {
                int index = findDeclaredNegative(page.text(), negKw);
                if (index < 0) continue;
                QualityEvidence evidence = QualityEvidence.bid("偏离表负偏离条款", page.pageNumber(),
                        BasicInfoCheckPolicy.snippetAt(page.text(), index, index + negKw.length(), 200));
                return QualityCheckItemResult.fail(
                        BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "商务或技术条款中存在实质性负偏离（" + negKw + "）！",
                        "请核减负偏离项或补充合理解释，避免触发招标文件实质性偏离否决条款",
                        List.of(evidence));
            }
        }

        // 5.3 检查正文候选页文本是否存在未填写的模板占位符
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) continue;
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

        // 5.4 候选页自身响应模糊事实核验
        for (var page : candidatePages) {
            if (page.text() == null || isCatalogDotLine(page.text())) continue;
            int index = DeviationEvidencePolicy.ambiguousOwnFactIndex(page.text());
            if (index >= 0) return QualityCheckItemResult.manualReview(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "存在需核实的自身响应或规则引用", "请核对投标人的实际响应及适用要求", List.of(
                            QualityEvidence.bid("响应事实待核实", page.pageNumber(),
                                    BasicInfoCheckPolicy.snippetAt(page.text(), index, index + 2, 200))));
        }

        // 5.5 非表格形式自述无偏离/全部响应直通 PASS
        for (DocumentContent.PageContent page : candidatePages) {
            if (isCatalogDotLine(page.text())) continue;
            String text = page.text();
            boolean hasNoDeviation = text.contains("无偏离") || text.contains("无任何偏离") || text.contains("全部响应")
                    || text.contains("完全响应") || text.contains("零偏离") || text.contains("无负偏离")
                    || text.contains("无实质性负偏离") || text.contains("无差异") || text.contains("无任何差异");
            if (hasNoDeviation) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "偏离表正文", DEVIATION_KEYWORDS);
                return QualityCheckItemResult.pass(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "商务与技术条款全面响应，偏离表声明无实质性负偏离", List.of(evidence));
            }
        }

        return null;
    }

    private static QualityCheckItemResult ruleOnlyReview(final DocumentContent bidDoc) {
        for (var page : bidDoc.pages()) {
            String text = page.text();
            if (text == null) continue;
            for (String kw : List.of("不允许负偏差", "不允许负偏离", "禁止负偏离", "不得负偏离")) {
                int idx = text.indexOf(kw);
                if (idx >= 0) return QualityCheckItemResult.pass(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                        "识别到招标规则引用或知悉承诺，无实质性负偏离", List.of(QualityEvidence.bid("规则引用",
                                page.pageNumber(), BasicInfoCheckPolicy.snippetAt(text, idx, idx + kw.length(), 200))));
            }
            int from = 0, index;
            while ((index = text.indexOf("低于", from)) >= 0) {
                int left = index, right = index + 2;
                while (left > 0 && text.charAt(left - 1) != '。' && text.charAt(left - 1) != '；') left--;
                while (right < text.length() && text.charAt(right) != '。' && text.charAt(right) != '；') right++;
                String context = text.substring(left, right);
                var fact = DeviationEvidencePolicy.assess(DeviationEvidencePolicy.Origin.BIDDER_TEXT, context);
                if (fact.polarity() == DeviationEvidencePolicy.Polarity.RULE_ONLY
                        || fact.polarity() == DeviationEvidencePolicy.Polarity.UNKNOWN
                        || RuleCitationContext.isMinimumThreshold(text, index, "低于")) {
                    return QualityCheckItemResult.manualReview(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                            "仅识别到门槛或评审规则引用，尚不能证明投标人实际响应", "请核对投标人实际响应",
                            List.of(QualityEvidence.bid("规则引用待核验", page.pageNumber(),
                                    BasicInfoCheckPolicy.snippetAt(text, index, index + 2, 200))));
                }
                from = index + 2;
            }
        }
        return null;
    }

    public static QualityCheckItemResult check(final DocumentContent bidDoc, final DocumentContent tenderDoc) {
        return ResponsivenessRequirementEvidencePolicy.reconcile(
                ResponsivenessRuleCitation.check(bidDoc, tenderDoc), bidDoc, tenderDoc);
    }

    private static List<DocumentContent.PageContent> findCandidatePages(final DocumentContent bidDoc) {
        List<DocumentContent.PageContent> list = new ArrayList<>();
        if (bidDoc.pages() != null && !bidDoc.pages().isEmpty()) {
            for (DocumentContent.PageContent page : bidDoc.pages()) {
                if (page.text() != null && isCandidatePage(page.text())) {
                    list.add(page);
                }
            }
        }
        if (list.isEmpty() && bidDoc.fullText() != null && isCandidatePage(bidDoc.fullText())) {
            list.add(new DocumentContent.PageContent(1, bidDoc.fullText(), List.of()));
        }
        return list;
    }

    private static boolean isCandidatePage(final String text) {
        for (String kw : DEVIATION_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        if (DeviationEvidencePolicy.ambiguousOwnFactIndex(text) >= 0) return true;
        if (text.contains("我方") || text.contains("我司") || text.contains("本公司") || text.contains("本投标人")) {
            for (String negKw : DeviationTableExtractor.NEGATIVES) {
                if (findDeclaredNegative(text, negKw) >= 0) return true;
            }
        }
        return false;
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

    private static int findDeclaredNegative(final String text, final String keyword) {
        int from = 0;
        int idx;
        while ((idx = text.indexOf(keyword, from)) >= 0) {
            if (!RuleCitationContext.isMinimumThreshold(text, idx, keyword)
                    && !(keyword.equals("低于") && !RuleCitationContext.isActualLow(text, idx))
                    && !hasNegationPrefix(text, idx) && !isHeaderChoiceOption(text, idx, keyword)
                    && !RuleCitationContext.isRuleCitation(text, idx, idx + keyword.length())) {
                return idx;
            }
            from = idx + keyword.length();
        }
        return -1;
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
        for (String negation : DeviationTableExtractor.COMPOSITE_NEGATIONS) {
            if (prefix.contains(negation)) {
                return true;
            }
        }
        for (String negation : DeviationTableExtractor.SHORT_NEGATIONS) {
            if (prefix.endsWith(negation)) {
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
