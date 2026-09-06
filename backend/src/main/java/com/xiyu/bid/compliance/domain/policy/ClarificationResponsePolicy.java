// Input: 投标文件 DocumentContent + 招标文件 DocumentContent + 招标要求 TenderRequirements
// Output: Optional<QualityCheckItemResult>（第一阶段确定性结论，空表示移交 LLM lane）
// Pos: compliance/domain/policy/ - 补遗与澄清答疑文件响应核验策略（纯核心）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 补遗与澄清答疑文件响应核验策略（纯核心）.
 */
public final class ClarificationResponsePolicy {

    private static final List<String> CLARIFICATION_KEYWORDS = List.of(
            "澄清文件确认函", "答疑纪要确认函", "补遗书确认函", "澄清答疑确认函",
            "答疑文件确认函", "澄清确认函", "澄清答疑", "答疑纪要", "补遗文件", "补遗书", "澄清文件");

    private static final List<String> TENDER_CLARIFICATION_CUES = List.of(
            "澄清", "答疑", "补遗");

    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|填写|输入|插入|待填|说明)[^】]{0,30}】|\\[(?:请|在此|填写|待填|说明)[^\\]]{0,30}\\]|【(?:第.*号澄清|澄清文件|盖章)[^】]{0,15}】");

    private ClarificationResponsePolicy() {
    }

    /**
     * 补遗与澄清答疑第一阶段确定性核验.
     *
     * @return 确定性结论（PASS/FAIL）；存疑或需语义核验返回 empty 移交 LLM
     */
    // REUSE-OK: * — 质检澄清答疑响应核查入口，业务域独立
    public static Optional<QualityCheckItemResult> checkFirstStage(
            final DocumentContent bidDoc, final DocumentContent tenderDoc, final TenderRequirements requirements) {
        if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
            return Optional.empty();
        }

        List<DocumentContent.PageContent> candidatePages = findCandidatePages(bidDoc);

        // 1. 标书包含澄清答疑相关正文
        if (!candidatePages.isEmpty()) {
            // 1.1 目录过滤：若候选页均为目录点线/大纲，硬拦截 FAIL
            boolean allCatalog = candidatePages.stream().allMatch(p ->
                    BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()) || isCatalogDotLine(p.text()));
            if (allCatalog) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "澄清确认函条目（目录）", CLARIFICATION_KEYWORDS);
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.CLARIFICATION_RESPONSE,
                        "投标文件仅在目录中列出澄清或答疑确认条目，未检索到实质性确认函正文",
                        "请在标书中完整提供澄清答疑确认函或在正文中对答疑事项实质性响应，切勿仅在目录中列出标题",
                        List.of(evidence)));
            }

            // 1.2 检查非目录页是否存在未填占位符或下划线留白
            for (DocumentContent.PageContent page : candidatePages) {
                if (BidLetterCheckPolicy.isCatalogOrOutlinePage(page.text()) || isCatalogDotLine(page.text())) {
                    continue;
                }
                String placeholder = findPlaceholder(page.text());
                if (placeholder != null) {
                    QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "澄清确认函占位留白", List.of(placeholder));
                    return Optional.of(QualityCheckItemResult.fail(
                            BidQualityCheckItem.CLARIFICATION_RESPONSE,
                            "澄清/答疑确认函正文中存在未填写的占位符或下划线留白（" + placeholder + "）",
                            "请在标书澄清确认函中补全具体应答内容，不得保留空白下划线或模板占位符",
                            List.of(evidence)));
                }
            }

            // 1.3 检查是否具备实质性有效签署的确认函
            for (DocumentContent.PageContent page : candidatePages) {
                if (BidLetterCheckPolicy.isCatalogOrOutlinePage(page.text()) || isCatalogDotLine(page.text())) {
                    continue;
                }
                String text = page.text();
                boolean hasClarificationCue = text.contains("澄清") || text.contains("答疑") || text.contains("补遗");
                boolean hasReceiptConfirmation = text.contains("确认收到") || text.contains("已收到") || text.contains("收悉")
                        || text.contains("悉知并遵守") || text.contains("无异议") || text.contains("全部吸收") || text.contains("遵守相关答疑");
                boolean hasSignOrSeal = text.contains("盖章") || text.contains("签署") || text.contains("投标人") || text.contains("日期");
                if (hasClarificationCue && hasReceiptConfirmation && hasSignOrSeal) {
                    QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "澄清答疑确认函正文", CLARIFICATION_KEYWORDS);
                    return Optional.of(QualityCheckItemResult.pass(
                            BidQualityCheckItem.CLARIFICATION_RESPONSE,
                            "投标文件包含真实完备的澄清答疑/补遗文件确认函，已实质性确认并响应答疑事项",
                            List.of(evidence)));
                }
            }
        }

        // 2. 标书未提及澄清：核查招标文件是否发布过澄清答疑
        if (tenderDoc != null && tenderDoc.fullText() != null && !tenderDoc.fullText().isBlank()) {
            boolean tenderHasClarification = TENDER_CLARIFICATION_CUES.stream().anyMatch(tenderDoc.fullText()::contains);
            if (!tenderHasClarification) {
                // 招标文件未涉及任何澄清答疑或补遗要求，免除响应
                return Optional.of(QualityCheckItemResult.pass(
                        BidQualityCheckItem.CLARIFICATION_RESPONSE,
                        "招标文件未发布澄清答疑或补遗文件，无需单独响应",
                        List.of()));
            }
        }

        // 其余场景：招标有答疑但标书无独立确认函，移交 LLM 语义核验技术/商务是否吸收了答疑参数
        return Optional.empty();
    }

    private static List<DocumentContent.PageContent> findCandidatePages(final DocumentContent bidDoc) {
        List<DocumentContent.PageContent> list = new ArrayList<>();
        if (bidDoc.pages() != null && !bidDoc.pages().isEmpty()) {
            for (DocumentContent.PageContent page : bidDoc.pages()) {
                if (page.text() == null) {
                    continue;
                }
                for (String kw : CLARIFICATION_KEYWORDS) {
                    if (page.text().contains(kw)) {
                        list.add(page);
                        break;
                    }
                }
            }
        }
        if (list.isEmpty() && bidDoc.fullText() != null) {
            for (String kw : CLARIFICATION_KEYWORDS) {
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
}
