// Input: TenderRequirementProfile（AI 合并结果）+ 招标文件全文
// Output: 增强后的 TenderRequirementProfile（评分标准用正则兜底覆盖）
// Pos: biddraftagent/infrastructure/openai - 评分标准聚焦增强器
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 评分标准聚焦增强器。
 *
 * <p>设计动机：招标文件按 4000 字分块送 AI 提取，评分表可能被切断导致 AI 漏提或错提。
 * 本类在 AI 分析完成后，用 {@link ScoringSectionLocator} 定位评分标准章节，
 * 再用 {@link ScoringItemExtractor} 正则提取结构化评分项，覆盖 AI 结果。
 *
 * <p>策略（正则优先，与 {@link PurchaserNameExtractor} 一致）：
 * <ol>
 *   <li>定位评分标准章节文本</li>
 *   <li>正则提取评分项</li>
 *   <li>正则提取到结果 -> 覆盖 AI 的 scoringCriteriaItems</li>
 *   <li>正则没提取到 -> 保持 AI 原结果（AI 可能从语义层面理解了非结构化格式）</li>
 *   <li>定位不到章节 -> 保持 AI 原结果</li>
 * </ol>
 *
 * <p>后续步骤：如果正则也没提取到但定位到了章节文本，可以用聚焦 prompt 单独送 AI
 * 做精炼分析（本版本暂不实现，预留扩展点）。
 */
final class ScoringCriteriaEnhancer {

    private ScoringCriteriaEnhancer() {
    }

    /**
     * 用正则兜底增强 AI 分析结果中的评分标准部分。
     *
     * @param profile  AI 合并后的分析结果
     * @param fullText 招标文件全文文本
     * @return 增强后的 profile（评分标准被正则结果覆盖时返回新实例，否则返回原 profile）
     */
    static TenderRequirementProfile enhance(TenderRequirementProfile profile, String fullText) {
        if (profile == null) {
            throw new NullPointerException("profile must not be null");
        }
        if (fullText == null || fullText.isBlank()) {
            return profile;
        }

        Optional<String> sectionOpt = ScoringSectionLocator.locate(fullText);
        if (sectionOpt.isEmpty()) {
            return profile;
        }

        List<ScoringCriterion> regexItems = ScoringItemExtractor.extract(sectionOpt.get());
        if (regexItems.isEmpty()) {
            return profile;
        }

        List<ScoringCriterion> normalized = normalizeWeights(regexItems);
        return replaceScoringCriteria(profile, normalized);
    }

    /**
     * 校验并归一化权重：有权重项的总和应等于 100。
     *
     * <p>策略：
     * <ul>
     *   <li>有权重项总和 = 100 -> 不变</li>
     *   <li>有权重项总和 != 100 且 > 0 -> 按比例归一化到 100</li>
     *   <li>有权重项总和 = 0 或全部为 null -> 不变</li>
     * </ul>
     *
     * @param items 正则提取的评分项列表
     * @return 归一化后的列表（可能原样返回）
     */
    private static List<ScoringCriterion> normalizeWeights(List<ScoringCriterion> items) {
        List<ScoringCriterion> weighted = items.stream()
                .filter(item -> item.weight() != null && item.weight().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (weighted.isEmpty()) {
            return items;
        }

        BigDecimal total = weighted.stream()
                .map(ScoringCriterion::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(new BigDecimal("100")) == 0) {
            return items;
        }

        // 归一化：weight = weight * 100 / total
        BigDecimal factor = new BigDecimal("100").divide(total, 10, RoundingMode.HALF_UP);
        return items.stream()
                .map(item -> item.weight() != null && item.weight().compareTo(BigDecimal.ZERO) > 0
                        ? new ScoringCriterion(
                                item.itemNumber(),
                                item.dimension(),
                                item.indicator(),
                                item.weight().multiply(factor).setScale(2, RoundingMode.HALF_UP),
                                item.subType())
                        : item)
                .toList();
    }

    /**
     * 用正则提取的结果替换 profile 中的评分标准部分。
     * 同时更新 scoringCriteria 原文列表，使其与结构化条目一致。
     */
    private static TenderRequirementProfile replaceScoringCriteria(
            TenderRequirementProfile profile, List<ScoringCriterion> items) {

        List<String> criteriaTexts = new ArrayList<>();
        for (ScoringCriterion item : items) {
            StringBuilder sb = new StringBuilder();
            if (item.itemNumber() != null) {
                sb.append(item.itemNumber()).append(" ");
            }
            sb.append(item.dimension());
            if (item.indicator() != null && !item.indicator().isBlank()) {
                sb.append(" ").append(item.indicator());
            }
            if (item.weight() != null) {
                sb.append(" ").append(item.weight()).append("分");
            }
            criteriaTexts.add(sb.toString());
        }

        return new TenderRequirementProfile(
                profile.projectName(),
                profile.tenderTitle(),
                profile.tenderScope(),
                profile.purchaserName(),
                profile.budget(),
                profile.region(),
                profile.industry(),
                profile.publishDate(),
                profile.deadline(),
                profile.qualificationRequirements(),
                profile.technicalRequirements(),
                profile.commercialRequirements(),
                criteriaTexts,
                items,
                profile.deadlineText(),
                profile.requiredMaterials(),
                profile.riskPoints(),
                profile.tags(),
                profile.items()
        );
    }
}
