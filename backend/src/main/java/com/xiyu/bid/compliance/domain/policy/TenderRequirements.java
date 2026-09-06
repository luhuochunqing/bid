// Input: LLM 结构化提取或规则提取的招标要求（评分/资质/否决/组成/格式 + 保证金/投标函/授权/执照/身份证明/财务/报价要求契约）
// Output: 招标要求纯核心模型（评分项/专用资质证书/否决条款/组成要求/格式要求/保证金/投标函/授权书/执照/身份证明/财务/报价要求契约）+ missing 哨兵
// Pos: compliance/domain/policy/ - 招标要求领域模型（spec 049 US2，data-model.md §3.1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 招标要求结构化提取产物（纯核心 record）.
 */
public record TenderRequirements(
        List<ScoringItem> scoringItems,
        List<String> mandatoryQualifications,
        List<VetoClause> vetoClauses,
        List<String> compositionRequirements,
        List<String> formatRequirements,
        DepositRequirement depositRequirement,
        BidLetterRequirement bidLetterRequirement,
        AuthorizationRequirement authorizationRequirement,
        BusinessLicenseRequirement businessLicenseRequirement,
        IdAuthLetterRequirement idAuthLetterRequirement,
        FinancialRequirement financialRequirement,
        PriceRequirement priceRequirement,
        CaCertificateRequirement caCertificateRequirement,
        boolean missing) {

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, null, null, null, null, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, null, null, null, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, null, null, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, null, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement,
            final PriceRequirement priceRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, null, missing);
    }

    public record ScoringItem(String name, Double maxScore, String criteria) {
        public ScoringItem {
            if (maxScore != null && maxScore < 0) {
                maxScore = null;
            }
        }
    }

    public record VetoClause(String clause, List<String> keywords) {
        public VetoClause {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }

    public TenderRequirements {
        scoringItems = scoringItems == null ? List.of() : List.copyOf(scoringItems);
        mandatoryQualifications = sanitizeQualifications(mandatoryQualifications);
        vetoClauses = vetoClauses == null ? List.of() : List.copyOf(vetoClauses);
        compositionRequirements = compositionRequirements == null ? List.of() : List.copyOf(compositionRequirements);
        formatRequirements = formatRequirements == null ? List.of() : List.copyOf(formatRequirements);
    }

    private static List<String> sanitizeQualifications(final List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String item = DeterministicQualificationExtractor.cleanItem(s);
            if (item.isBlank() || DeterministicQualificationExtractor.isIntroductoryHeading(item)
                    || "/".equals(item) || "无".equals(item) || "不适用".equals(item)
                    || !DeterministicQualificationExtractor.isSpecificCertificate(item)) {
                continue;
            }
            cleaned.add(item);
        }
        return List.copyOf(cleaned);
    }

    public static TenderRequirements empty() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null, false);
    }

    public static TenderRequirements missingSentinel() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null, true);
    }

    public TenderRequirements withMergedQualifications(final List<String> extraQualifications) {
        if (extraQualifications == null || extraQualifications.isEmpty()) {
            return this;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(extraQualifications);
        merged.addAll(this.mandatoryQualifications);
        return new TenderRequirements(scoringItems, sanitizeQualifications(List.copyOf(merged)), vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, false);
    }

    public TenderRequirements withDepositRequirement(final DepositRequirement depositRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withBidLetterRequirement(final BidLetterRequirement bidLetterRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withAuthorizationRequirement(final AuthorizationRequirement authorizationRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withBusinessLicenseRequirement(final BusinessLicenseRequirement businessLicenseRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withIdAuthLetterRequirement(final IdAuthLetterRequirement idAuthLetterRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withFinancialRequirement(final FinancialRequirement financialRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withPriceRequirement(final PriceRequirement priceRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public TenderRequirements withCaCertificateRequirement(final CaCertificateRequirement caCertificateRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, missing);
    }

    public static TenderRequirements merge(final List<TenderRequirements> parts) {
        List<ScoringItem> scoringItems = new ArrayList<>();
        LinkedHashSet<String> qualifications = new LinkedHashSet<>();
        List<VetoClause> vetoClauses = new ArrayList<>();
        LinkedHashSet<String> composition = new LinkedHashSet<>();
        LinkedHashSet<String> format = new LinkedHashSet<>();
        DepositRequirement mergedDeposit = null;
        BidLetterRequirement mergedBidLetter = null;
        AuthorizationRequirement mergedAuth = null;
        BusinessLicenseRequirement mergedLicense = null;
        IdAuthLetterRequirement mergedIdAuth = null;
        FinancialRequirement mergedFinancial = null;
        PriceRequirement mergedPrice = null;
        CaCertificateRequirement mergedCa = null;
        for (TenderRequirements part : parts) {
            if (part == null || part.missing()) {
                continue;
            }
            scoringItems.addAll(part.scoringItems());
            qualifications.addAll(part.mandatoryQualifications());
            vetoClauses.addAll(part.vetoClauses());
            composition.addAll(part.compositionRequirements());
            format.addAll(part.formatRequirements());
            if (part.depositRequirement() != null) {
                mergedDeposit = DepositRequirement.fuse(mergedDeposit, part.depositRequirement());
            }
            if (part.bidLetterRequirement() != null) {
                mergedBidLetter = BidLetterRequirement.fuse(mergedBidLetter, part.bidLetterRequirement());
            }
            if (part.authorizationRequirement() != null) {
                mergedAuth = AuthorizationRequirement.fuse(mergedAuth, part.authorizationRequirement());
            }
            if (part.businessLicenseRequirement() != null) {
                mergedLicense = BusinessLicenseRequirement.fuse(mergedLicense, part.businessLicenseRequirement());
            }
            if (part.idAuthLetterRequirement() != null) {
                mergedIdAuth = IdAuthLetterRequirement.fuse(mergedIdAuth, part.idAuthLetterRequirement());
            }
            if (part.financialRequirement() != null) {
                mergedFinancial = FinancialRequirement.fuse(mergedFinancial, part.financialRequirement());
            }
            if (part.priceRequirement() != null) {
                mergedPrice = PriceRequirement.fuse(mergedPrice, part.priceRequirement());
            }
            if (part.caCertificateRequirement() != null) {
                mergedCa = CaCertificateRequirement.fuse(mergedCa, part.caCertificateRequirement());
            }
        }
        boolean allMissing = parts.isEmpty() || parts.stream().allMatch(p -> p == null || p.missing());
        return new TenderRequirements(scoringItems, List.copyOf(qualifications), vetoClauses,
                List.copyOf(composition), List.copyOf(format), mergedDeposit, mergedBidLetter, mergedAuth, mergedLicense, mergedIdAuth, mergedFinancial, mergedPrice, mergedCa, allMissing);
    }
}
