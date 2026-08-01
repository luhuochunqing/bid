// Input: AI 分析产出的 ScoringCriterion 列表
// Output: 转换为 ProjectScoreDraft 实体列表（复用 ScoreDraftSeedFactory 推断任务字段）
// Pos: projectworkflow/parser - AI 分析结果到评分草稿的转换器

package com.xiyu.bid.projectworkflow.parser;

import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaSubType;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 AI 分析产出的 ScoringCriterion 列表转换为 ProjectScoreDraft 实体。
 * 复用 ScoreDraftSeedFactory 推断 taskAction / generatedTaskTitle 等字段，
 * 复用 ProjectScoreDraftMapper 构建 Draft 实体。
 */
@Component
public class ScoreDraftFromProfileAssembler {

    private final ProjectScoreDraftMapper draftMapper;

    public ScoreDraftFromProfileAssembler(ProjectScoreDraftMapper draftMapper) {
        this.draftMapper = draftMapper;
    }

    public List<ProjectScoreDraft> assemble(Long projectId, String sourceFileName, List<ScoringCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return List.of();
        }

        List<ProjectScoreDraft> drafts = new ArrayList<>();
        int index = 0;
        for (ScoringCriterion criterion : criteria) {
            DraftSeed seed = toSeed(criterion);
            String category = mapCategory(criterion);
            drafts.add(draftMapper.buildDraft(projectId, sourceFileName, category, seed, index, index));
            index++;
        }
        return drafts;
    }

    private DraftSeed toSeed(ScoringCriterion criterion) {
        String scoreItemTitle = criterion.indicator();
        String ruleText = buildRuleText(criterion);
        String scoreText = ScoreDraftSeedFactory.formatScoreText(
                criterion.weight() != null ? criterion.weight().toPlainString() : null);
        return ScoreDraftSeedFactory.buildSeed(scoreItemTitle, scoreItemTitle, ruleText, scoreText);
    }

    private String buildRuleText(ScoringCriterion criterion) {
        String dimension = criterion.dimension() != null && !criterion.dimension().isBlank()
                ? criterion.dimension() : "";
        String indicator = criterion.indicator() != null ? criterion.indicator() : "";
        if (dimension.isEmpty()) {
            return indicator;
        }
        return dimension + " / " + indicator;
    }

    private String mapCategory(ScoringCriterion criterion) {
        ScoringCriteriaSubType subType = criterion.subType();
        if (subType == null) {
            return "business";
        }
        return switch (subType) {
            case PRICE_WEIGHT -> "price";
            case TECHNICAL_EVALUATION -> "technical";
            default -> "business";
        };
    }
}
