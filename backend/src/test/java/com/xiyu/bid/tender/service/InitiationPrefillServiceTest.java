package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitiationPrefillServiceTest {

    @Mock
    private ProjectInitiationDetailsRepository repository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @InjectMocks
    private InitiationPrefillService service;

    @Test
    void shouldSkipWhenInitiationAlreadyExists() {
        when(repository.findByProjectId(1L)).thenReturn(Optional.of(new ProjectInitiationDetails()));
        service.prefillFromEvaluation(1L, null, null);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldSkipWhenNoEvaluationOrBasic() {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
        service.prefillFromEvaluation(1L, null, null);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldPrefillAndMarkEvalPrefilledWhenEvaluationPresent() throws JsonProcessingException {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        TenderEvaluation eval = new TenderEvaluation();
        TenderEvaluationBasic basic = TenderEvaluationBasic.builder()
                .plannedShortlistedCount(5)
                .mroOfficeFlowAmount(new BigDecimal("200"))
                .customerRevenue(new BigDecimal("5000"))
                .unfavorableItems("不利项X")
                .riskAssessment("风险Y")
                .contingencyPlan("是")
                .processKnowledge("是")
                .supportNotes("支持Z")
                .projectPlanGap("GAP说明")
                .build();
        eval.setBasic(basic);
        eval.setCustomerInfos(Collections.emptyList());
        // CO-323: copyGapAttachments 会查 GAP 附件，mock 返回空列表避免 NPE
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        service.prefillFromEvaluation(1L, 1L, eval);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repository).save(captor.capture());
        ProjectInitiationDetails saved = captor.getValue();
        assertThat(saved.getEvalPrefilled()).isTrue();
        assertThat(saved.getExpectedBidders()).isEqualTo(5);
        assertThat(saved.getAnnualEcommerceAmount()).isEqualByComparingTo("200");
        assertThat(saved.getTenderAdverseItems()).isEqualTo("不利项X");
        assertThat(saved.getRiskAssessment()).isEqualTo("风险Y");
        assertThat(saved.getRiskMitigationPlan()).isEqualTo("是");
        assertThat(saved.getSupportNeeded()).isEqualTo("支持Z");
        assertThat(saved.getProjectPlanGap()).isEqualTo("GAP说明");
        assertThat(saved.getCustomerInfoJson()).isEqualTo("[]");
    }
}
