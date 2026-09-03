// Input: LLM 结构化提取后的五类招标要求列表（contracts/tender-requirements-extraction.md §2）
// Output: 招标要求纯核心模型（评分项/硬性要求/否决条款/组成要求/格式要求）+ missing 哨兵
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
        boolean missing) {

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
        mandatoryQualifications = mandatoryQualifications == null ? List.of() : List.copyOf(mandatoryQualifications);
        vetoClauses = vetoClauses == null ? List.of() : List.copyOf(vetoClauses);
        compositionRequirements = compositionRequirements == null ? List.of() : List.copyOf(compositionRequirements);
        formatRequirements = formatRequirements == null ? List.of() : List.copyOf(formatRequirements);
    }

    /** 无提取结果的空集（非哨兵）. */
    public static TenderRequirements empty() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }

    /** 提取失败/未启用哨兵值：比对项 MANUAL + REFERENCE_MISSING，独立项照常. */
    public static TenderRequirements missingSentinel() {
        return new TenderRequirements(List.of(), List.of(), List.of(), List.of(), List.of(), true);
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
                false
        );
    }


    /** 合并多段提取结果（长文档分段送入时）；任一段为哨兵不影响其他段，全空则哨兵. */
    public static TenderRequirements merge(final List<TenderRequirements> parts) {
        List<ScoringItem> scoringItems = new ArrayList<>();
        LinkedHashSet<String> qualifications = new LinkedHashSet<>();
        List<VetoClause> vetoClauses = new ArrayList<>();
        LinkedHashSet<String> composition = new LinkedHashSet<>();
        LinkedHashSet<String> format = new LinkedHashSet<>();
        for (TenderRequirements part : parts) {
            if (part == null || part.missing()) {
                continue;
            }
            scoringItems.addAll(part.scoringItems());
            qualifications.addAll(part.mandatoryQualifications());
            vetoClauses.addAll(part.vetoClauses());
            composition.addAll(part.compositionRequirements());
            format.addAll(part.formatRequirements());
        }
        boolean allMissing = parts.isEmpty() || parts.stream().allMatch(p -> p == null || p.missing());
        return new TenderRequirements(scoringItems, List.copyOf(qualifications), vetoClauses,
                List.copyOf(composition), List.copyOf(format), allMissing);
    }
}
