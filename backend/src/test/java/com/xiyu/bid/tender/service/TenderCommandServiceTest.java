package com.xiyu.bid.tender.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderCommandServiceTest {

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderAssignmentRecordRepository tenderAssignmentRecordRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private DataScopeConfigService dataScopeConfigService;
    @Mock
    private TaskService taskService;
    @Mock
    private TenderAssignmentPermissions tenderAssignmentPermissions;
    @Mock
    private TenderAutoAssignmentService autoAssignmentService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TenderCommandAccessGuard commandAccessGuard;
    @Mock
    private TenderDeduplicationService tenderDeduplicationService;
    @Mock
    private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock
    private ProjectInitiationDetailsRepository initiationDetailsRepository;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private com.xiyu.bid.notification.service.NotificationApplicationService notificationAppService;

    private TenderCommandService tenderCommandService;
    private TenderMapper tenderMapper;
    private TenderProjectAccessGuard accessGuard;
    private TenderStatusTransitionPolicy statusTransitionPolicy;
    private Tender tender;
    private TenderDTO tenderDTO;

    @BeforeEach
    void setUp() {
        tenderMapper = new TenderMapper();
        accessGuard = new TenderProjectAccessGuard(projectRepository, projectAccessScopeService, dataScopeConfigService, userRepository);
        statusTransitionPolicy = new TenderStatusTransitionPolicy();
        tenderCommandService = new TenderCommandService(
                tenderDeduplicationService, tenderRepository, tenderEvaluationRepository, initiationDetailsRepository,
                objectMapper, tenderMapper, accessGuard, taskService, tenderAssignmentPermissions,
                commandAccessGuard, autoAssignmentService, eventPublisher, userRepository, notificationAppService);

        tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .budget(new BigDecimal("100.00"))
                .region("上海")
                .industry("制造业")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        tenderDTO = TenderDTO.builder()
                .id(1L)
                .title("测试标讯")
                .budget(new BigDecimal("100.00"))
                .region("上海")
                .industry("制造业")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();
    }

    @Test
    @DisplayName("创建标讯 - 成功创建")
    void createTender_Success() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(false);

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getTitle()).isEqualTo(tenderDTO.getTitle());
        verify(tenderRepository).save(any(Tender.class));
    }

    @Test
    @DisplayName("创建标讯 - CRM 匹配成功则状态变为 TRACKING")
    void createTender_CrmMatch_ShouldChangeStatusToTracking() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(true);

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getStatus()).isEqualTo(Tender.Status.TRACKING);
        verify(tenderRepository, times(2)).save(any(Tender.class));
    }

    @Test
    @DisplayName("创建标讯 - CRM 匹配失败则保持 PENDING_ASSIGNMENT")
    void createTender_CrmNoMatch_ShouldKeepPendingAssignment() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(false);

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        verify(tenderRepository, times(1)).save(any(Tender.class));
    }

    @Test
    @DisplayName("更新标讯 - 成功更新")
    void updateTender_Success() {
        when(tenderRepository.findById(1L)).thenReturn(java.util.Optional.of(tender));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));

        TenderDTO updateDto = TenderDTO.builder()
                .title("更新后的标题")
                .budget(new BigDecimal("200.00"))
                .build();

        TenderDTO result = tenderCommandService.updateTender(1L, updateDto);

        assertThat(result.getTitle()).isEqualTo("更新后的标题");
        verify(tenderRepository).save(any(Tender.class));
    }

    @Test
    @DisplayName("删除标讯 - 成功删除")
    void deleteTender_Success() {
        when(tenderRepository.findById(1L)).thenReturn(java.util.Optional.of(tender));

        tenderCommandService.deleteTender(1L, 1L);

        verify(tenderRepository).delete(tender);
    }
}
