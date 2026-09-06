// Input: 语义检查项 + 投标/招标文件 + 招标要求 + 知识库证据 + 失败知识库源
// Output: Optional<结果>——确定性硬信号（guard/一票否决第一阶段/声明负偏离）直接裁决；empty 表示移交 LLM lane
// Pos: compliance/domain/policy/ - 语义项规则 lane（spec 049 US5 / T046 拆分产物：确定性第一阶段）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 语义项规则 lane（纯核心）：只产出确定性硬信号.
 *
 * <p>spec 049 第五步混合裁决的第一阶段：
 * <ul>
 *   <li>guard：招标要求不可用 → MANUAL + REFERENCE_MISSING；资质库失败 → MANUAL + EVIDENCE_INSUFFICIENT</li>
 *   <li>一票否决第一阶段：标书缺失招标硬性资格 → FAIL（veto_keyword 二阶段的关键词阶段）</li>
 *   <li>声明负偏离：标书自述负偏离/不满足 → FAIL</li>
 * </ul>
 * 其余模糊场景返回 {@link Optional#empty()}，由 LLM lane 语义裁决（替代纯 String.contains 直出 PASS）.
 */
public final class SemanticRulesLane {

    /** 7 项语义条款的标准顺序. */
    public static final List<BidQualityCheckItem> SEMANTIC_ITEMS = List.of(
            BidQualityCheckItem.QUALIFICATION_MATCH,
            BidQualityCheckItem.QUALIFICATION_VETO,
            BidQualityCheckItem.FRONT_TABLE_RESPONSE,
            BidQualityCheckItem.RESPONSIVENESS_REVIEW,
            BidQualityCheckItem.QUALIFICATION_REVIEW,
            BidQualityCheckItem.SCORING_RESPONSE,
            BidQualityCheckItem.CLARIFICATION_RESPONSE);

    /** 负偏离/不满足声明的否定修饰词（紧邻关键词前出现时豁免 FAIL），按长度降序避免前缀误匹配. */
    private static final List<String> NEGATION_TERMS = List.of(
            "无任何", "不存在", "没有", "不含", "无", "未", "零");

    /** 资格/关键词匹配归一化：剔除空白（含全角空格）与全部 Unicode 标点，降低 LLM 提取表述与标书写法的差异. */
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[\\s\\u3000\\p{P}]");

    private SemanticRulesLane() {
    }

    /**
     * 确定性第一阶段裁决.
     *
     * @return 确定性结论；empty 表示无硬信号、需 LLM lane 语义裁决
     */
    public static Optional<QualityCheckItemResult> deterministicOutcome(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences,
            final Set<String> failedKnowledgeSources) {
        return switch (item) {
            case QUALIFICATION_MATCH -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "未在招标文件中提取到明确的硬性资格要求，请对照标书资质证明文件人工复核",
                    "企业资质库查询异常，无法比对企业资质与招标硬性要求");
            case QUALIFICATION_VETO -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "未在招标文件中提取到明确的一票否决与资格前置条件，请人工复核",
                    "企业资质库查询异常，无法核查一票否决与资格前置条件")
                    .or(() -> vetoFirstStage(bidDoc, tenderDoc, requirements, kbEvidences));
            case QUALIFICATION_REVIEW -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "未在招标文件中提取到资格性评审条款要求，请人工复核",
                    "企业资质库查询异常，无法核查资格性评审条款响应")
                    .or(() -> qualificationReviewFirstStage(bidDoc, requirements));
            case SCORING_RESPONSE -> requirements.missing()
                    ? Optional.of(referenceMissing(item, "招标要求不可用，无法对照评分标准逐项核查"))
                    : Optional.empty();
            case RESPONSIVENESS_REVIEW -> declaredDeviation(bidDoc);
            default -> Optional.empty();
        };
    }

    /**
     * 比对项 guard：招标要求不可用 / 资质库失败 → MANUAL（PRD §5.1）.
     */
    private static Optional<QualityCheckItemResult> guardForComparison(
            final BidQualityCheckItem item,
            final TenderRequirements requirements,
            final Set<String> failedKnowledgeSources,
            final String referenceDetail,
            final String kbDetail) {
        if (requirements.missing()) {
            return Optional.of(referenceMissing(item, referenceDetail));
        }
        if (failedKnowledgeSources.contains(KnowledgeMatch.SOURCE_QUALIFICATION)) {
            return Optional.of(knowledgeBaseUnavailable(item, kbDetail));
        }
        return Optional.empty();
    }

    /**
     * 一票否决第一阶段：标书缺失招标硬性资格要求即 FAIL（veto_keyword 关键词阶段）.
     *
     * <p>匹配前对投标全文与资格表述做归一化（去空白/标点），
     * 避免 LLM 提取表述（如 "ISO27001 信息安全认证"）与标书写法（"ISO 27001"）因
     * 空白/全半角差异而误判缺失.
     */
    private static Optional<QualityCheckItemResult> vetoFirstStage(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        String bidText = normalize(bidDoc.fullText());
        List<QualityEvidence> evidences = new ArrayList<>();
        for (String qualification : requirements.mandatoryQualifications()) {
            if (qualification.isBlank()) {
                continue;
            }
            evidences.add(EvidenceAssembler.locateTender(tenderDoc, "招标文件资质要求", List.of(qualification)));
            if (!bidText.contains(normalize(qualification))) {
                if (kbEvidences != null) {
                    evidences.addAll(kbEvidences);
                }
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_VETO,
                        "未满足招标文件硬性要求（一票否决项）：" + qualification,
                        "请在投标文件中补充对应证明材料或在企业知识库中维护有效记录",
                        evidences));
            }
        }
        return Optional.empty();
    }

    /**
     * 实质性响应：标书自述存在负偏离/不满足 → FAIL（确定性硬信号）；
     * 提及偏离但全部为否定表述（"无负偏离"/"无不满足"）→ PASS（自述无偏离）；
     * 未提及 → empty 交 LLM lane.
     */
    private static Optional<QualityCheckItemResult> declaredDeviation(final DocumentContent bidDoc) {
        String text = bidDoc.fullText();
        boolean declared = containsNonNegated(text, "负偏离") || containsNonNegated(text, "不满足");
        if (declared) {
            List<QualityEvidence> evidences = List.of(
                    EvidenceAssembler.locateBid(bidDoc, "偏离表章节", List.of("负偏离", "不满足")));
            return Optional.of(QualityCheckItemResult.fail(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "商务或技术条款中存在实质性负偏离！",
                    "请核减负偏离项或补充合理解释",
                    evidences));
        }
        if (text.contains("负偏离") || text.contains("不满足")) {
            List<QualityEvidence> evidences = List.of(
                    EvidenceAssembler.locateBid(bidDoc, "偏离表章节", List.of("负偏离", "不满足")));
            return Optional.of(QualityCheckItemResult.pass(
                    BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                    "投标文件未声明实质性负偏离", evidences));
        }
        return Optional.empty();
    }

    /** 文本中是否存在未被否定修饰的指定关键词出现（如"无负偏离"中的"负偏离"不计数）. */
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

    /** 关键词紧邻前缀（最多 4 字符）是否以否定修饰词结尾. */
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

    /** 匹配归一化：去空白（含全角空格）与全部标点. */
    private static String normalize(final String text) {
        return text == null ? "" : NORMALIZE_PATTERN.matcher(text).replaceAll("");
    }

    private static QualityCheckItemResult referenceMissing(final BidQualityCheckItem item, final String detail) {
        return ManualReasons.manual(item, ManualReasons.REFERENCE_MISSING, detail,
                "请先上传招标文件并确认 AI 能力已启用后重新核查", Collections.emptyList());
    }

    private static QualityCheckItemResult knowledgeBaseUnavailable(final BidQualityCheckItem item, final String detail) {
        return ManualReasons.manual(item, ManualReasons.EVIDENCE_INSUFFICIENT, detail,
                "知识库查询异常，请在知识库恢复后人工复核对应证明材料", Collections.emptyList());
    }

    /**
     * 资格性评审第一阶段只拦截「有占位、无真报表」的造假硬信号.
     * 财务三表 PASS / 免除不得结束整项（spec 047：本项是信誉与类似业绩门槛）.
     */
    private static Optional<QualityCheckItemResult> qualificationReviewFirstStage(
            final DocumentContent bidDoc,
            final TenderRequirements requirements) {
        if (requirements == null || requirements.financialRequirement() == null) {
            return Optional.empty();
        }
        QualityCheckItemResult result = FinancialStatementCheckPolicy.check(bidDoc, requirements.financialRequirement());
        if (FinancialStatementCheckPolicy.isHardAntiFakeFail(result)) {
            return Optional.of(result);
        }
        return Optional.empty();
    }
}
