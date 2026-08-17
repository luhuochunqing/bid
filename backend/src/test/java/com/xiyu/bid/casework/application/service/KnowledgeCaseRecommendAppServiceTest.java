package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchCriteria;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.domain.policy.KnowledgeCaseMatchPolicy;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeCaseRecommendAppServiceTest {

    @Mock
    private KnowledgeCaseRepository knowledgeCaseRepository;

    @Mock
    private ProjectScoreDraftRepository scoreDraftRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Spy
    private KnowledgeCaseMatchPolicy matchPolicy = new KnowledgeCaseMatchPolicy();

    @InjectMocks
    private KnowledgeCaseRecommendAppService service;

    private KnowledgeCase createCandidate(Long id, String scoringTitle, String responseText) {
        KnowledgeCase kCase = new KnowledgeCase();
        kCase.setId(id);
        kCase.setSourceProjectId(200L);
        kCase.setSourceProjectName("测试历史项目");
        kCase.setScoringPointTitle(scoringTitle);
        kCase.setRequirementRaw("需求描述");
        kCase.setResponseText(responseText);
        kCase.setCustomerType("CENTRAL_SOE");
        kCase.setProjectType("IT");
        kCase.setBidResult("WON");
        kCase.setReuseCount(5);
        kCase.setStatus("ACTIVE");
        return kCase;
    }

    @Test
    void recommendForScoringItem_ShouldReturnScoresWithFullMetadata() {
        Project project = new Project();
        project.setId(100L);
        project.setCustomerType("CENTRAL_SOE");
        project.setIndustry("IT");

        ProjectScoreDraft draft = new ProjectScoreDraft();
        draft.setId(1L);
        draft.setProjectId(100L);
        draft.setScoreItemTitle("技术方案");
        draft.setCategory("TECHNICAL");

        KnowledgeCase candidate = createCandidate(1L, "技术方案实施细则", "提供高可用技术方案");

        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(100L))
                .thenReturn(List.of(draft));
        when(knowledgeCaseRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(candidate)));

        List<KnowledgeCaseMatchScore> results = service.recommendForScoringItem(100L, "技术方案", "方案");

        assertThat(results).isNotEmpty();
        KnowledgeCaseMatchScore top = results.get(0);
        assertThat(top.caseId()).isEqualTo(1L);
        assertThat(top.score()).isGreaterThan(0);
        assertThat(top.scoringTitle()).isEqualTo("技术方案实施细则");
        assertThat(top.sourceProjectName()).isEqualTo("测试历史项目");
        assertThat(top.responseText()).isEqualTo("提供高可用技术方案");
        assertThat(top.customerType()).isEqualTo("CENTRAL_SOE");
        assertThat(top.projectType()).isEqualTo("IT");
        assertThat(top.bidResult()).isEqualTo("WON");
        assertThat(top.reuseCount()).isEqualTo(5);
    }
}
