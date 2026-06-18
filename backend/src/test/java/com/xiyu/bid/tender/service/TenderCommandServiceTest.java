package com.xiyu.bid.tender.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.service.TaskService;
import com.xiyu.bid.tender.dto.TenderDTO;
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
    private com.xiyu.bid.notification.service.NotificationApplicationService notificationAppService;
    @Mock
    private TenderAssignmentNotifier assignmentNotifier;
    @Mock
    private TenderAttachmentRepository tenderAttachmentRepository;

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
                tenderDeduplicationService, tenderRepository, projectRepository,
                tenderMapper, accessGuard, taskService, commandAccessGuard,
                autoAssignmentService, eventPublisher, userRepository, notificationAppService,
                assignmentNotifier, tenderAttachmentRepository);

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
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(AssignmentResult.noMatch());

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getTitle()).isEqualTo(tenderDTO.getTitle());
        verify(tenderRepository).save(any(Tender.class));
    }

    @Test
    @DisplayName("创建标讯 - CRM 匹配成功则状态变为 TRACKING 并写入负责人信息")
    void createTender_CrmMatch_ShouldChangeStatusToTrackingAndWriteAssignment() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(
                AssignmentResult.success("CRM-001", "1001", "张三", "DEPT-001", "销售部"));

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(savedDto.getProjectManagerId()).isEqualTo(1001L);
        assertThat(savedDto.getProjectManagerName()).isEqualTo("张三");
        assertThat(savedDto.getDepartment()).isEqualTo("销售部");
        verify(tenderRepository, times(2)).save(any(Tender.class));
    }

    @Test
    @DisplayName("CO-261: CRM 匹配成功后给被分配的负责人发站内通知")
    void createTender_CrmMatch_ShouldNotifyAssignee() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(
                AssignmentResult.success("CRM-001", "1001", "张三", "DEPT-001", "销售部"));

        tenderCommandService.createTender(tenderDTO);

        // CO-261: 自动分配成功后必须通知被分配人（Notifier 内部保证异常不影响主事务）
        verify(assignmentNotifier).notifyAutoAssigned(any(Tender.class));
    }

    @Test
    @DisplayName("创建标讯 - CRM 匹配失败则保持 PENDING_ASSIGNMENT 且不写入负责人信息")
    void createTender_CrmNoMatch_ShouldKeepPendingAssignmentAndNotWriteAssignment() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class))).thenReturn(AssignmentResult.noMatch());

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(savedDto.getProjectManagerId()).isNull();
        assertThat(savedDto.getProjectManagerName()).isNull();
        assertThat(savedDto.getDepartment()).isNull();
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
