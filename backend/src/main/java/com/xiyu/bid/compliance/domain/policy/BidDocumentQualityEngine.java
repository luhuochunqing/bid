// Input: 投标/招标文件分页内容 + 知识库匹配 + 招标要求 + 失败知识库源 + 可选 LLM lane
// Output: 25 项标准化核查结果（确定性 lane 毫秒级 + 投标函/保证金/CA证书强凭证直通与存疑深度裁决 + 语义项混合裁决，证据经 EvidenceLocator 真实定位）
// Pos: compliance/domain/policy/ - 25 项聚合裁决编排器（spec 049 US5 / T046：规则 lane + LLM lane 拆分）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 标书质量 25 项全量核查聚合裁决编排器.
 *
 * <p>spec 049 第五步混合裁决：
 * <ul>
 *   <li>确定性 lane：{@link DeterministicCheckPolicy} 18 项毫秒级熔断</li>
 *   <li>语义 lane 第一阶段：{@link SemanticRulesLane} guard 与硬信号（否决关键词/声明负偏离）</li>
 *   <li>语义 lane 第二阶段：{@link LlmAdjudicator} 真实 LLM 裁决（替代纯 String.contains 直出）；
 *       lane 缺省时语义项 MANUAL + MANUAL_REVIEW_REQUIRED，不阻断任务</li>
 * </ul>
 */
public final class BidDocumentQualityEngine {

    private BidDocumentQualityEngine() {
    }

    /**
     * 执行 25 项全量质量核查（无 LLM lane：语义项除硬信号外 MANUAL 降级）.
     */
    public static List<QualityCheckItemResult> evaluateAll(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final List<KnowledgeMatch> kbMatches,
            final TenderRequirements requirements,
            final Set<String> failedKnowledgeSources) {
        return evaluateAll(bidDoc, tenderDoc, kbMatches, requirements, failedKnowledgeSources, null, (p, s) -> { });
    }

    /**
     * 执行 25 项全量质量核查（混合裁决）.
     *
     * @param kbMatches 四知识库真实查询匹配（映射为知识库证据并进入 LLM prompt）
     * @param requirements 招标要求结构化提取结果；哨兵态时比对项 MANUAL + [REFERENCE_MISSING]
     * @param failedKnowledgeSources 查询失败的知识库来源；依赖项 MANUAL + [EVIDENCE_INSUFFICIENT]（PRD §5.1）
     * @param llm LLM 裁决 lane；null 时语义项除确定性硬信号外 MANUAL 降级
     * @param progress LLM 裁决进度回调（progress 0-100 按语义项推进）
     * @return 25 项标准化核查结果（按标准序号排序）
     */
    public static List<QualityCheckItemResult> evaluateAll(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final List<KnowledgeMatch> kbMatches,
            final TenderRequirements requirements,
            final Set<String> failedKnowledgeSources,
            final LlmAdjudicator llm,
            final LlmAdjudicator.ProgressReporter progress) {

        TenderRequirements effective = requirements == null ? TenderRequirements.missingSentinel() : requirements;
        Set<String> failedKb = failedKnowledgeSources == null ? Set.of() : failedKnowledgeSources;
        DocumentContent effectiveTender = tenderDoc == null ? DocumentContent.empty() : tenderDoc;
        List<KnowledgeMatch> effectiveMatches = kbMatches == null ? List.of() : kbMatches;
        List<QualityEvidence> kbEvidences = effectiveMatches.stream()
                .map(QualityEvidence::fromKnowledgeMatch)
                .toList();
        Map<String, QualityCheckItemResult> resultMap = new HashMap<>();

        // 1. 确定性 lane：18 项毫秒级熔断
        for (QualityCheckItemResult r : DeterministicCheckPolicy.executeChecks(bidDoc, effectiveTender.fullText())) {
            resultMap.put(r.itemCode(), r);
        }
        if (effective.bidLetterRequirement() != null) {
            resultMap.put(BidQualityCheckItem.BID_LETTER_FORMAT.getCode(),
                    BasicInfoCheckPolicy.checkBidLetterFormat(bidDoc, effective.bidLetterRequirement()));
        }
        if (effective.authorizationRequirement() != null) {
            resultMap.put(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode(),
                    BasicInfoCheckPolicy.checkAuthorizationLetter(bidDoc, effective.authorizationRequirement()));
        }
        if (effective.businessLicenseRequirement() != null) {
            resultMap.put(BidQualityCheckItem.BUSINESS_LICENSE.getCode(),
                    BasicInfoCheckPolicy.checkBusinessLicense(bidDoc, effective.businessLicenseRequirement()));
        }
        if (effective.idAuthLetterRequirement() != null) {
            resultMap.put(BidQualityCheckItem.ID_AUTH_LETTER.getCode(),
                    SubItemAndAttachmentRules.checkIdAuthLetter(bidDoc, effective.idAuthLetterRequirement()));
        }
        if (effective.priceRequirement() != null) {
            resultMap.put(BidQualityCheckItem.PRICE_CONSISTENCY.getCode(),
                    BasicInfoCheckPolicy.checkPriceConsistency(bidDoc, effective.priceRequirement()));
        }
        if (effective.caCertificateRequirement() != null) {
            resultMap.put(BidQualityCheckItem.CA_CERTIFICATE.getCode(),
                    SubItemAndAttachmentRules.checkCaCertificate(bidDoc, effective.caCertificateRequirement()));
        }
        if (effective.businessTermsRequirement() != null) {
            resultMap.put(BidQualityCheckItem.SCHEDULE_RESPONSE.getCode(),
                    BasicInfoCheckPolicy.checkScheduleResponse(bidDoc, effective.businessTermsRequirement()));
        }

        // 2. 语义 lane：规则硬信号优先，其次 LLM 裁决，lane 缺省则 MANUAL 降级
        List<BidQualityCheckItem> semanticItems = SemanticRulesLane.SEMANTIC_ITEMS;
        int adjudicated = 0;
        for (BidQualityCheckItem item : semanticItems) {
            Optional<QualityCheckItemResult> hardSignal = SemanticRulesLane.deterministicOutcome(
                    item, bidDoc, effectiveTender, effective, kbEvidences, failedKb);
            if (hardSignal.isPresent()) {
                resultMap.put(item.getCode(), hardSignal.get());
                continue;
            }
            if (llm != null) {
                resultMap.put(item.getCode(),
                        llm.adjudicateItem(item, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences));
                adjudicated++;
                progress.accept(adjudicated * 100 / semanticItems.size(), item.getTitle());
                continue;
            }
            if (resultMap.containsKey(item.getCode())) {
                // 已有确定性策略初筛结果（如 DEPOSIT_RECEIPT），在 AI 未启用时保留初筛结果
                continue;
            }
            resultMap.put(item.getCode(), ManualReasons.manual(item, ManualReasons.MANUAL_REVIEW_REQUIRED,
                    "AI 未启用，语义条款未经 LLM 裁决，请人工确认", "请在系统设置中启用 AI 后重新核查",
                    Collections.emptyList()));
        }

        // 3. 保证金要求联动与标书端 LLM 深度语义裁决接通
        if (effective.depositRequirement() != null && Boolean.FALSE.equals(effective.depositRequirement().required())) {
            // 招标明确免收：直出 PASS，附带免收说明并联动退款信息项免除
            String desc = effective.depositRequirement().description();
            String detail = (desc != null && !desc.isBlank() && !desc.contains("免收/不涉及")) ? "：" + desc : "";
            resultMap.put(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode(),
                    QualityCheckItemResult.pass(BidQualityCheckItem.DEPOSIT_RECEIPT,
                            "【不涉及】招标文件未要求缴纳投标保证金/响应担保（免收/不涉及" + detail + "）", Collections.emptyList()));
            resultMap.put(BidQualityCheckItem.DEPOSIT_REFUND.getCode(),
                    QualityCheckItemResult.pass(BidQualityCheckItem.DEPOSIT_REFUND,
                            "【不涉及】本项目不涉及投标保证金/响应担保，无需提供退款账户信息", Collections.emptyList()));
        } else {
            // 招标要求缴纳（或未明确免收）：若已有确定性强凭证直通则无需重调 LLM，可疑场景接通 LLM
            QualityCheckItemResult existingDeposit = resultMap.get(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode());
            boolean hasStrongVoucherPass = existingDeposit != null
                    && existingDeposit.status() == QualityCheckStatus.PASS
                    && !existingDeposit.evidences().isEmpty();
            if (!hasStrongVoucherPass && llm != null) {
                QualityCheckItemResult depositVerdict = llm.adjudicateItem(
                        BidQualityCheckItem.DEPOSIT_RECEIPT, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
                resultMap.put(BidQualityCheckItem.DEPOSIT_RECEIPT.getCode(), depositVerdict);
            }
        }

        // 4. 投标函格式要求联动与标书端 LLM 深度语义裁决接通（规范正文强凭证直通，存疑场景接通 LLM）
        QualityCheckItemResult existingBidLetter = resultMap.get(BidQualityCheckItem.BID_LETTER_FORMAT.getCode());
        boolean hasStrongBidLetterPass = existingBidLetter != null
                && existingBidLetter.status() == QualityCheckStatus.PASS
                && !existingBidLetter.evidences().isEmpty();
        if (!hasStrongBidLetterPass && llm != null) {
            QualityCheckItemResult letterVerdict = llm.adjudicateItem(
                    BidQualityCheckItem.BID_LETTER_FORMAT, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
            resultMap.put(BidQualityCheckItem.BID_LETTER_FORMAT.getCode(), letterVerdict);
        }

        // 5. 授权委托书要求联动与标书端 LLM 深度语义裁决接通（规范正文强凭证直通，存疑或未命中接通 LLM）
        QualityCheckItemResult existingAuthLetter = resultMap.get(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode());
        boolean hasStrongAuthLetterPass = existingAuthLetter != null
                && existingAuthLetter.status() == QualityCheckStatus.PASS
                && !existingAuthLetter.evidences().isEmpty();
        if (!hasStrongAuthLetterPass && llm != null) {
            QualityCheckItemResult authVerdict = llm.adjudicateItem(
                    BidQualityCheckItem.AUTHORIZATION_LETTER, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
            resultMap.put(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode(), authVerdict);
        }

        // 6. 营业执照要求联动与标书端 LLM 深度语义裁决接通（规范证照强凭证直通，存疑或未命中接通 LLM）
        QualityCheckItemResult existingLicense = resultMap.get(BidQualityCheckItem.BUSINESS_LICENSE.getCode());
        boolean hasStrongLicensePass = existingLicense != null
                && existingLicense.status() == QualityCheckStatus.PASS
                && !existingLicense.evidences().isEmpty();
        // 招标明确不要求证照：保留步骤 1 的直接 PASS，不再接通 LLM（防 LLM 覆盖为 FAIL/MANUAL）
        boolean licenseNotRequired = effective.businessLicenseRequirement() != null
                && !effective.businessLicenseRequirement().required();
        if (!licenseNotRequired && !hasStrongLicensePass && llm != null) {
            QualityCheckItemResult licenseVerdict = llm.adjudicateItem(
                    BidQualityCheckItem.BUSINESS_LICENSE, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
            resultMap.put(BidQualityCheckItem.BUSINESS_LICENSE.getCode(), licenseVerdict);
        }

        // 7. 委托人/法定代表人身份证件要求联动与标书端 LLM 深度语义裁决接通（规范正文强凭证直通，存疑或未命中接通 LLM）
        QualityCheckItemResult existingIdAuth = resultMap.get(BidQualityCheckItem.ID_AUTH_LETTER.getCode());
        boolean hasStrongIdAuthPass = existingIdAuth != null
                && existingIdAuth.status() == QualityCheckStatus.PASS
                && !existingIdAuth.evidences().isEmpty();
        if (!hasStrongIdAuthPass && llm != null) {
            QualityCheckItemResult idVerdict = llm.adjudicateItem(
                    BidQualityCheckItem.ID_AUTH_LETTER, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
            resultMap.put(BidQualityCheckItem.ID_AUTH_LETTER.getCode(), idVerdict);
        }

        // 8. 报价一致性与限价要求联动与标书端 LLM 深度语义裁决接通（规范报价强凭证直通，硬性超限/不符 FAIL 保留，存疑人工项接通 LLM）
        QualityCheckItemResult existingPrice = resultMap.get(BidQualityCheckItem.PRICE_CONSISTENCY.getCode());
        boolean hasStrongPricePass = existingPrice != null
                && existingPrice.status() == QualityCheckStatus.PASS
                && !existingPrice.evidences().isEmpty();
        boolean hasHardPriceFail = existingPrice != null
                && existingPrice.status() == QualityCheckStatus.FAIL;
        if (!hasStrongPricePass && !hasHardPriceFail && llm != null) {
            QualityCheckItemResult priceVerdict = llm.adjudicateItem(
                    BidQualityCheckItem.PRICE_CONSISTENCY, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
            resultMap.put(BidQualityCheckItem.PRICE_CONSISTENCY.getCode(), priceVerdict);
        }

        // 9. 开标一览表/报价表要求联动与标书端 LLM 深度语义裁决接通（规范表格强凭证直通，占位未填 FAIL 保留，存疑或未检出接通 LLM）
        QualityCheckItemResult existingTable = resultMap.get(BidQualityCheckItem.BID_OPENING_TABLE.getCode());
        boolean hasStrongTablePass = existingTable != null
                && existingTable.status() == QualityCheckStatus.PASS
                && !existingTable.evidences().isEmpty();
        boolean isPlaceholderFail = existingTable != null
                && existingTable.status() == QualityCheckStatus.FAIL
                && existingTable.conclusion() != null
                && existingTable.conclusion().contains("占位");
        if (!hasStrongTablePass && !isPlaceholderFail && llm != null) {
            QualityCheckItemResult tableVerdict = llm.adjudicateItem(
                    BidQualityCheckItem.BID_OPENING_TABLE, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
            resultMap.put(BidQualityCheckItem.BID_OPENING_TABLE.getCode(), tableVerdict);
        }

        // 10. 电子标 CA 证书与签章有效性要求联动与标书端 LLM 深度语义裁决接通（免收直通，强凭证直通，占位未填 FAIL 保留，存疑或未检出接通 LLM）
        QualityCheckItemResult existingCa = resultMap.get(BidQualityCheckItem.CA_CERTIFICATE.getCode());
        boolean caNotRequired = effective.caCertificateRequirement() != null
                && !effective.caCertificateRequirement().required();
        if (!caNotRequired) {
            boolean hasStrongCaPass = existingCa != null
                    && existingCa.status() == QualityCheckStatus.PASS
                    && !existingCa.evidences().isEmpty();
            boolean isCaPlaceholderFail = existingCa != null
                    && existingCa.status() == QualityCheckStatus.FAIL
                    && existingCa.conclusion() != null
                    && existingCa.conclusion().contains("占位");
            if (!hasStrongCaPass && !isCaPlaceholderFail && llm != null) {
                QualityCheckItemResult caVerdict = llm.adjudicateItem(
                        BidQualityCheckItem.CA_CERTIFICATE, bidDoc, effectiveTender, effective, effectiveMatches, kbEvidences);
                resultMap.put(BidQualityCheckItem.CA_CERTIFICATE.getCode(), caVerdict);
            }
        }

        // 11. 严格按 25 项标准序号排序输出
        List<QualityCheckItemResult> sorted = new ArrayList<>(resultMap.values());
        sorted.sort(Comparator.comparingInt(QualityCheckItemResult::order));
        return sorted;
    }
}
