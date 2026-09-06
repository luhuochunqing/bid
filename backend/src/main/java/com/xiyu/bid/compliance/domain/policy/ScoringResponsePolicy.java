// Input: 投标文件 DocumentContent + 招标要求 TenderRequirements
// Output: Optional<QualityCheckItemResult>（第一阶段确定性结论，空表示移交 LLM lane）
// Pos: compliance/domain/policy/ - 评分标准逐项对应与支撑材料核验策略（纯核心）
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
 * 评分标准逐项对应与支撑材料核验策略（纯核心）.
 */
public final class ScoringResponsePolicy {

    private static final List<String> SCORING_KEYWORDS = List.of(
            "评分标准对应表", "评分细则响应表", "评分细则对应表", "得分点对应表", "评分索引表",
            "评分标准响应", "技术方案与组织设计", "技术方案与项目实施", "技术方案", "施工组织设计",
            "项目实施方案", "实施组织设计", "项目管理机构", "服务方案", "售后服务方案");

    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|此处|填写|输入|插入|粘贴|待填|说明)[^】]{0,30}】|\\[(?:请|在此|此处|填写|粘贴|待填|说明)[^\\]]{0,30}\\]|【(?:技术方案|实施方案|团队人员|服务方案)[^】]{0,15}】");

    private ScoringResponsePolicy() {
    }

    /**
     * 评分项响应第一阶段确定性核验.
     *
     * @return 确定性拦截（FAIL）；无硬伤返回 empty 移交 LLM 语义裁决
     */
    // REUSE-OK: * — 质检评分标准响应核查入口，业务域独立
    public static Optional<QualityCheckItemResult> checkFirstStage(
            final DocumentContent bidDoc, final TenderRequirements requirements) {
        if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
            return Optional.empty();
        }

        List<DocumentContent.PageContent> candidatePages = findCandidatePages(bidDoc);
        if (candidatePages.isEmpty()) {
            return Optional.empty();
        }

        // 1. 目录过滤：若候选页全为目录点线/大纲索引页，硬拦截 FAIL
        boolean allCatalog = candidatePages.stream().allMatch(p ->
                BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()) || isCatalogDotLine(p.text()));
        if (allCatalog) {
            QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "评分响应条目（目录）", SCORING_KEYWORDS);
            return Optional.of(QualityCheckItemResult.fail(
                    BidQualityCheckItem.SCORING_RESPONSE,
                    "投标文件仅在目录中列出评分响应或技术方案条目，未检索到实质性正文章节",
                    "请在标书正文中提供完整的技术与实施方案及评分项对应表，切勿仅在目录中列出标题",
                    List.of(evidence)));
        }

        // 2. 检查非目录正文页是否存在未填写的模板占位符或下划线留白
        for (DocumentContent.PageContent page : candidatePages) {
            if (BidLetterCheckPolicy.isCatalogOrOutlinePage(page.text()) || isCatalogDotLine(page.text())) {
                continue;
            }
            String placeholder = findPlaceholder(page.text());
            if (placeholder != null) {
                QualityEvidence evidence = EvidenceAssembler.locateBidSkippingCatalog(bidDoc, "技术方案占位留白", List.of(placeholder));
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.SCORING_RESPONSE,
                        "评分项或技术方案正文中存在未填写的占位符或下划线留白（" + placeholder + "）",
                        "请在标书技术方案与评分对应章节中补全实际应答内容，不得保留空白下划线或模板占位符",
                        List.of(evidence)));
            }
        }

        // 无硬伤：移交 LLM lane 进行评分点深度对照与打分
        return Optional.empty();
    }

    private static List<DocumentContent.PageContent> findCandidatePages(final DocumentContent bidDoc) {
        List<DocumentContent.PageContent> list = new ArrayList<>();
        if (bidDoc.pages() != null && !bidDoc.pages().isEmpty()) {
            for (DocumentContent.PageContent page : bidDoc.pages()) {
                if (page.text() == null) {
                    continue;
                }
                for (String kw : SCORING_KEYWORDS) {
                    if (page.text().contains(kw)) {
                        list.add(page);
                        break;
                    }
                }
            }
        }
        if (list.isEmpty() && bidDoc.fullText() != null) {
            for (String kw : SCORING_KEYWORDS) {
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
