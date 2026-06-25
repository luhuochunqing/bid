package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
    void shouldSkipWhenInitiationAlreadyComplete() {
        ProjectInitiationDetails existing = ProjectInitiationDetails.builder()
                .projectId(1L).ownerUnit("已有单位").customerType("GOVERNMENT").build();
        when(repository.findByProjectId(1L)).thenReturn(Optional.of(existing));
        service.prefillFromEvaluation(1L, 1L, null, null);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldBackfillMissingRequiredFieldsWhenAlreadyExists() {
        ProjectInitiationDetails existing = ProjectInitiationDetails.builder()
                .projectId(1L).ownerUnit(null).customerType(null).build();
        when(repository.findByProjectId(1L)).thenReturn(Optional.of(existing));
        Tender tender = Tender.builder().purchaserName("补全单位").customerType("央企").build();

        service.prefillFromEvaluation(1L, 1L, null, tender);

        verify(repository).save(existing);
        assertThat(existing.getOwnerUnit()).isEqualTo("补全单位");
        assertThat(existing.getCustomerType()).isEqualTo("CENTRAL_SOE");
    }

    @Test
    void shouldPrefillAndMarkEvalPrefilledWhenEvaluationPresent() throws JsonProcessingException {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
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

        service.prefillFromEvaluation(1L, 1L, eval, null);

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
        assertThat(saved.getCustomerInfoJson()).isNull();
    }

    @Test
    void shouldPrefillOwnerUnitAndCustomerTypeFromTender() {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        Tender tender = Tender.builder()
                .purchaserName("测试采购方")
                .customerType("政府机关")
                .build();

        service.prefillFromEvaluation(1L, 1L, null, tender);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repository).save(captor.capture());
        ProjectInitiationDetails saved = captor.getValue();
        assertThat(saved.getOwnerUnit()).isEqualTo("测试采购方");
        assertThat(saved.getCustomerType()).isEqualTo("GOVERNMENT");
        assertThat(saved.getEvalPrefilled()).isTrue();
    }

    @Test
    void shouldMapVariousCustomerTypeFormats() {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        // 验证中文文本映射
        service.prefillFromEvaluation(1L, 1L, null, Tender.builder().purchaserName("A").customerType("央企").build());
        ArgumentCaptor<ProjectInitiationDetails> captor1 = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repository, times(1)).save(captor1.capture());
        assertThat(captor1.getValue().getCustomerType()).isEqualTo("CENTRAL_SOE");

        // 验证历史数据"政府"映射
        reset(repository);
        when(repository.findByProjectId(2L)).thenReturn(Optional.empty());
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        service.prefillFromEvaluation(2L, 2L, null, Tender.builder().purchaserName("B").customerType("政府").build());
        ArgumentCaptor<ProjectInitiationDetails> captor2 = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repository).save(captor2.capture());
        assertThat(captor2.getValue().getCustomerType()).isEqualTo("GOVERNMENT");
    }

    @Test
    void shouldStillPrefillFromTenderEvenWithoutEvaluation() {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        Tender tender = Tender.builder()
                .purchaserName("无评估表的采购方")
                .customerType("民企")
                .build();

        service.prefillFromEvaluation(1L, 1L, null, tender);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repository).save(captor.capture());
        ProjectInitiationDetails saved = captor.getValue();
        // 即使无评估表，标讯基础字段仍应带入
        assertThat(saved.getOwnerUnit()).isEqualTo("无评估表的采购方");
        assertThat(saved.getCustomerType()).isEqualTo("PRIVATE");
        assertThat(saved.getEvalPrefilled()).isTrue();
    }

    @Test
    void shouldPrefillCustomerInfoEvenWhenBasicIsNull() throws JsonProcessingException {
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"role\":\"专家1\",\"name\":\"王专家\"}]");
        when(projectDocumentRepository.findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());
        TenderEvaluation eval = new TenderEvaluation();
        eval.setBasic(null);
        eval.setCustomerInfos(List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("NAME")
                        .cellValue("王专家")
                        .valueType(TenderEvaluationCustomerInfo.ValueType.TEXT)
                        .build()));

        service.prefillFromEvaluation(1L, 1L, eval, null);

        ArgumentCaptor<ProjectInitiationDetails> captor = ArgumentCaptor.forClass(ProjectInitiationDetails.class);
        verify(repository).save(captor.capture());
        ProjectInitiationDetails saved = captor.getValue();
        assertThat(saved.getEvalPrefilled()).isTrue();
        assertThat(saved.getCustomerInfoJson()).isEqualTo("[{\"role\":\"专家1\",\"name\":\"王专家\"}]");
    }
}
