package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯函数式确定性质量核查策略引擎 (FP-Java).
 * 零框架依赖，毫秒级快速断言与物理页码定位.
 */
public final class DeterministicCheckPolicy {

    private DeterministicCheckPolicy() {
    }

    /**
     * 对投标文件执行确定性规则检查.
     *
     * @param bidDoc 投标文件结构化内容
     * @param tenderText 招标文件文本内容
     * @return 检查项结果列表
     */
    public static List<QualityCheckItemResult> executeChecks(
            final DocumentContent bidDoc, final String tenderText) {
        List<QualityCheckItemResult> results = new ArrayList<>();

        // 基本信息 10 项中的确定性规则
        results.add(BasicInfoCheckPolicy.checkCoverInfo(bidDoc));
        results.add(BasicInfoCheckPolicy.checkBidLetterFormat(bidDoc));
        results.add(BasicInfoCheckPolicy.checkAuthorizationLetter(bidDoc));
        results.add(BasicInfoCheckPolicy.checkBusinessLicense(bidDoc));
        results.add(BasicInfoCheckPolicy.checkPriceConsistency(bidDoc));
        results.add(BasicInfoCheckPolicy.checkCurrencyUnit(bidDoc));
        results.add(BasicInfoCheckPolicy.checkScheduleResponse(bidDoc, tenderText));
        results.add(BasicInfoCheckPolicy.checkSignatureSeal(bidDoc));
        results.add(BasicInfoCheckPolicy.checkContactInfo(bidDoc));

        // 分项合规规则
        results.add(SubItemAndAttachmentRules.checkDocumentCompleteness(bidDoc));
        results.add(SubItemAndAttachmentRules.checkTemplateFormat(bidDoc));
        results.add(SubItemAndAttachmentRules.checkVetoKeyword(bidDoc));
        results.add(SubItemAndAttachmentRules.checkCommitmentSeal(bidDoc));

        // 附件清单 5 项确定性规则
        results.add(SubItemAndAttachmentRules.checkIdAuthLetter(bidDoc));
        results.add(SubItemAndAttachmentRules.checkBidOpeningTable(bidDoc));
        results.add(SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText));
        results.add(SubItemAndAttachmentRules.checkCaCertificate(bidDoc, tenderText));
        results.add(SubItemAndAttachmentRules.checkDepositRefund(bidDoc, tenderText));

        return results;
    }

    public static QualityCheckItemResult checkCoverInfo(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkCoverInfo(bidDoc);
    }

    public static QualityCheckItemResult checkBidLetterFormat(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkBidLetterFormat(bidDoc);
    }

    public static QualityCheckItemResult checkBidLetterFormat(
            final DocumentContent bidDoc, final BidLetterRequirement requirement) {
        return BasicInfoCheckPolicy.checkBidLetterFormat(bidDoc, requirement);
    }

    public static QualityCheckItemResult checkAuthorizationLetter(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkAuthorizationLetter(bidDoc);
    }

    public static QualityCheckItemResult checkBusinessLicense(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkBusinessLicense(bidDoc);
    }

    public static QualityCheckItemResult checkPriceConsistency(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkPriceConsistency(bidDoc);
    }

    public static QualityCheckItemResult checkCurrencyUnit(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkCurrencyUnit(bidDoc);
    }

    public static QualityCheckItemResult checkScheduleResponse(final DocumentContent bidDoc, final String tenderText) {
        return BasicInfoCheckPolicy.checkScheduleResponse(bidDoc, tenderText);
    }

    public static QualityCheckItemResult checkSignatureSeal(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkSignatureSeal(bidDoc);
    }

    public static QualityCheckItemResult checkContactInfo(final DocumentContent bidDoc) {
        return BasicInfoCheckPolicy.checkContactInfo(bidDoc);
    }

    public static QualityCheckItemResult checkDocumentCompleteness(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkDocumentCompleteness(bidDoc);
    }

    public static QualityCheckItemResult checkTemplateFormat(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkTemplateFormat(bidDoc);
    }

    public static QualityCheckItemResult checkVetoKeyword(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkVetoKeyword(bidDoc);
    }

    public static QualityCheckItemResult checkCommitmentSeal(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkCommitmentSeal(bidDoc);
    }

    public static QualityCheckItemResult checkIdAuthLetter(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkIdAuthLetter(bidDoc);
    }

    public static QualityCheckItemResult checkBidOpeningTable(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkBidOpeningTable(bidDoc);
    }

    public static QualityCheckItemResult checkDepositReceipt(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, null);
    }

    public static QualityCheckItemResult checkDepositReceipt(final DocumentContent bidDoc, final String tenderText) {
        return SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkCaCertificate(bidDoc, null);
    }

    public static QualityCheckItemResult checkCaCertificate(final DocumentContent bidDoc, final String tenderText) {
        return SubItemAndAttachmentRules.checkCaCertificate(bidDoc, tenderText);
    }

    public static QualityCheckItemResult checkDepositRefund(final DocumentContent bidDoc) {
        return SubItemAndAttachmentRules.checkDepositRefund(bidDoc, null);
    }

    public static QualityCheckItemResult checkDepositRefund(final DocumentContent bidDoc, final String tenderText) {
        return SubItemAndAttachmentRules.checkDepositRefund(bidDoc, tenderText);
    }

    public static String convertArabicToChinese(final BigDecimal amount) {
        return ChineseAmountConverter.toChineseAmount(amount);
    }

    public static BigDecimal convertChineseToArabic(final String chinese) {
        return ChineseAmountConverter.convertChineseToNumber(chinese);
    }

    public static BigDecimal convertChineseToNumber(final String chinese) {
        return ChineseAmountConverter.convertChineseToNumber(chinese);
    }
}
