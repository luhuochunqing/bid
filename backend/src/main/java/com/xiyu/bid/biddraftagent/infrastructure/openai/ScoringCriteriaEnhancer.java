// Input: TenderRequirementProfile（AI 合并结果）+ 招标文件全文
// Output: 增强后的 TenderRequirementProfile（评分标准用正则兜底覆盖）
// Pos: biddraftagent/infrastructure/openai - 评分标准聚焦增强器
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;

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

        return replaceScoringCriteria(profile, regexItems);
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
