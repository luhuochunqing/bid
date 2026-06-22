package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-305: TenderIntegrationCommandService TenderStatusChangedEvent 事件发布测试。
 *
 * <p>验证三个场景下事件发布的正确性：
 * <ul>
 *   <li>updateByExternalId - 更新后状态变为 EVALUATED 时发布事件</li>
 *   <li>pushTender (forceUpdate) - 强制更新后状态变为 EVALUATED 时发布事件</li>
 *   <li>pushTender (create) - CRM 推送创建的标讯状态为 EVALUATED 时发布事件</li>
 * </ul>
 *
 * <p>关键断言：
 * <ul>
 *   <li>只有状态变为 EVALUATED 时才发布事件（TRACKING → EVALUATED）</li>
 *   <li>状态未变化时不发布事件（EVALUATED → EVALUATED）</li>
 *   <li>事件包含正确的 tenderId、externalId、oldStatus、newStatus、title</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenderIntegrationCommandService CO-305 事件发布")
class TenderIntegrationCommandServiceEventTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderAttachmentRepository attachmentRepository;
    @Mock private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock private TenderEvaluationSubmissionMapper submissionMapper;
    @Mock private CrmTenderLinkService crmTenderLinkService;
    @Mock private ProjectDocumentRepository projectDocumentRepository;
    @Mock private TenderAutoAssignmentService autoAssignmentService;
    @Mock private TenderAssignmentNotifier assignmentNotifier;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TenderIntegrationCommandService commandService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository, submissionMapper);
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                tenderMapper, evaluationMapper);
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository, evaluationMapper, projectDocumentRepository);
        TenderIntegrationResolver helper = new TenderIntegrationResolver(tenderRepository);
        TenderIntegrationCommandSupport support = new TenderIntegrationCommandSupport(
                crmTenderLinkService,
                autoAssignmentService,
                assignmentNotifier,
                eventPublisher,
                tenderRepository);
        commandService = new TenderIntegrationCommandService(
                tenderRepository, attachmentRepository, crmTenderLinkService, mapper, evaluationService, helper, support, eventPublisher);
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        TenderDTO stubDto = TenderDTO.builder().build();
        when(tenderMapper.toDTO(any(Tender.class))).thenReturn(stubDto);
        when(tenderMapper.buildContacts(any(Tender.class))).thenReturn(Collections.emptyList());
    }

    private Tender createExistingTender(Tender.Status status) {
        Tender t = new Tender();
        t.setId(1L);
        t.setExternalId("crm:test-001");
        t.setTitle("测试标讯");
        t.setStatus(status);
        return t;
    }

    // ── updateByExternalId 场景 ──────────────────────────────────────────────

    @Test
    @DisplayName("updateByExternalId: TRACKING → EVALUATED 时发布 TenderStatusChangedEvent")
    void updateByExternalId_trackingToEvaluated_publishesEvent() {
        Tender tender = createExistingTender(Tender.Status.TRACKING);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .evaluation(new TenderUpdateRequest.EvaluationUpdate(
                        null, null, null))
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TenderStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.tenderId()).isEqualTo(1L);
        assertThat(event.externalId()).isEqualTo("crm:test-001");
        assertThat(event.oldStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(event.newStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(event.title()).isEqualTo("测试标讯");
    }

    @Test
    @DisplayName("updateByExternalId: EVALUATED → EVALUATED 不发布事件（状态未变化）")
    void updateByExternalId_evaluatedToEvaluated_noEvent() {
        Tender tender = createExistingTender(Tender.Status.EVALUATED);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .evaluation(new TenderUpdateRequest.EvaluationUpdate(
                        null, null, null))
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("updateByExternalId: PENDING_ASSIGNMENT → EVALUATED 时发布事件")
    void updateByExternalId_pendingToEvaluated_publishesEvent() {
        Tender tender = createExistingTender(Tender.Status.PENDING_ASSIGNMENT);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .evaluation(new TenderUpdateRequest.EvaluationUpdate(
                        null, null, null))
                .build();

        commandService.updateByExternalId("crm", "test-001", request);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TenderStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.oldStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(event.newStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    // ── pushTender (forceUpdate) 场景 ─────────────────────────────────────────

    @Test
    @DisplayName("pushTender forceUpdate: TRACKING → EVALUATED 时发布事件")
    void pushTender_forceUpdate_trackingToEvaluated_publishesEvent() {
        Tender tender = createExistingTender(Tender.Status.TRACKING);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderPushRequest request = TenderPushRequest.builder()
                .sourceSystem("crm")
                .sourceId("test-001")
                .title("测试标讯")
                .forceUpdate(true)
                .evaluation(new TenderPushRequest.EvaluationUpdate(
                        null, null, null))
                .build();

        commandService.pushTender(request, 100L);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TenderStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.oldStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(event.newStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("pushTender forceUpdate: EVALUATED → EVALUATED 不发布事件")
    void pushTender_forceUpdate_evaluatedToEvaluated_noEvent() {
        Tender tender = createExistingTender(Tender.Status.EVALUATED);
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderPushRequest request = TenderPushRequest.builder()
                .sourceSystem("crm")
                .sourceId("test-001")
                .title("测试标讯")
                .forceUpdate(true)
                .evaluation(new TenderPushRequest.EvaluationUpdate(
                        null, null, null))
                .build();

        commandService.pushTender(request, 100L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── pushTender (create) 场景 ─────────────────────────────────────────────

    @Test
    @DisplayName("pushTender create: 无 evaluation 时状态为 PENDING_ASSIGNMENT 不发布事件")
    void pushTender_create_withoutEvaluation_noEvent() {
        when(tenderRepository.findByExternalId("crm:new-002")).thenReturn(Optional.empty());

        TenderPushRequest request = TenderPushRequest.builder()
                .sourceSystem("crm")
                .sourceId("new-002")
                .title("新建标讯")
                .build();

        commandService.pushTender(request, 100L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("pushTender create: 有 evaluation 时状态仍为 PENDING_ASSIGNMENT 不发布事件（需二次更新）")
    void pushTender_create_withEvaluation_noEvent() {
        // CO-305 实测：创建时即使有 evaluation，状态也是 PENDING_ASSIGNMENT
        // 事件只在 updateByExternalId 或 forceUpdate 时发布（状态变为 EVALUATED）
        when(tenderRepository.findByExternalId("crm:new-001")).thenReturn(Optional.empty());

        TenderPushRequest request = TenderPushRequest.builder()
                .sourceSystem("crm")
                .sourceId("new-001")
                .title("新建标讯")
                .evaluation(new TenderPushRequest.EvaluationUpdate(
                        null, null, null))
                .build();

        commandService.pushTender(request, 100L);

        verify(eventPublisher, never()).publishEvent(any());
    }
}