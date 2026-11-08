// Input: LLM 结构化提取或规则提取的招标要求（评分/资质/否决/组成/格式 + 各分项契约，自动清洗 HTML 标签）
// Output: 保留来源引文及精确条件身份的招标要求纯核心模型；组合ISO仅通过显式拆分映射适用性；含 missing 哨兵
// Pos: compliance/domain/policy/ - 招标要求领域模型（spec 049 US2，data-model.md §3.1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 招标要求结构化提取产物（纯核心 record，严格 <= 300 行）.
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
        FrontTableRequirement frontTableRequirement,
        BusinessTermsRequirement businessTermsRequirement,
        boolean missing,
        List<QualificationCondition> qualificationConditions) {

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement,
            final PriceRequirement priceRequirement, final CaCertificateRequirement caCertificateRequirement,
            final FrontTableRequirement frontTableRequirement, final BusinessTermsRequirement businessTermsRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement,
                idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement,
                frontTableRequirement, businessTermsRequirement, missing, null);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                null, null, null, null, null, null, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement,
                idAuthLetterRequirement, financialRequirement, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement,
            final PriceRequirement priceRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement,
                idAuthLetterRequirement, financialRequirement, priceRequirement, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement,
            final PriceRequirement priceRequirement, final CaCertificateRequirement caCertificateRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement,
                idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final BusinessLicenseRequirement businessLicenseRequirement,
            final IdAuthLetterRequirement idAuthLetterRequirement, final FinancialRequirement financialRequirement,
            final PriceRequirement priceRequirement, final CaCertificateRequirement caCertificateRequirement,
            final FrontTableRequirement frontTableRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement,
                idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement,
                frontTableRequirement, null, missing);
    }

    public record ScoringItem(String name, Double maxScore, String criteria) {
        public ScoringItem {
            if (maxScore != null && maxScore < 0) maxScore = null;
        }
    }

    public record VetoClause(String clause, List<String> keywords) {
        public VetoClause {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
        }
    }

    public TenderRequirements {
        scoringItems = scoringItems == null ? List.of() : List.copyOf(scoringItems);
        // 结构化条件已解码；模型 with*/merge 重建时只处理尚未覆盖的原始旧文本。
        List<QualificationCondition> existing = qualificationConditions == null ? List.of() : qualificationConditions;
        List<QualificationCondition> completed = new ArrayList<>(existing);
        for (String raw : mandatoryQualifications == null ? List.<String>of() : mandatoryQualifications) {
            if (raw == null || existing.stream().anyMatch(c -> c.text().equals(raw))) continue;
            for (String text : QualificationCondition.cleanTexts(List.of(QualificationConditionExtractor.stripHtml(raw)))) {
                if (completed.stream().noneMatch(c -> c.text().equals(text))) {
                    completed.add(new QualificationCondition(text, new QualificationCondition.Source("", "", null, raw), null));
                }
            }
        }
        qualificationConditions = completed.stream().distinct().toList();
        mandatoryQualifications = qualificationConditions.stream().map(QualificationCondition::text).distinct().toList();
        vetoClauses = vetoClauses == null ? List.of() : List.copyOf(vetoClauses);
        compositionRequirements = compositionRequirements == null ? List.of() : List.copyOf(compositionRequirements);
        formatRequirements = formatRequirements == null ? List.of() : List.copyOf(formatRequirements);
    }

    /** 资质库仅查询证书子集；完整资格条件不得因资料库能力而被过滤。 */
    public List<String> certificateRequirements() {
        return QualificationCondition.certificateQueries(qualificationConditions);
    }

    public boolean hasUnresolvedCertificateRequirements() {
        return qualificationConditions.stream().flatMap(c -> c.certificates().stream()).anyMatch(c ->
                c.applicability() == QualificationCondition.Applicability.UNKNOWN
                        || c.applicability() == QualificationCondition.Applicability.MANDATORY
                        && c.target() == QualificationCondition.Target.UNKNOWN);
    }

    public boolean isUnresolvedCertificateRequirement(final String requirement) {
        if (requirement == null || requirement.isBlank()) return false;
        var candidates = qualificationConditions.stream().flatMap(c -> c.certificates().stream())
                .filter(c -> c.requirementText().equals(requirement)).toList();
        if (candidates.isEmpty()) {
            candidates = qualificationConditions.stream().flatMap(c -> c.certificates().stream())
                    .filter(c -> QualificationMatchPolicy.expandCombinedIsoRequirements(
                            List.of(c.requirementText())).contains(requirement)).toList();
        }
        return candidates.stream().noneMatch(c ->
                c.applicability() == QualificationCondition.Applicability.MANDATORY && c.target() == QualificationCondition.Target.BIDDER);
    }

    public static TenderRequirements empty() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, false);
    }

    public static TenderRequirements missingSentinel() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, true);
    }

    public TenderRequirements withMergedQualifications(final List<String> extraQualifications) {
        if (extraQualifications == null || extraQualifications.isEmpty()) return this;
        LinkedHashSet<String> merged = new LinkedHashSet<>(extraQualifications);
        merged.addAll(this.mandatoryQualifications);
        return new TenderRequirements(scoringItems, List.copyOf(merged), vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement,
                businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement,
                frontTableRequirement, businessTermsRequirement, false, qualificationConditions);
    }

    public TenderRequirements withMergedQualificationConditions(final List<QualificationCondition> extra) {
        if (extra == null || extra.isEmpty()) return this;
        List<QualificationCondition> combined = new ArrayList<>(qualificationConditions);
        combined.addAll(extra);
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, false, combined);
    }

    public TenderRequirements withMergedVetoClauses(final List<VetoClause> extraClauses) {
        if (extraClauses == null || extraClauses.isEmpty()) return this;
        List<VetoClause> merged = new ArrayList<>(this.vetoClauses);
        for (VetoClause extra : extraClauses) {
            if (extra != null && extra.clause() != null && !extra.clause().isBlank()
                    && merged.stream().noneMatch(existing -> existing.clause().equals(extra.clause()))) {
                merged.add(extra);
            }
        }
        return new TenderRequirements(scoringItems, mandatoryQualifications, List.copyOf(merged),
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement,
                businessLicenseRequirement, idAuthLetterRequirement, financialRequirement, priceRequirement, caCertificateRequirement,
                frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withDepositRequirement(final DepositRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                req, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withBidLetterRequirement(final BidLetterRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, req, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withAuthorizationRequirement(final AuthorizationRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, req, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withBusinessLicenseRequirement(final BusinessLicenseRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, req, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withIdAuthLetterRequirement(final IdAuthLetterRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, req,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withFinancialRequirement(final FinancialRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                req, priceRequirement, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withPriceRequirement(final PriceRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, req, caCertificateRequirement, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withCaCertificateRequirement(final CaCertificateRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, req, frontTableRequirement, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withFrontTableRequirement(final FrontTableRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, req, businessTermsRequirement, missing, qualificationConditions);
    }

    public TenderRequirements withBusinessTermsRequirement(final BusinessTermsRequirement req) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements,
                depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, idAuthLetterRequirement,
                financialRequirement, priceRequirement, caCertificateRequirement, frontTableRequirement, req, missing, qualificationConditions);
    }

    public static TenderRequirements merge(final List<TenderRequirements> parts) {
        List<ScoringItem> scoringItems = new ArrayList<>();
        LinkedHashSet<String> qualifications = new LinkedHashSet<>();
        List<QualificationCondition> conditions = new ArrayList<>();
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
        FrontTableRequirement mergedFrontTable = null;
        BusinessTermsRequirement mergedTerms = null;
        for (TenderRequirements part : parts) {
            if (part == null || part.missing()) continue;
            scoringItems.addAll(part.scoringItems());
            qualifications.addAll(part.mandatoryQualifications());
            conditions.addAll(part.qualificationConditions());
            vetoClauses.addAll(part.vetoClauses());
            composition.addAll(part.compositionRequirements());
            format.addAll(part.formatRequirements());
            if (part.depositRequirement() != null) mergedDeposit = DepositRequirement.fuse(mergedDeposit, part.depositRequirement());
            if (part.bidLetterRequirement() != null) mergedBidLetter = BidLetterRequirement.fuse(mergedBidLetter, part.bidLetterRequirement());
            if (part.authorizationRequirement() != null) mergedAuth = AuthorizationRequirement.fuse(mergedAuth, part.authorizationRequirement());
            if (part.businessLicenseRequirement() != null) mergedLicense = BusinessLicenseRequirement.fuse(mergedLicense, part.businessLicenseRequirement());
            if (part.idAuthLetterRequirement() != null) mergedIdAuth = IdAuthLetterRequirement.fuse(mergedIdAuth, part.idAuthLetterRequirement());
            if (part.financialRequirement() != null) mergedFinancial = FinancialRequirement.fuse(mergedFinancial, part.financialRequirement());
            if (part.priceRequirement() != null) mergedPrice = PriceRequirement.fuse(mergedPrice, part.priceRequirement());
            if (part.caCertificateRequirement() != null) mergedCa = CaCertificateRequirement.fuse(mergedCa, part.caCertificateRequirement());
            if (part.frontTableRequirement() != null) mergedFrontTable = FrontTableRequirement.fuse(mergedFrontTable, part.frontTableRequirement());
            if (part.businessTermsRequirement() != null) mergedTerms = BusinessTermsRequirement.fuse(mergedTerms, part.businessTermsRequirement());
        }
        boolean allMissing = parts.isEmpty() || parts.stream().allMatch(p -> p == null || p.missing());
        return new TenderRequirements(scoringItems, List.copyOf(qualifications), vetoClauses,
                List.copyOf(composition), List.copyOf(format), mergedDeposit, mergedBidLetter, mergedAuth,
                mergedLicense, mergedIdAuth, mergedFinancial, mergedPrice, mergedCa, mergedFrontTable, mergedTerms, allMissing, conditions);
    }
}
