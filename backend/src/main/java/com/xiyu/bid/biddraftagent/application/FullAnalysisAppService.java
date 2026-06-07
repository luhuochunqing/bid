// Input: projectId → 全维度分析结果
// Output: FullAnalysisResult（5 个维度聚合结果 + 风险总览）
// Pos: biddraftagent/application — 全维度分析编排服务

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.risk.RiskItem;
import com.xiyu.bid.biddraftagent.domain.risk.RiskLevel;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全维度分析编排服务。
 * 串行调用 5 个 AppService，统一返回 + 风险总览。
 */
@Service
@RequiredArgsConstructor
public class FullAnalysisAppService {

    private final KnowledgeBaseMatchAppService knowledgeBaseMatchAppService;
    private final ScoringCriteriaClassificationAppService scoringCriteriaAppService;
    private final TechnicalClassificationAppService technicalClassificationAppService;
    private final CommercialClassificationAppService commercialClassificationAppService;
    private final RiskClassificationAppService riskClassificationAppService;

    public record FullAnalysisResult(
            KnowledgeBaseMatchResult knowledgeBaseMatch,
            ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult scoringCriteria,
            TechnicalClassificationAppService.TechnicalClassificationResult technicalRequirements,
            CommercialClassificationAppService.CommercialClassificationResult commercialRequirements,
            RiskClassificationAppService.RiskClassificationResult riskClassification,
            RiskSummary riskSummary
    ) {}

    public record RiskSummary(
            int redLineCount,
            int unsatisfiedCount,
            int attentionCount
    ) {}

    public FullAnalysisResult analyzeForProject(Long projectId) {
        KnowledgeBaseMatchResult kbMatch = knowledgeBaseMatchAppService.matchForProject(projectId);
        var scoring = scoringCriteriaAppService.classifyForProject(projectId);
        var technical = technicalClassificationAppService.classifyForProject(projectId);
        var commercial = commercialClassificationAppService.classifyForProject(projectId);
        var risk = riskClassificationAppService.classifyForProject(projectId);

        RiskSummary summary = computeRiskSummary(kbMatch, risk);

        return new FullAnalysisResult(kbMatch, scoring, technical, commercial, risk, summary);
    }

    private RiskSummary computeRiskSummary(
            KnowledgeBaseMatchResult kbMatch,
            RiskClassificationAppService.RiskClassificationResult risk) {

        int redLineCount = 0;
        int unsatisfiedCount = 0;
        int attentionCount = 0;

        if (risk != null && risk.items() != null) {
            redLineCount = (int) risk.items().stream()
                    .filter(i -> i.level() == RiskLevel.RED_LINE).count();
        }

        if (kbMatch != null && kbMatch.summary() != null) {
            unsatisfiedCount = kbMatch.summary().totalUnsatisfied();
            attentionCount = kbMatch.summary().totalAttention();
        }

        return new RiskSummary(redLineCount, unsatisfiedCount, attentionCount);
    }
}
