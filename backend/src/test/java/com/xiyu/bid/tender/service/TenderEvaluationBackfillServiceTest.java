package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderEvaluationBackfillServiceTest {

    @Mock
    private TenderEvaluationRepository evaluationRepository;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderProjectAccessGuard accessGuard;
    @Mock
    private TenderAssignmentPermissions permissions;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private TenderEvaluationDocumentService documentService;

    private TenderEvaluationBackfillService backfillService;
    private Clock clock = Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        backfillService = new TenderEvaluationBackfillService(
                evaluationRepository,
                tenderRepository,
                userRepository,
                accessGuard,
                permissions,
                eventPublisher,
                projectDocumentRepository,
                documentService,
                clock
        );
    }

    @Test
    @DisplayName("级联保存覆盖测试：已有客户信息时先 clear+saveAndFlush 再插入")
    void testApplyCustomerInfosWithFlush() {
        // given
        Long tenderId = 1L;
        Long evaluatorId = 2L;

        Tender tender = new Tender();
        tender.setId(tenderId);
        tender.setStatus(Tender.Status.TRACKING); // using TRACKING instead of IN_PROGRESS

        User evaluator = new User();
        evaluator.setId(evaluatorId);
        evaluator.setUsername("sales1");

        when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(tender));
        when(userRepository.findById(evaluatorId)).thenReturn(Optional.of(evaluator));
        doNothing().when(accessGuard).assertCanAccessTender(tender);

        TenderEvaluation existingEvaluation = new TenderEvaluation();
        existingEvaluation.setId(10L);
        existingEvaluation.setTenderId(tenderId);
        
        // 模拟已有的客户矩阵信息
        List<TenderEvaluationCustomerInfo> existingInfos = new ArrayList<>();
        TenderEvaluationCustomerInfo oldInfo = new TenderEvaluationCustomerInfo();
        oldInfo.setRoleKey("PROJECT_HIGHEST_DECISION_MAKER");
        oldInfo.setInfoKey("NAME");
        oldInfo.setCellValue("张三");
        existingInfos.add(oldInfo);
        existingEvaluation.setCustomerInfos(existingInfos);

        when(evaluationRepository.findByTenderId(tenderId)).thenReturn(Optional.of(existingEvaluation));
        when(evaluationRepository.saveAndFlush(any())).thenReturn(existingEvaluation);
        when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 构造回填请求
        EvaluationCustomerInfoDTO newInfoDTO = new EvaluationCustomerInfoDTO(
                "MATERIALS_COMPANY_CHAIRMAN", "NAME", "李四", "TEXT"
        );
        EvaluationBasicDTO basicDTO = new EvaluationBasicDTO(
                3, new BigDecimal("100.5"), "None", "Low", "Yes", "Yes", "Support", new BigDecimal("500")
        );
        
        TenderEvaluationSubmitRequest req = new TenderEvaluationSubmitRequest(
                TenderEvaluation.BidRecommendation.RECOMMEND, basicDTO, List.of(newInfoDTO), null
        );

        // when
        backfillService.backfillFromCrmLink(tenderId, req, evaluatorId);

        // then
        // 验证先 saveAndFlush 清空历史
        verify(evaluationRepository, times(1)).saveAndFlush(any());

        // 验证最终 save 包含新的客户矩阵信息
        ArgumentCaptor<TenderEvaluation> saveCaptor = ArgumentCaptor.forClass(TenderEvaluation.class);
        verify(evaluationRepository, times(1)).save(saveCaptor.capture());
        List<TenderEvaluationCustomerInfo> finalInfos = saveCaptor.getValue().getCustomerInfos();
        
        assertThat(finalInfos).hasSize(1);
        assertThat(finalInfos.get(0).getRoleKey()).isEqualTo("MATERIALS_COMPANY_CHAIRMAN");
        assertThat(finalInfos.get(0).getInfoKey()).isEqualTo("NAME");
        assertThat(finalInfos.get(0).getCellValue()).isEqualTo("李四");
    }
}
