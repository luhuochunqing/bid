// Input: 标书文档 + TenderRequirements（含财务/信誉/业绩要求）
// Output: Optional<QualityCheckItemResult>（第一阶段硬信号：财务造假/资格凭证占位符直接 FAIL，否则 empty 移交 LLM 穿透全部资格核验）
// Pos: compliance/domain/policy/ - 标书资格性评审条款反假与反短路核验策略（纯核心，FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书资格性评审条款反假与反短路核验策略（纯核心）.
 *
 * <p>基于招投标法规与准则 8（反短路纪律）：
 * 1. 财务报表第一阶段只拦截「有占位、无真报表」的造假硬信号；
 * 2. 资格证明材料反假拦截：若纳税凭证、社保清单、信用中国等资格证明页存在占位模板或空白留白，直接 FAIL 拦截；
 * 3. 严格反短路：财务报表通过/免除绝对不得直接判定整项 PASS，必须移交 LLM lane 穿透核验企业信誉、类似项目业绩门槛、纳税与社保等全部资格性条款.
 */
public final class QualificationReviewPolicy {

    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile(
            "【(?:请|在此|在此处|填写|输入|插入|待填|此处|贴图|粘贴)[^】]{0,30}】|\\[(?:请|在此|填写|待填|贴图)[^\\]]{0,30}\\]");

    /** 资格性证明材料上下文关联关键词. */
    private static final List<String> QUALIFICATION_PROOF_KEYWORDS = List.of(
            "资格性审查", "资格评审", "纳税证明", "完税凭证", "依法缴纳税收",
            "社保证明", "社会保障资金", "信用中国", "失信被执行人", "经营异常名录", "业绩合同", "类似业绩");

    private QualificationReviewPolicy() {
    }

    /**
     * 资格性评审第一阶段确定性核查.
     *
     * @return 确定性硬造假 FAIL；无硬信号返回 empty 移交 LLM lane（严格反短路）
     */
    // REUSE-OK: * — 质检资格性评审核查入口，业务域独立
    public static Optional<QualityCheckItemResult> checkFirstStage(
            final DocumentContent bidDoc,
            final TenderRequirements requirements) {
        if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
            return Optional.empty();
        }

        // 1. 财务报表硬造假拦截
        if (requirements != null && requirements.financialRequirement() != null) {
            QualityCheckItemResult finResult = FinancialStatementCheckPolicy.check(bidDoc, requirements.financialRequirement());
            if (FinancialStatementCheckPolicy.isHardAntiFakeFail(finResult)) {
                return Optional.of(finResult);
            }
        }

        // 2. 资格证明材料占位符未填反假拦截
        List<DocumentContent.PageContent> nonCatalogPages = filterNonCatalogPages(bidDoc);
        for (DocumentContent.PageContent page : nonCatalogPages) {
            String text = page.text();
            if (text == null || !hasAnyKeyword(text, QUALIFICATION_PROOF_KEYWORDS)) {
                continue;
            }
            String placeholder = findPlaceholder(text);
            if (placeholder != null) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "资格证明材料占位留白", List.of(placeholder));
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_REVIEW,
                        "资格性评审证明材料中存在未填写的占位模板或空白留白（" + placeholder + "）",
                        "请在标书中补充完整的纳税证明、社保凭据、信用核查等实质性证明材料并移除占位符",
                        List.of(evidence)));
            }
        }

        // 3. 反短路：无造假硬信号时一律返回 empty，由 LLM lane 穿透核验信誉、业绩、纳税、社保等全部条款
        return Optional.empty();
    }

    private static List<DocumentContent.PageContent> filterNonCatalogPages(final DocumentContent doc) {
        if (doc.pages() == null || doc.pages().isEmpty()) {
            if (doc.fullText() != null && !doc.fullText().isBlank()) {
                return List.of(new DocumentContent.PageContent(1, doc.fullText(), List.of()));
            }
            return List.of();
        }
        return doc.pages().stream()
                .filter(p -> p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                .toList();
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

    private static boolean hasAnyKeyword(final String text, final List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
