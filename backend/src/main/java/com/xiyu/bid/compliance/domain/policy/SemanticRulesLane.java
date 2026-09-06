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
                    "企业资质库查询异常，无法比对企业资质与招标硬性要求")
                    .or(() -> QualificationMatchPolicy.checkFirstStage(bidDoc, requirements));
            case QUALIFICATION_VETO -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "未在招标文件中提取到明确的一票否决与资格前置条件，请人工复核",
                    "企业资质库查询异常，无法核查一票否决与资格前置条件")
                    .or(() -> QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, requirements, kbEvidences));
            case QUALIFICATION_REVIEW -> guardForComparison(item, requirements, failedKnowledgeSources,
                    "未在招标文件中提取到资格性评审条款要求，请人工复核",
                    "企业资质库查询异常，无法核查资格性评审条款响应")
                    .or(() -> QualificationReviewPolicy.checkFirstStage(bidDoc, requirements));
            case SCORING_RESPONSE -> requirements.missing()
                    ? Optional.of(referenceMissing(item, "招标要求不可用，无法对照评分标准逐项核查"))
                    : Optional.empty();
            case FRONT_TABLE_RESPONSE -> Optional.ofNullable(
                    FrontTableResponsePolicy.check(bidDoc, requirements != null ? requirements.frontTableRequirement() : null));
            case RESPONSIVENESS_REVIEW -> Optional.ofNullable(ResponsivenessReviewPolicy.check(bidDoc));
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

    private static QualityCheckItemResult referenceMissing(final BidQualityCheckItem item, final String detail) {
        return ManualReasons.manual(item, ManualReasons.REFERENCE_MISSING, detail,
                "请先上传招标文件并确认 AI 能力已启用后重新核查", Collections.emptyList());
    }

    private static QualityCheckItemResult knowledgeBaseUnavailable(final BidQualityCheckItem item, final String detail) {
        return ManualReasons.manual(item, ManualReasons.EVIDENCE_INSUFFICIENT, detail,
                "知识库查询异常，请在知识库恢复后人工复核对应证明材料", Collections.emptyList());
    }
}
