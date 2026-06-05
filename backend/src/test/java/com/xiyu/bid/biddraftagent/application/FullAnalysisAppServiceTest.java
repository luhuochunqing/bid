// Input: FullAnalysisAppService（5 个 AppService 聚合 + RiskSummary 计算）
// Output: FullAnalysisResult 聚合逻辑验证
// Pos: Test/biddraftagent/application

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.risk.RiskItem;
import com.xiyu.bid.biddraftagent.domain.risk.RiskLevel;
import com.xiyu.bid.biddraftagent.domain.validation.BrandAuthMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult.KnowledgeBaseSummary;
import com.xiyu.bid.biddraftagent.domain.validation.PerformanceMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.PersonnelCertMatcher;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationMatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FullAnalysisAppServiceTest {

    @Mock
    private KnowledgeBaseMatchAppService knowledgeBaseMatchAppService;

    @Mock
    private ScoringCriteriaClassificationAppService scoringCriteriaAppService;

    @Mock
    private TechnicalClassificationAppService technicalClassificationAppService;

    @Mock
    private CommercialClassificationAppService commercialClassificationAppService;

    @Mock
    private RiskClassificationAppService riskClassificationAppService;

    @InjectMocks
    private FullAnalysisAppService fullAnalysisAppService;

    @Test
    void analyzeForProject_shouldAggregateAllResults() {
        when(knowledgeBaseMatchAppService.matchForProject(1L)).thenReturn(emptyKbMatch());
        when(scoringCriteriaAppService.classifyForProject(1L)).thenReturn(emptyScoringResult());
        when(technicalClassificationAppService.classifyForProject(1L)).thenReturn(emptyTechnicalResult());
        when(commercialClassificationAppService.classifyForProject(1L)).thenReturn(emptyCommercialResult());
        when(riskClassificationAppService.classifyForProject(1L)).thenReturn(emptyRiskResult());

        var result = fullAnalysisAppService.analyzeForProject(1L);

        assertThat(result).isNotNull();
        assertThat(result.knowledgeBaseMatch()).isNotNull();
        assertThat(result.scoringCriteria()).isNotNull();
        assertThat(result.technicalRequirements()).isNotNull();
        assertThat(result.commercialRequirements()).isNotNull();
        assertThat(result.riskClassification()).isNotNull();
        assertThat(result.riskSummary()).isNotNull();
    }

    @Test
    void riskSummary_shouldCountRedLineItems() {
        when(knowledgeBaseMatchAppService.matchForProject(1L)).thenReturn(emptyKbMatch());
        when(scoringCriteriaAppService.classifyForProject(1L)).thenReturn(emptyScoringResult());
        when(technicalClassificationAppService.classifyForProject(1L)).thenReturn(emptyTechnicalResult());
        when(commercialClassificationAppService.classifyForProject(1L)).thenReturn(emptyCommercialResult());

        var riskItems = List.of(
                new RiskItem("不符合资格要求", RiskLevel.RED_LINE),
                new RiskItem("保证金条款需关注", RiskLevel.WARNING),
                new RiskItem("未提供原件", RiskLevel.RED_LINE)
        );
        when(riskClassificationAppService.classifyForProject(1L))
                .thenReturn(new RiskClassificationAppService.RiskClassificationResult(riskItems));

        var result = fullAnalysisAppService.analyzeForProject(1L);

        assertThat(result.riskSummary().redLineCount()).isEqualTo(2);
    }

    @Test
    void riskSummary_shouldCountKbUnsatisfiedAndAttention() {
        var kbMatch = new KnowledgeBaseMatchResult(
                new QualificationMatchResult(List.of()),
                new PersonnelCertMatcher.PersonnelMatchResult(List.of()),
                new BrandAuthMatcher.BrandAuthMatchResult(List.of()),
                new PerformanceMatcher.PerformanceMatchResult(List.of()),
                new KnowledgeBaseSummary(3, 2, 1)
        );
        when(knowledgeBaseMatchAppService.matchForProject(1L)).thenReturn(kbMatch);
        when(scoringCriteriaAppService.classifyForProject(1L)).thenReturn(emptyScoringResult());
        when(technicalClassificationAppService.classifyForProject(1L)).thenReturn(emptyTechnicalResult());
        when(commercialClassificationAppService.classifyForProject(1L)).thenReturn(emptyCommercialResult());
        when(riskClassificationAppService.classifyForProject(1L)).thenReturn(emptyRiskResult());

        var result = fullAnalysisAppService.analyzeForProject(1L);

        assertThat(result.riskSummary().unsatisfiedCount()).isEqualTo(1);
        assertThat(result.riskSummary().attentionCount()).isEqualTo(2);
    }

    @Test
    void riskSummary_shouldHandleNullRiskItems() {
        when(knowledgeBaseMatchAppService.matchForProject(1L)).thenReturn(emptyKbMatch());
        when(scoringCriteriaAppService.classifyForProject(1L)).thenReturn(emptyScoringResult());
        when(technicalClassificationAppService.classifyForProject(1L)).thenReturn(emptyTechnicalResult());
        when(commercialClassificationAppService.classifyForProject(1L)).thenReturn(emptyCommercialResult());
        when(riskClassificationAppService.classifyForProject(1L))
                .thenReturn(new RiskClassificationAppService.RiskClassificationResult(null));

        var result = fullAnalysisAppService.analyzeForProject(1L);

        assertThat(result.riskSummary().redLineCount()).isZero();
    }

    @Test
    void riskSummary_shouldHandleNullKbSummary() {
        var kbMatch = new KnowledgeBaseMatchResult(
                new QualificationMatchResult(List.of()),
                new PersonnelCertMatcher.PersonnelMatchResult(List.of()),
                new BrandAuthMatcher.BrandAuthMatchResult(List.of()),
                new PerformanceMatcher.PerformanceMatchResult(List.of()),
                null
        );
        when(knowledgeBaseMatchAppService.matchForProject(1L)).thenReturn(kbMatch);
        when(scoringCriteriaAppService.classifyForProject(1L)).thenReturn(emptyScoringResult());
        when(technicalClassificationAppService.classifyForProject(1L)).thenReturn(emptyTechnicalResult());
        when(commercialClassificationAppService.classifyForProject(1L)).thenReturn(emptyCommercialResult());
        when(riskClassificationAppService.classifyForProject(1L)).thenReturn(emptyRiskResult());

        var result = fullAnalysisAppService.analyzeForProject(1L);

        assertThat(result.riskSummary().unsatisfiedCount()).isZero();
        assertThat(result.riskSummary().attentionCount()).isZero();
    }

    @Test
    void riskSummary_allEmpty_shouldReturnZeroCounts() {
        when(knowledgeBaseMatchAppService.matchForProject(1L)).thenReturn(emptyKbMatch());
        when(scoringCriteriaAppService.classifyForProject(1L)).thenReturn(emptyScoringResult());
        when(technicalClassificationAppService.classifyForProject(1L)).thenReturn(emptyTechnicalResult());
        when(commercialClassificationAppService.classifyForProject(1L)).thenReturn(emptyCommercialResult());
        when(riskClassificationAppService.classifyForProject(1L)).thenReturn(emptyRiskResult());

        var result = fullAnalysisAppService.analyzeForProject(1L);

        assertThat(result.riskSummary().redLineCount()).isZero();
        assertThat(result.riskSummary().unsatisfiedCount()).isZero();
        assertThat(result.riskSummary().attentionCount()).isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static KnowledgeBaseMatchResult emptyKbMatch() {
        return new KnowledgeBaseMatchResult(
                new QualificationMatchResult(List.of()),
                new PersonnelCertMatcher.PersonnelMatchResult(List.of()),
                new BrandAuthMatcher.BrandAuthMatchResult(List.of()),
                new PerformanceMatcher.PerformanceMatchResult(List.of()),
                new KnowledgeBaseSummary(0, 0, 0));
    }

    private static ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult emptyScoringResult() {
        return ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult.empty();
    }

    private static TechnicalClassificationAppService.TechnicalClassificationResult emptyTechnicalResult() {
        return new TechnicalClassificationAppService.TechnicalClassificationResult(List.of());
    }

    private static CommercialClassificationAppService.CommercialClassificationResult emptyCommercialResult() {
        return new CommercialClassificationAppService.CommercialClassificationResult(List.of());
    }

    private static RiskClassificationAppService.RiskClassificationResult emptyRiskResult() {
        return new RiskClassificationAppService.RiskClassificationResult(List.of());
    }
}
