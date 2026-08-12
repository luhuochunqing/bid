package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishService;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderCrmOccupancyChecker;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-297: 验证 CRM 推送创建标讯时，CRM 商机号重复应返回 409 而不是未捕获的 500。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenderIntegrationCommandService CRM 商机号重复校验")
class TenderIntegrationCommandServiceCrmDuplicateTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderAttachmentRepository attachmentRepository;
    @Mock private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock private TenderEvaluationSubmissionMapper submissionMapper;
    @Mock private CrmTenderLinkService crmTenderLinkService;
    @Mock private ProjectDocumentRepository projectDocumentRepository;
    @Mock private TenderAutoAssignmentService autoAssignmentService;
    @Mock private TenderAssignmentNotifier assignmentNotifier;
    @Mock private TenderAuditService tenderAuditService;
    @Mock private TenderCrmOccupancyChecker crmOccupancyChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectManagerIdResolver projectManagerIdResolver;
    @Mock private ProjectManagerDepartmentEnricher departmentEnricher;

    private TenderIntegrationCommandService commandService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository, submissionMapper);
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                tenderMapper, evaluationMapper, projectManagerIdResolver);
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository, evaluationMapper, projectDocumentRepository);
        TenderIntegrationResolver helper = new TenderIntegrationResolver(tenderRepository);
        TenderIntegrationCommandSupport support = new TenderIntegrationCommandSupport(
                crmTenderLinkService,
                autoAssignmentService,
                assignmentNotifier,
                eventPublisher,
                tenderRepository,
                projectManagerIdResolver,
                departmentEnricher,
                userRepository);
        commandService = new TenderIntegrationCommandService(
                tenderRepository, attachmentRepository, crmTenderLinkService, mapper, evaluationService, helper, support,
                eventPublisher, tenderAuditService, userRepository, crmOccupancyChecker,
                new com.xiyu.bid.webhook.application.OperatorUsernameResolver(userRepository),
                new com.xiyu.bid.tender.service.TenderDeduplicationService(tenderRepository),
                projectManagerIdResolver,
                mock(TenderEventPublishService.class));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        TenderDTO stubDto = TenderDTO.builder().build();
        when(tenderMapper.toDTO(any(Tender.class))).thenReturn(stubDto);
        when(tenderMapper.buildContacts(any(Tender.class))).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("pushTender 创建时 CRM 商机号已被占用应抛 409")
    void pushTender_createWithOccupiedCrmOpportunityId_shouldThrow409() {
        when(tenderRepository.findByExternalId("crm:new-001")).thenReturn(Optional.empty());
        doThrow(new BusinessException(409, "该 CRM 商机已被标讯 ID=1 关联"))
                .when(crmOccupancyChecker).assertCrmOpportunityNotOccupied(eq(null), eq("CC20260626498"));

        TenderPushRequest request = TenderPushRequest.builder()
                .sourceSystem("crm")
                .sourceId("new-001")
                .title("测试标讯")
                .crmOpportunityId("CC20260626498")
                .build();

        assertThatThrownBy(() -> commandService.pushTender(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该 CRM 商机已被标讯 ID=1 关联");

        verify(crmOccupancyChecker).assertCrmOpportunityNotOccupied(null, "CC20260626498");
        verify(tenderRepository, never()).save(any(Tender.class));
    }

    @Test
    @DisplayName("updateByExternalId 更新时 CRM 商机号已被占用应抛 409")
    void updateByExternalId_withOccupiedCrmOpportunityId_shouldThrow409() {
        Tender tender = new Tender();
        tender.setId(1630L);
        tender.setExternalId("crm:test-001");
        tender.setTitle("测试标讯");
        tender.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC20260703615");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), eq("21246"), any(), any());

        doThrow(new BusinessException(409, "该 CRM 商机已被标讯 ID=926 关联"))
                .when(crmOccupancyChecker).assertCrmOpportunityNotOccupied(eq(1630L), eq("CC20260703615"));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("21246")
                .build();

        assertThatThrownBy(() -> commandService.updateByExternalId("crm", "test-001", request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该 CRM 商机已被标讯 ID=926 关联");

        verify(crmOccupancyChecker).assertCrmOpportunityNotOccupied(1630L, "CC20260703615");
        verify(tenderRepository, never()).save(any(Tender.class));
    }

    @Test
    @DisplayName("updateByExternalId 并发写入触发唯一索引，应翻译为 409")
    void updateByExternalId_concurrentDuplicateKey_shouldTranslateTo409() {
        Tender tender = new Tender();
        tender.setId(1630L);
        tender.setExternalId("crm:test-001");
        tender.setTitle("测试标讯");
        tender.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC20260703615");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), eq("21246"), any(), any());

        DataIntegrityViolationException dbEx = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'CC20260703615' for key 'tenders.idx_tender_crm_opportunity_id']",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'CC20260703615' for key 'tenders.idx_tender_crm_opportunity_id'"));
        when(tenderRepository.save(any(Tender.class))).thenThrow(dbEx);
        doThrow(new BusinessException(409, "CRM 商机已被其他标讯关联（并发冲突），请刷新后重试"))
                .when(crmOccupancyChecker).translateUniqueConstraintViolation(dbEx);

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("21246")
                .build();

        assertThatThrownBy(() -> commandService.updateByExternalId("crm", "test-001", request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");

        verify(crmOccupancyChecker).assertCrmOpportunityNotOccupied(1630L, "CC20260703615");
        verify(crmOccupancyChecker).translateUniqueConstraintViolation(dbEx);
    }

    @Test
    @DisplayName("pushTender 创建时并发写入触发唯一索引，应翻译为 409")
    void pushTender_createConcurrentDuplicateKey_shouldTranslateTo409() {
        when(tenderRepository.findByExternalId("crm:new-002")).thenReturn(Optional.empty());
        DataIntegrityViolationException dbEx = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'CC20260626499' for key 'tenders.idx_tender_crm_opportunity_id']",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'CC20260626499' for key 'tenders.idx_tender_crm_opportunity_id'"));
        when(tenderRepository.save(any(Tender.class))).thenThrow(dbEx);
        doThrow(new BusinessException(409, "CRM 商机已被其他标讯关联（并发冲突），请刷新后重试"))
                .when(crmOccupancyChecker).translateUniqueConstraintViolation(dbEx);

        TenderPushRequest request = TenderPushRequest.builder()
                .sourceSystem("crm")
                .sourceId("new-002")
                .title("测试标讯")
                .crmOpportunityId("CC20260626499")
                .build();

        assertThatThrownBy(() -> commandService.pushTender(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("并发冲突");

        verify(crmOccupancyChecker).assertCrmOpportunityNotOccupied(null, "CC20260626499");
        verify(crmOccupancyChecker).translateUniqueConstraintViolation(dbEx);
    }
}
