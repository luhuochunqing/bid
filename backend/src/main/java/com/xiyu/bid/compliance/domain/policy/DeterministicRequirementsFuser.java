// Input: LLM 提取的 TenderRequirements + 招标文件 DocumentContent
// Output: 融合确定性规则硬事实后的 TenderRequirements（原文确定性事实优先）
// Pos: compliance/domain/policy/ - 确定性招标要求融合器（纯核心策略）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.List;

/**
 * 确定性招标要求融合器（纯核心策略）.
 *
 * <p>将大模型提取的招标要求与纯核心确定性规则提取的硬事实（资质、保证金、投标函、
 * 授权书、执照、身份证、财务、报价、CA、前附表等）进行确定性优先融合，
 * 防大模型幻觉与格式漂移.
 */
public final class DeterministicRequirementsFuser {

    private DeterministicRequirementsFuser() {
    }

    /**
     * 执行全量确定性招标要求融合.
     */
    public static TenderRequirements fuseAll(final TenderRequirements base, final DocumentContent tenderDoc) {
        if (base == null || tenderDoc == null || tenderDoc.fullText() == null || tenderDoc.fullText().isBlank()) {
            return base == null ? TenderRequirements.missingSentinel() : base;
        }
        TenderRequirements req = base;
        String text = tenderDoc.fullText();

        List<String> detQuals = DeterministicQualificationExtractor.extract(text);
        if (!detQuals.isEmpty()) {
            req = req.withMergedQualifications(detQuals);
        }
        DepositRequirement detDeposit = DeterministicDepositExtractor.extract(text);
        if (detDeposit != null) {
            req = req.withDepositRequirement(DepositRequirement.fuseWithDeterministic(req.depositRequirement(), detDeposit));
        }
        BidLetterRequirement detBidLetter = DeterministicBidLetterExtractor.extract(text);
        if (detBidLetter != null) {
            req = req.withBidLetterRequirement(BidLetterRequirement.fuseWithDeterministic(req.bidLetterRequirement(), detBidLetter));
        }
        AuthorizationRequirement detAuth = DeterministicAuthorizationExtractor.extract(text);
        if (detAuth != null) {
            req = req.withAuthorizationRequirement(AuthorizationRequirement.fuseWithDeterministic(req.authorizationRequirement(), detAuth));
        }
        BusinessLicenseRequirement detLicense = DeterministicBusinessLicenseExtractor.extract(text);
        if (detLicense != null) {
            req = req.withBusinessLicenseRequirement(BusinessLicenseRequirement.fuseWithDeterministic(req.businessLicenseRequirement(), detLicense));
        }
        IdAuthLetterRequirement detIdAuth = DeterministicIdAuthLetterExtractor.extract(text);
        if (detIdAuth != null) {
            req = req.withIdAuthLetterRequirement(IdAuthLetterRequirement.fuse(req.idAuthLetterRequirement(), detIdAuth));
        }
        FinancialRequirement detFinancial = DeterministicFinancialExtractor.extract(text);
        if (detFinancial != null) {
            req = req.withFinancialRequirement(FinancialRequirement.fuseWithDeterministic(req.financialRequirement(), detFinancial));
        }
        PriceRequirement detPrice = DeterministicPriceExtractor.extract(text);
        if (detPrice != null) {
            req = req.withPriceRequirement(PriceRequirement.fuseWithDeterministic(req.priceRequirement(), detPrice));
        }
        CaCertificateRequirement detCa = DeterministicCaCertificateExtractor.extract(text);
        if (detCa != null) {
            req = req.withCaCertificateRequirement(CaCertificateRequirement.fuseWithDeterministic(req.caCertificateRequirement(), detCa));
        }
        FrontTableRequirement detFrontTable = DeterministicFrontTableExtractor.extract(tenderDoc);
        if (detFrontTable != null) {
            req = req.withFrontTableRequirement(FrontTableRequirement.fuse(req.frontTableRequirement(), detFrontTable));
        }
        return req;
    }
}
