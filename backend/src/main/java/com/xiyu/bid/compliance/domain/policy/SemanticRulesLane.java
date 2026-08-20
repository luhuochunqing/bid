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
                    "招标要求不可用，无法比对企业资质与招标硬性要求",
                    "企业资质库查询异常，无法比对企业资质与招标硬性要求");
            case QUALIFICATION_VETO -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "招标要求不可用，无法核查一票否决与资格前置条件",
                    "企业资质库查询异常，无法核查一票否决与资格前置条件")
                    .or(() -> vetoFirstStage(bidDoc, tenderDoc, requirements, kbEvidences));
            case QUALIFICATION_REVIEW -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "招标要求不可用，无法核查资格性评审条款响应",
                    "企业资质库查询异常，无法核查资格性评审条款响应");
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
     */
    private static Optional<QualityCheckItemResult> vetoFirstStage(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<QualityEvidence> kbEvidences) {
        List<QualityEvidence> evidences = new ArrayList<>();
        for (String qualification : requirements.mandatoryQualifications()) {
            if (qualification.isBlank()) {
                continue;
            }
            evidences.add(EvidenceAssembler.locateTender(tenderDoc, "招标文件资质要求", List.of(qualification)));
            if (!bidDoc.fullText().contains(qualification)) {
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
     * 实质性响应：标书自述负偏离/不满足即 FAIL（确定性硬信号）.
     */
    private static Optional<QualityCheckItemResult> declaredDeviation(final DocumentContent bidDoc) {
        boolean hasDeviation = bidDoc.fullText().contains("负偏离") || bidDoc.fullText().contains("不满足");
        if (!hasDeviation) {
            return Optional.empty();
        }
        List<QualityEvidence> evidences = List.of(
                EvidenceAssembler.locateBid(bidDoc, "偏离表章节", List.of("负偏离", "不满足")));
        return Optional.of(QualityCheckItemResult.fail(
                BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                "商务或技术条款中存在实质性负偏离！",
                "请核减负偏离项或补充合理解释",
                evidences));
    }

    private static QualityCheckItemResult referenceMissing(final BidQualityCheckItem item, final String detail) {
        return ManualReasons.manual(item, ManualReasons.REFERENCE_MISSING, detail,
                "请先上传招标文件并确认 AI 能力已启用后重新核查", Collections.emptyList());
    }

    private static QualityCheckItemResult knowledgeBaseUnavailable(final BidQualityCheckItem item, final String detail) {
        return ManualReasons.manual(item, ManualReasons.EVIDENCE_INSUFFICIENT, detail,
                "知识库查询异常，请在知识库恢复后人工复核对应证明材料", Collections.emptyList());
    }
}
