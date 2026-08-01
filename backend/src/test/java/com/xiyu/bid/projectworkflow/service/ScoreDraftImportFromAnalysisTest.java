package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.application.ScoringCriteriaClassificationAppService;
import com.xiyu.bid.biddraftagent.application.ScoringCriteriaClassificationAppService.ScoringCriteriaClassificationResult;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.ScoringCriteriaSubType;
import com.xiyu.bid.projectworkflow.dto.ProjectScoreDraftParseResponse;
import com.xiyu.bid.projectworkflow.parser.ProjectScoreDraftMapper;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftFromProfileAssembler;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreDraftImportFromAnalysisTest {

    private ProjectScoreDraftRepository repository;
    private ScoringCriteriaClassificationAppService classificationService;
    private ScoreDraftImportWorkflowService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        repository = mock(ProjectScoreDraftRepository.class);
        classificationService = mock(ScoringCriteriaClassificationAppService.class);

        ProjectWorkflowGuardService guardService = mock(ProjectWorkflowGuardService.class);
        ProjectScoreDraftMapper draftMapper = new ProjectScoreDraftMapper(objectMapper);
        ScoreDraftFromProfileAssembler assembler = new ScoreDraftFromProfileAssembler(draftMapper);

        service = new ScoreDraftImportWorkflowService(
                guardService,
                repository,
                classificationService,
                assembler,
                draftMapper
        );
    }

    @Test
    void importFromAiAnalysis_ShouldConvertAndPersistDrafts() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("1", "技术方案", "整体架构", new BigDecimal("30"), ScoringCriteriaSubType.TECHNICAL_EVALUATION),
                new ScoringCriterion("2", "价格评分", "投标报价", new BigDecimal("40"), ScoringCriteriaSubType.PRICE_WEIGHT)
        );
        when(classificationService.classifyForProject(1001L))
                .thenReturn(new ScoringCriteriaClassificationResult(criteria, null, new BigDecimal("70"), BigDecimal.ZERO, "招标文件.pdf"));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ProjectScoreDraftParseResponse response = service.importFromAiAnalysis(1001L);

        assertThat(response.getDrafts()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(2);
        verify(repository).deleteByProjectIdAndStatusIn(eq(1001L), any());
        verify(repository).saveAll(any());
    }

    @Test
    void importFromAiAnalysis_ShouldThrowWhenNoSnapshot() {
        when(classificationService.classifyForProject(1001L))
                .thenReturn(ScoringCriteriaClassificationResult.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importFromAiAnalysis(1001L))
                .isInstanceOf(com.xiyu.bid.exception.BusinessException.class)
                .hasMessageContaining("评分标准");
    }

    @Test
    void importFromAiAnalysis_ShouldThrowWhenNoStructuredItems() {
        when(classificationService.classifyForProject(1001L))
                .thenReturn(new ScoringCriteriaClassificationResult(null, List.of(), null, BigDecimal.ZERO, "招标文件.pdf"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.importFromAiAnalysis(1001L))
                .isInstanceOf(com.xiyu.bid.exception.BusinessException.class);
    }
}
