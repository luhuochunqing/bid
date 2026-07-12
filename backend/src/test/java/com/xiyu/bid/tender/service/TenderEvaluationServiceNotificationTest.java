// Output: TenderEvaluationService.proceedToBid 成功后触发待立项通知
// Pos: tender/service/ - 投标立项与通知集成单元测试
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.application.BidNotificationApplicationService;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.tender.controller.TenderEvaluationController.TenderBidResult;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenderEvaluationService — 投标立项后待立项通知触发")
class TenderEvaluationServiceNotificationTest {

    @Mock
    private TenderEvaluationRepository tenderEvaluationRepository;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskService taskService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenderAssignmentPermissions permissions;

    @Mock
    private TenderProjectAccessGuard accessGuard;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TenderEvaluationDocumentService tenderEvaluationDocumentService;

    @Mock
    private InitiationPrefillService initiationPrefillService;

    @Mock
    private TenderAuditService tenderAuditService;

    @Mock
    private BidNotificationApplicationService bidNotificationApplicationService;

    private TenderEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new TenderEvaluationService(
                tenderEvaluationRepository,
                tenderRepository,
                projectService,
                taskService,
                taskRepository,
                userRepository,
                mock(TenderEvaluationSubmissionService.class),
                permissions,
                accessGuard,
                eventPublisher,
                tenderEvaluationDocumentService,
                initiationPrefillService,
                tenderAuditService,
                bidNotificationApplicationService);
        lenient().when(permissions.canDecide(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("proceedToBid 成功后调用 BidNotificationApplicationService 发送待立项通知")
    void proceedToBid_shouldSendPendingInitiationNotification() {
        Long tenderId = 1L;
        Long adminId = 99L;
        Long projectId = 101L;
        Tender tender = Tender.builder()
                .id(tenderId)
                .title("测试标讯")
                .status(Tender.Status.BIDDING)
                .industry("制造业")
                .region("上海")
                .purchaserName("测试采购方")
                .description("需要创建立项")
                .deadline(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        TenderEvaluation evaluation = TenderEvaluation.builder()
                .tenderId(tenderId)
                .evaluatorId(18L)
                .build();

        when(tenderEvaluationRepository.findByTenderId(tenderId)).thenReturn(Optional.of(evaluation));
        when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(tender));
        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(ProjectDTO.builder()
                .id(projectId)
                .name("测试标讯")
                .status(Project.Status.PENDING_INITIATION)
                .build());
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(
                com.xiyu.bid.entity.User.builder().id(adminId).username("admin").build()));

        TenderBidResult result = service.proceedToBid(tenderId, adminId);

        assertThat(result.projectId()).isEqualTo(projectId);
        verify(bidNotificationApplicationService)
                .sendPendingInitiationNotification(tenderId, projectId, tender.getTitle(), "测试标讯", adminId);
    }

    @Test
    @DisplayName("proceedToBid 成功后通知发送失败不阻塞主流程")
    void proceedToBid_shouldNotFail_whenNotificationServiceThrows() {
        Long tenderId = 1L;
        Long adminId = 99L;
        Long projectId = 101L;
        Tender tender = Tender.builder()
                .id(tenderId)
                .title("测试标讯")
                .status(Tender.Status.BIDDING)
                .industry("制造业")
                .region("上海")
                .purchaserName("测试采购方")
                .description("需要创建立项")
                .deadline(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();

        when(tenderEvaluationRepository.findByTenderId(tenderId)).thenReturn(Optional.empty());
        when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(tender));
        when(projectService.createProject(any(ProjectDTO.class))).thenReturn(ProjectDTO.builder()
                .id(projectId)
                .name("测试标讯")
                .status(Project.Status.PENDING_INITIATION)
                .build());
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(
                com.xiyu.bid.entity.User.builder().id(adminId).username("admin").build()));
        org.mockito.Mockito.doThrow(new RuntimeException("notification down"))
                .when(bidNotificationApplicationService)
                .sendPendingInitiationNotification(tenderId, projectId, tender.getTitle(), "测试标讯", adminId);

        TenderBidResult result = service.proceedToBid(tenderId, adminId);

        assertThat(result.projectId()).isEqualTo(projectId);
        verify(bidNotificationApplicationService)
                .sendPendingInitiationNotification(tenderId, projectId, tender.getTitle(), "测试标讯", adminId);
    }

    @Test
    @DisplayName("proceedToBid 前置校验失败时不发送通知")
    void proceedToBid_shouldNotSendNotification_whenStatusInvalid() {
        Long tenderId = 1L;
        Tender tender = Tender.builder()
                .id(tenderId)
                .title("测试标讯")
                .status(Tender.Status.EVALUATED)
                .build();

        when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(tender));
        lenient().when(permissions.canDecide(tenderId, 99L)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.proceedToBid(tenderId, 99L));

        verify(bidNotificationApplicationService, never()).sendPendingInitiationNotification(any(), any(), any(), any(), any());
    }
}
