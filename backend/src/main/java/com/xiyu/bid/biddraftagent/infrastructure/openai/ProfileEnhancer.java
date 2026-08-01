// Input: TenderRequirementProfile（AI 合并结果）+ 招标文件全文
// Output: 增强后的 TenderRequirementProfile（5 个维度用正则兜底覆盖）
// Pos: biddraftagent/infrastructure/openai - 通用 Profile 增强器
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;

import java.util.List;
import java.util.Optional;

/**
 * 通用 Profile 增强器。
 *
 * <p>在 AI 分析完成后，用 SectionLocator + RequirementExtractor 正则兜底
 * 增强 5 个维度的提取结果。评分标准维度复用 ScoringCriteriaEnhancer 逻辑。
 *
 * <p>策略（正则优先，与 PurchaserNameExtractor 一致）：
 * <ol>
 *   <li>评分标准：委托 ScoringCriteriaEnhancer（含权重归一化）</li>
 *   <li>资质/技术/商务/风险：定位章节 -> 提取需求条目 -> 覆盖 AI 结果</li>
 *   <li>定位不到章节或提取为空 -> 保持 AI 原结果</li>
 * </ol>
 */
final class ProfileEnhancer {

    private ProfileEnhancer() {
    }

    /**
     * 增强 profile 中 5 个维度的提取结果。
     *
     * @param profile  AI 合并后的分析结果
     * @param fullText 招标文件全文文本
     * @return 增强后的 profile
     */
    static TenderRequirementProfile enhance(TenderRequirementProfile profile, String fullText) {
        if (profile == null) {
            throw new NullPointerException("profile must not be null");
        }
        if (fullText == null || fullText.isBlank()) {
            return profile;
        }

        // 评分标准：委托 ScoringCriteriaEnhancer
        TenderRequirementProfile enhanced = ScoringCriteriaEnhancer.enhance(profile, fullText);

        // 资质要求
        List<String> qualification = extractRequirements(fullText, SectionAliases.QUALIFICATION);
        // 技术要求
        List<String> technical = extractRequirements(fullText, SectionAliases.TECHNICAL);
        // 商务要求
        List<String> commercial = extractRequirements(fullText, SectionAliases.COMMERCIAL);
        // 风险条款
        List<String> risk = extractRequirements(fullText, SectionAliases.RISK);

        return new TenderRequirementProfile(
                enhanced.projectName(),
                enhanced.tenderTitle(),
                enhanced.tenderScope(),
                enhanced.purchaserName(),
                enhanced.budget(),
                enhanced.region(),
                enhanced.industry(),
                enhanced.publishDate(),
                enhanced.deadline(),
                qualification.isEmpty() ? enhanced.qualificationRequirements() : qualification,
                technical.isEmpty() ? enhanced.technicalRequirements() : technical,
                commercial.isEmpty() ? enhanced.commercialRequirements() : commercial,
                enhanced.scoringCriteria(),
                enhanced.scoringCriteriaItems(),
                enhanced.deadlineText(),
                enhanced.requiredMaterials(),
                risk.isEmpty() ? enhanced.riskPoints() : risk,
                enhanced.tags(),
                enhanced.items()
        );
    }

    private static List<String> extractRequirements(String fullText, List<String> aliases) {
        Optional<String> section = SectionLocator.locate(fullText, aliases);
        if (section.isEmpty()) {
            return List.of();
        }
        return RequirementExtractor.extract(section.get(), aliases);
    }
}
