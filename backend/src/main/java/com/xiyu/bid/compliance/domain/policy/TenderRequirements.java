// Input: LLM 结构化提取或规则提取的招标要求（评分/资质/否决/组成/格式 + 保证金/投标函/授权/执照契约）
// Output: 招标要求纯核心模型（含各专项契约）+ missing 哨兵
// Pos: compliance/domain/policy/ - 招标要求领域模型（spec 049 US2，data-model.md §3.1）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 招标要求结构化提取产物（纯核心 record）.
 *
 * <p>来源为真实招标文件的 LLM 结构化提取；提取失败 / AI 未启用 / 招标未上传
 * 统一落为 {@link #missingSentinel()} 哨兵，比对项 MANUAL + REFERENCE_MISSING，禁止阻断任务.
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
        boolean missing) {

    /** 投标保证金要求. required 为 false 表示明确免收/不涉及；true 为需缴纳；null 为未明确. */
    public record DepositRequirement(
            Boolean required,
            String amount,
            String forms,
            String description) {

        public static DepositRequirement notRequired(final String description) {
            return new DepositRequirement(false, null, null, description);
        }

        public static DepositRequirement required(final String amount, final String forms, final String description) {
            return new DepositRequirement(true, amount, forms, description);
        }

        /**
         * 交叉校验与融合两条保证金要求提取结果（硬事实优先，杜绝偏向免收放大幻觉）.
         */
        public static DepositRequirement fuse(final DepositRequirement a, final DepositRequirement b) {
            if (a == null) {
                return b;
            }
            if (b == null) {
                return a;
            }
            // 1. 若一方具备明确金额（硬事实），优先以具备金额的要求为准
            boolean aHasAmount = a.amount() != null && !a.amount().isBlank();
            boolean bHasAmount = b.amount() != null && !b.amount().isBlank();
            if (aHasAmount && !bHasAmount) {
                return a;
            }
            if (bHasAmount && !aHasAmount) {
                return b;
            }

            // 2. 若双方结论一致，保留更详细的描述与形式
            if (Boolean.TRUE.equals(a.required()) && Boolean.TRUE.equals(b.required())) {
                String forms = a.forms() != null ? a.forms() : b.forms();
                String amount = a.amount() != null ? a.amount() : b.amount();
                return new DepositRequirement(true, amount, forms, a.description() != null ? a.description() : b.description());
            }
            if (Boolean.FALSE.equals(a.required()) && Boolean.FALSE.equals(b.required())) {
                return new DepositRequirement(false, null, null, a.description() != null ? a.description() : b.description());
            }

            // 3. 冲突防御：一方声称需缴纳（但无明确金额），另一方声称免收
            // 绝不无脑偏向免收！保持审慎合规原则，保留要求缴纳并附带冲突说明
            return new DepositRequirement(true, null, null, "提取结果存在分歧（条款与前附表表述不一致），按审慎合规原则提示缴纳并建议人工复核");
        }

        /**
         * 双轨融合：以确定性提取结果校准 LLM 提取结果.
         * 1. 强事实优先：若双方均提取出明确金额（双有数），坚决以确定性规则从原文正则提取的精准金额为准，防止 LLM 幻觉或改写覆盖原文；
         * 2. 丰富描述：若双方结论一致，保留 LLM 提取的更丰富形式与详细描述；
         * 3. 其余场景（单方有数、单方免收、条款冲突等）：遵循 fuse 审慎融合策略.
         */
        public static DepositRequirement fuseWithDeterministic(final DepositRequirement llmReq, final DepositRequirement detReq) {
            if (detReq == null) {
                return llmReq;
            }
            if (llmReq == null) {
                return detReq;
            }
            boolean detHasAmount = detReq.amount() != null && !detReq.amount().isBlank();
            boolean llmHasAmount = llmReq.amount() != null && !llmReq.amount().isBlank();
            if (detHasAmount && llmHasAmount) {
                // 双有数：以确定性原文金额为准，防止大模型改写原文金额
                String forms = llmReq.forms() != null ? llmReq.forms() : detReq.forms();
                String desc = llmReq.description() != null ? llmReq.description() : detReq.description();
                return new DepositRequirement(true, detReq.amount(), forms, desc);
            }
            return fuse(llmReq, detReq);
        }
    }

    /** 投标函/报价函/响应函格式与内容要求契约. */
    public record BidLetterRequirement(
            String letterType,
            String recipientName,
            Integer validityDays,
            String description) {

        /**
         * 交叉校验与融合两条投标函要求提取结果（硬事实优先）.
         */
        public static BidLetterRequirement fuse(final BidLetterRequirement a, final BidLetterRequirement b) {
            if (a == null) return b;
            if (b == null) return a;
            String type = a.letterType() != null && !a.letterType().isBlank() ? a.letterType() : b.letterType();
            String recipient = a.recipientName() != null && !a.recipientName().isBlank() ? a.recipientName() : b.recipientName();
            Integer days = a.validityDays() != null ? a.validityDays() : b.validityDays();
            String desc = a.description() != null && !a.description().isBlank() ? a.description() : b.description();
            return new BidLetterRequirement(type, recipient, days, desc);
        }

        /**
         * 双轨融合：以确定性提取结果校准 LLM 提取结果（硬事实数字天数与确定性公司全称优先）.
         */
        public static BidLetterRequirement fuseWithDeterministic(final BidLetterRequirement llmReq, final BidLetterRequirement detReq) {
            if (detReq == null) return llmReq;
            if (llmReq == null) return detReq;
            Integer days = detReq.validityDays() != null ? detReq.validityDays() : llmReq.validityDays();
            String type = detReq.letterType() != null && !detReq.letterType().isBlank() ? detReq.letterType() : llmReq.letterType();
            String recipient = detReq.recipientName() != null && !detReq.recipientName().isBlank() ? detReq.recipientName() : llmReq.recipientName();
            String desc = llmReq.description() != null && !llmReq.description().isBlank() ? llmReq.description() : detReq.description();
            return new BidLetterRequirement(type, recipient, days, desc);
        }
    }

    /** 兼容历史签名的构造重载. */
    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, null, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, null, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, null, null, missing);
    }

    public TenderRequirements(final List<ScoringItem> scoringItems, final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses, final List<String> compositionRequirements, final List<String> formatRequirements,
            final DepositRequirement depositRequirement, final BidLetterRequirement bidLetterRequirement,
            final AuthorizationRequirement authorizationRequirement, final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, null, missing);
    }

    /** 评分项. maxScore 允许 null（文本未标分值时），负值归一为 null. */
    public record ScoringItem(String name, Double maxScore, String criteria) {
        public ScoringItem {
            if (maxScore != null && maxScore < 0) {
                maxScore = null;
            }
        }
    }

    /** 否决条款（keywords 为否决关键词语义判断的候选词）. */
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

    /** 无提取结果的空集（非哨兵）. */
    public static TenderRequirements empty() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null, false);
    }

    /** 提取失败/未启用哨兵值：比对项 MANUAL + REFERENCE_MISSING，独立项照常. */
    public static TenderRequirements missingSentinel() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null, true);
    }

    /**
     * 双轨融合：将规则提取的确定性资格要求与现有要求合并（并集去重保序）.
     * 若本对象原为 missingSentinel，但在注入确定性资质后，将解除 missing 标记，确保后续资质比对正常执行.
     */
    public TenderRequirements withMergedQualifications(final List<String> extraQualifications) {
        if (extraQualifications == null || extraQualifications.isEmpty()) {
            return this;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(extraQualifications);
        merged.addAll(this.mandatoryQualifications);
        return new TenderRequirements(scoringItems, sanitizeQualifications(List.copyOf(merged)), vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, false);
    }

    /** 注入保证金要求. */
    public TenderRequirements withDepositRequirement(final DepositRequirement depositRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, missing);
    }

    /** 注入投标函格式要求. */
    public TenderRequirements withBidLetterRequirement(final BidLetterRequirement bidLetterRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, missing);
    }

    /** 注入授权委托书要求. */
    public TenderRequirements withAuthorizationRequirement(final AuthorizationRequirement authorizationRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, missing);
    }

    /** 注入营业执照要求. */
    public TenderRequirements withBusinessLicenseRequirement(final BusinessLicenseRequirement businessLicenseRequirement) {
        return new TenderRequirements(scoringItems, mandatoryQualifications, vetoClauses,
                compositionRequirements, formatRequirements, depositRequirement, bidLetterRequirement, authorizationRequirement, businessLicenseRequirement, missing);
    }

    /** 合并多段提取结果（长文档分段送入时）；任一段为哨兵不影响其他段，全空则哨兵. */
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
        }
        boolean allMissing = parts.isEmpty() || parts.stream().allMatch(p -> p == null || p.missing());
        return new TenderRequirements(scoringItems, List.copyOf(qualifications), vetoClauses,
                List.copyOf(composition), List.copyOf(format), mergedDeposit, mergedBidLetter, mergedAuth, mergedLicense, allMissing);
    }
}
