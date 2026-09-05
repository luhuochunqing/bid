// Input: LLM 结构化提取或规则提取的五类招标要求列表（contracts/tender-requirements-extraction.md §2）+ 保证金要求
// Output: 招标要求纯核心模型（评分项/专用资质证书/否决条款/组成要求/格式要求/保证金要求）+ missing 哨兵
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
    }

    /** 兼容历史签名的 6 参数构造方法. */
    public TenderRequirements(
            final List<ScoringItem> scoringItems,
            final List<String> mandatoryQualifications,
            final List<VetoClause> vetoClauses,
            final List<String> compositionRequirements,
            final List<String> formatRequirements,
            final boolean missing) {
        this(scoringItems, mandatoryQualifications, vetoClauses, compositionRequirements, formatRequirements, null, missing);
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
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), null, false);
    }

    /** 提取失败/未启用哨兵值：比对项 MANUAL + REFERENCE_MISSING，独立项照常. */
    public static TenderRequirements missingSentinel() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), null, true);
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
        return new TenderRequirements(
                scoringItems,
                List.copyOf(merged),
                vetoClauses,
                compositionRequirements,
                formatRequirements,
                depositRequirement,
                false
        );
    }

    /** 注入保证金要求. */
    public TenderRequirements withDepositRequirement(final DepositRequirement depositRequirement) {
        return new TenderRequirements(
                scoringItems,
                mandatoryQualifications,
                vetoClauses,
                compositionRequirements,
                formatRequirements,
                depositRequirement,
                missing
        );
    }

    /** 合并多段提取结果（长文档分段送入时）；任一段为哨兵不影响其他段，全空则哨兵. */
    public static TenderRequirements merge(final List<TenderRequirements> parts) {
        List<ScoringItem> scoringItems = new ArrayList<>();
        LinkedHashSet<String> qualifications = new LinkedHashSet<>();
        List<VetoClause> vetoClauses = new ArrayList<>();
        LinkedHashSet<String> composition = new LinkedHashSet<>();
        LinkedHashSet<String> format = new LinkedHashSet<>();
        DepositRequirement mergedDeposit = null;
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
        }
        boolean allMissing = parts.isEmpty() || parts.stream().allMatch(p -> p == null || p.missing());
        return new TenderRequirements(scoringItems, List.copyOf(qualifications), vetoClauses,
                List.copyOf(composition), List.copyOf(format), mergedDeposit, allMissing);
    }
}
