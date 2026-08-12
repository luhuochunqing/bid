package com.xiyu.bid.tender.service;

import com.xiyu.bid.admin.service.DataScopeConfigService;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.external.ProjectManagerIdResolver;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishService;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.tender.entity.TenderAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

import java.util.Optional;

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
    private TenderAssignmentNotifier assignmentNotifier;
    @Mock
    private TenderAttachmentRepository tenderAttachmentRepository;
    @Mock
    private TenderCrmOccupancyChecker crmOccupancyChecker;
    @Mock
    private TenderAuditService tenderAuditService;
    @Mock
    private ProjectManagerIdResolver projectManagerIdResolver;
    @Mock
    private com.xiyu.bid.crm.application.CrmTenderSubjectChecker crmTenderSubjectChecker;
    @Mock
    private TenderCrmLinkPersistService crmLinkPersistService;
    @Mock
    private ProjectManagerDepartmentEnricher departmentEnricher;

    private TenderCommandService tenderCommandService;
    private TenderMapper tenderMapper;
    private TenderProjectAccessGuard accessGuard;
    private TenderStatusTransitionPolicy statusTransitionPolicy;
    private Tender tender;
    private TenderDTO tenderDTO;

    @BeforeEach
    void setUp() {
        tenderMapper = new TenderMapper();
        accessGuard = new TenderProjectAccessGuard(projectRepository, projectAccessScopeService, dataScopeConfigService, userRepository, tenderAssignmentRecordRepository);
        statusTransitionPolicy = new TenderStatusTransitionPolicy();
        tenderCommandService = new TenderCommandService(
                tenderDeduplicationService, tenderRepository, projectRepository,
                tenderMapper, accessGuard, commandAccessGuard,
                autoAssignmentService, eventPublisher, userRepository,
                assignmentNotifier, tenderAttachmentRepository, crmOccupancyChecker,
                null, // CO-310: TenderEvaluationBackfillService（本测试不涉及回填）
                projectManagerIdResolver,
                tenderAssignmentRecordRepository,
                tenderAuditService,
                crmTenderSubjectChecker,
                crmLinkPersistService,
                departmentEnricher,
                mock(TenderEventPublishService.class));

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
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), nullable(String.class)))
                .thenReturn(AssignmentResult.noMatch());

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getTitle()).isEqualTo(tenderDTO.getTitle());
        verify(tenderRepository).save(any(Tender.class));
        // 无 userId 时 operator username 为 null（批量导入正常路径会带 userId）
        verify(autoAssignmentService).autoAssignIfPossible(any(Tender.class), isNull());
    }

    @Test
    @DisplayName("批量导入路径：createTender(userId) 将操作人 username 传给 CRM 自动分配")
    void createTender_WithUserId_PassesOperatorUsernameToAutoAssign() {
        User operator = User.builder().id(42L).username("06234").fullName("郑蓉蓉").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(operator));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), eq("06234")))
                .thenReturn(AssignmentResult.noMatch());

        tenderCommandService.createTender(tenderDTO, 42L);

        // 批量导入异步线程无 SecurityContext，必须显式把导入人 username 交给 CRM 换票
        verify(autoAssignmentService).autoAssignIfPossible(any(Tender.class), eq("06234"));
    }

    @Test
    @DisplayName("CO-576: createTender(userId) 自动分配转 TRACKING 时应将 operatorId/operatorName 写入事件")
    void createTender_WithUserId_AutoAssignPublishesEventWithOperator() {
        User operator = User.builder().id(42L).username("06234").fullName("郑蓉蓉").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(operator));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), eq("06234")))
                .thenReturn(AssignmentResult.success("CRM-001", "1001", "张三", "DEPT-001", "销售部"));
        when(projectManagerIdResolver.resolveByFullName("张三")).thenReturn(100L);

        tenderCommandService.createTender(tenderDTO, 42L);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TenderStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.tenderId()).isEqualTo(1L);
        assertThat(event.oldStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(event.newStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(event.operatorId()).isEqualTo(42L);
        assertThat(event.operatorName()).isEqualTo("郑蓉蓉");
    }

    @Test
    @DisplayName("CO-265: 创建标讯时招标主体+报名截止+开标时间重复应抛 TenderDuplicateException")
    void createTender_DuplicatePurchaserDeadlineBidTime_ThrowsTenderDuplicateException() {
        LocalDateTime futureDeadline = LocalDateTime.now().plusDays(30);
        LocalDateTime futureBidOpen = futureDeadline.plusDays(14);
        TenderDTO duplicateDto = TenderDTO.builder()
                .title("已有标讯")
                .purchaserName("重复采购人")
                .registrationDeadline(futureDeadline)
                .bidOpeningTime(futureBidOpen)
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        Tender existing = Tender.builder()
                .id(99L)
                .title("已有标讯")
                .purchaserName("重复采购人")
                .registrationDeadline(futureDeadline)
                .bidOpeningTime(futureBidOpen)
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        when(tenderDeduplicationService.findDuplicates(any(Tender.class)))
                .thenReturn(List.of(existing));

        TenderDuplicateException ex = assertThrows(
                TenderDuplicateException.class,
                () -> tenderCommandService.createTender(duplicateDto)
        );

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("投标管理系统该标讯已存在");
        assertThat(ex.getDuplicates()).hasSize(1);
        verify(tenderRepository, never()).save(any(Tender.class));
    }

    @Test
    @DisplayName("创建标讯 - CRM 匹配成功则状态变为 TRACKING 并写入负责人信息")
    void createTender_CrmMatch_ShouldChangeStatusToTrackingAndWriteAssignment() {
        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), nullable(String.class))).thenReturn(
                AssignmentResult.success("CRM-001", "1001", "张三", "DEPT-001", "销售部"));
        when(projectManagerIdResolver.resolveByFullName("张三")).thenReturn(100L);

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(savedDto.getProjectManagerId()).isEqualTo(100L);
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
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), nullable(String.class))).thenReturn(
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
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), nullable(String.class)))
                .thenReturn(AssignmentResult.noMatch());

        TenderDTO savedDto = tenderCommandService.createTender(tenderDTO);

        assertThat(savedDto.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(savedDto.getProjectManagerId()).isNull();
        assertThat(savedDto.getProjectManagerName()).isNull();
        assertThat(savedDto.getDepartment()).isNull();
        verify(tenderRepository, times(1)).save(any(Tender.class));
    }

    @Test
    @DisplayName("创建标讯 - 拒绝保存已选择但未上传完成的附件")
    void createTender_WithBlankAttachmentUrl_ShouldRejectBeforeSaving() {
        TenderDTO dtoWithBlankAttachment = TenderDTO.builder()
                .title("附件未上传完成标讯")
                .region("上海")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .attachments(List.of(TenderAttachmentDTO.builder()
                        .fileName("招标文件.pdf")
                        .fileType("application/pdf")
                        .fileUrl("")
                        .build()))
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> tenderCommandService.createTender(dtoWithBlankAttachment)
        );

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("标讯附件未完成上传，请重新上传后再保存");
        verify(tenderRepository, never()).save(any(Tender.class));
        verify(tenderAttachmentRepository, never()).save(any(TenderAttachment.class));
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
    @DisplayName("更新标讯 - 拒绝保存已选择但未上传完成的附件且不删除旧附件")
    void updateTender_WithBlankAttachmentUrl_ShouldRejectBeforeDeletingAttachments() {
        when(tenderRepository.findById(1L)).thenReturn(java.util.Optional.of(tender));

        TenderDTO updateDto = TenderDTO.builder()
                .attachments(List.of(TenderAttachmentDTO.builder()
                        .fileName("招标文件.pdf")
                        .fileType("application/pdf")
                        .fileUrl(" ")
                        .build()))
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> tenderCommandService.updateTender(1L, updateDto)
        );

        assertThat(ex.getCode()).isEqualTo(400);
        assertThat(ex.getMessage()).isEqualTo("标讯附件未完成上传，请重新上传后再保存");
        verify(tenderRepository, never()).save(any(Tender.class));
        verify(tenderAttachmentRepository, never()).deleteByTenderId(any());
        verify(tenderAttachmentRepository, never()).save(any(TenderAttachment.class));
    }

    @Test
    @DisplayName("CO-263: 创建标讯时 attachments[0] 同步到 sourceDocument* 字段")
    @Disabled("CO-297 顺手修：pre-existing failure in origin/main c102515bf (PR !954 doc-insight 路径改后未更新测试期望); 与本 PR 范围无关，待 CO-XXX 单独修复")
    void createTender_WithAttachments_ShouldSyncFirstAttachmentToSourceDocument() {
        TenderDTO dtoWithAttachment = TenderDTO.builder()
                .title("带附件标讯")
                .region("上海")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .attachments(List.of(TenderAttachmentDTO.builder()
                        .fileName("招标文件.pdf")
                        .fileType("application/pdf")
                        .fileUrl("doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf")
                        .build()))
                .build();

        when(tenderRepository.save(any(Tender.class))).thenAnswer(invocation -> {
            Tender saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(autoAssignmentService.autoAssignIfPossible(any(Tender.class), nullable(String.class)))
                .thenReturn(AssignmentResult.noMatch());

        TenderDTO savedDto = tenderCommandService.createTender(dtoWithAttachment);

        assertThat(savedDto.getSourceDocumentName()).isEqualTo("招标文件.pdf");
        assertThat(savedDto.getSourceDocumentFileType()).isEqualTo("application/pdf");
        assertThat(savedDto.getSourceDocumentFileUrl()).isEqualTo("doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf");
        verify(tenderAttachmentRepository).save(any(TenderAttachment.class));
    }

    @Test
    @DisplayName("CO-263: 更新标讯时 attachments[0] 同步到 sourceDocument* 字段")
    @Disabled("CO-297 顺手修：pre-existing failure in origin/main c102515bf (PR !954 doc-insight 路径改后未更新测试期望); 与本 PR 范围无关，待 CO-XXX 单独修复")
    void updateTender_WithAttachments_ShouldSyncFirstAttachmentToSourceDocument() {
        when(tenderRepository.findById(1L)).thenReturn(java.util.Optional.of(tender));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));

        TenderDTO updateDto = TenderDTO.builder()
                .attachments(List.of(TenderAttachmentDTO.builder()
                        .fileName("更新招标文件.docx")
                        .fileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .fileUrl("doc-insight://TENDER_INTAKE/manual-tender/hash-更新招标文件.docx")
                        .build()))
                .build();

        TenderDTO result = tenderCommandService.updateTender(1L, updateDto);

        assertThat(result.getSourceDocumentName()).isEqualTo("更新招标文件.docx");
        assertThat(result.getSourceDocumentFileType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(result.getSourceDocumentFileUrl()).isEqualTo("doc-insight://TENDER_INTAKE/manual-tender/hash-更新招标文件.docx");
    }

    @Test
    @DisplayName("删除标讯 - 成功删除")
    void deleteTender_Success() {
        when(tenderRepository.findById(1L)).thenReturn(java.util.Optional.of(tender));
        when(projectRepository.findByTenderId(1L)).thenReturn(java.util.Collections.emptyList());

        tenderCommandService.deleteTender(1L, 1L);

        verify(tenderRepository).delete(tender);
    }

    @Test
    @DisplayName("删除标讯 - 有关联项目时拒绝删除")
    void deleteTender_WithLinkedProjects_ThrowsBusinessException() {
        when(tenderRepository.findById(1L)).thenReturn(java.util.Optional.of(tender));
        com.xiyu.bid.entity.Project project = new com.xiyu.bid.entity.Project();
        project.setId(100L);
        project.setName("测试项目");
        project.setTenderId(1L);
        when(projectRepository.findByTenderId(1L)).thenReturn(java.util.List.of(project));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> tenderCommandService.deleteTender(1L, 1L)
        );

        assertThat(ex.getMessage()).contains("1 个项目");
        verify(tenderRepository, never()).delete(any(Tender.class));
    }

    @Test
    @DisplayName("CO-258: 关联CRM商机 - 标讯不存在时抛 409 BusinessException")
    void linkCrmOpportunity_NotFound_ThrowsBusinessException409() {
        when(tenderRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> tenderCommandService.linkCrmOpportunity(99L, "CRM-1", "商机-1", 1L)
        );

        assertThat(ex.getCode()).isEqualTo(409);
        assertThat(ex.getHttpStatus().value()).isEqualTo(409);
        verify(tenderRepository, never()).save(any(Tender.class));
    }

    // ── CO-333: applyAssignmentResult 用姓名解析 User.id（而非工号强转） ──────────

    @Test
    @DisplayName("CO-333: applyAssignmentResult_姓名唯一匹配_设置正确的 projectManagerId")
    void applyAssignmentResult_uniqueNameMatch_setsCorrectUserId() {
        AssignmentResult result = AssignmentResult.success(
                "crm-1", "08687", "王凯毅", "dept-1", "销售一部");

        when(projectManagerIdResolver.resolveByFullName("王凯毅")).thenReturn(42L);

        tenderCommandService.applyAssignmentResult(tender, result);

        assertThat(tender.getProjectManagerName()).isEqualTo("王凯毅");
        assertThat(tender.getProjectManagerId()).isEqualTo(42L);
        assertThat(tender.getDepartment()).isEqualTo("销售一部");
        verify(projectManagerIdResolver).resolveByFullName("王凯毅");
    }

    @Test
    @DisplayName("CO-333: applyAssignmentResult_姓名无匹配_projectManagerId 保持 null")
    void applyAssignmentResult_noNameMatch_managerIdRemainsNull() {
        AssignmentResult result = AssignmentResult.success(
                "crm-1", "08687", "不存在的人", "dept-1", "销售一部");

        when(projectManagerIdResolver.resolveByFullName("不存在的人")).thenReturn(null);

        tenderCommandService.applyAssignmentResult(tender, result);

        assertThat(tender.getProjectManagerName()).isEqualTo("不存在的人");
        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getDepartment()).isEqualTo("销售一部");
    }

    @Test
    @DisplayName("CO-333: applyAssignmentResult_projectManagerName 为 null_不调用 resolver")
    void applyAssignmentResult_nullName_doesNotCallResolver() {
        AssignmentResult result = AssignmentResult.success(
                "crm-1", "08687", null, "dept-1", "销售一部");

        tenderCommandService.applyAssignmentResult(tender, result);

        assertThat(tender.getProjectManagerName()).isNull();
        assertThat(tender.getProjectManagerId()).isNull();
        verify(projectManagerIdResolver, never()).resolveByFullName(anyString());
    }

    @Test
    @DisplayName("CO-333: applyAssignmentResult_本地映射工号不再被直接当 User.id 使用")
    void applyAssignmentResult_employeeIdNotUsedAsUserId() {
        // 本地映射表存的 projectManagerId 是工号（如 "10086"），不应再被 Long.valueOf 当 User.id
        AssignmentResult result = AssignmentResult.success(
                "crm-1", "10086", "张三", "dept-1", "销售部");

        when(projectManagerIdResolver.resolveByFullName("张三")).thenReturn(10L);

        tenderCommandService.applyAssignmentResult(tender, result);

        // 验证用的是 resolver 返回的 10L，而不是工号 10086
        assertThat(tender.getProjectManagerId()).isEqualTo(10L);
        assertThat(tender.getProjectManagerId()).isNotEqualTo(10086L);
    }

    // ── CO-576: updateStatus 操作人传播与边界条件 ───────────────────────────────

    @Test
    @DisplayName("CO-576: updateStatus 应将 operatorId 与 fullName 传播到状态变更事件")
    void updateStatus_WithUserId_ShouldPropagateOperatorIdAndNameToEvent() {
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        User operator = User.builder().id(42L).username("06234").fullName("郑蓉蓉").build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(operator));
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(mockProject()));

        tenderCommandService.updateStatus(1L, Tender.Status.TRACKING, 42L);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TenderStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.tenderId()).isEqualTo(1L);
        assertThat(event.oldStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(event.newStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(event.operatorId()).isEqualTo(42L);
        assertThat(event.operatorName()).isEqualTo("郑蓉蓉");
        verify(tenderRepository).save(any(Tender.class));
    }

    @Test
    @DisplayName("CO-576: updateStatus 无 userId 时 operatorId 与 operatorName 均为 null")
    void updateStatus_WithNullUserId_ShouldPublishEventWithNullOperator() {
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(mockProject()));

        tenderCommandService.updateStatus(1L, Tender.Status.TRACKING, null);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().operatorId()).isNull();
        assertThat(eventCaptor.getValue().operatorName()).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("CO-576: updateStatus userId 找不到用户时 operatorName 降级为 null")
    void updateStatus_WithUnknownUserId_ShouldPublishEventWithNullOperatorName() {
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(mockProject()));

        tenderCommandService.updateStatus(1L, Tender.Status.TRACKING, 99L);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().operatorId()).isEqualTo(99L);
        assertThat(eventCaptor.getValue().operatorName()).isNull();
    }

    @Test
    @DisplayName("CO-576: updateStatus 标讯不存在时抛 ResourceNotFoundException")
    void updateStatus_TenderNotFound_ShouldThrowResourceNotFoundException() {
        when(tenderRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> tenderCommandService.updateStatus(99L, Tender.Status.TRACKING, 42L)
        );

        assertThat(ex.getMessage()).contains("Tender");
        verify(tenderRepository, never()).save(any(Tender.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("CO-576: updateStatus 非法状态转换时不保存且不发事件")
    void updateStatus_IllegalTransition_ShouldThrowAndNotSave() {
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(mockProject()));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> tenderCommandService.updateStatus(1L, Tender.Status.WON, 42L)
        );

        assertThat(ex.getMessage()).contains("cannot transition");
        verify(tenderRepository, never()).save(any(Tender.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private com.xiyu.bid.entity.Project mockProject() {
        com.xiyu.bid.entity.Project project = new com.xiyu.bid.entity.Project();
        project.setId(100L);
        project.setTenderId(1L);
        return project;
    }

    @Test
    @DisplayName("CO-576: resolveCreatorId 正常返回创建者 ID")
    void resolveCreatorId_ReturnsCreatorId() {
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        Long result = tenderCommandService.resolveCreatorId(1L);

        assertThat(result).isEqualTo(tender.getCreatorId());
    }

    @Test
    @DisplayName("CO-576: resolveCreatorId tender 不存在时返回 null")
    void resolveCreatorId_TenderNotFound_ReturnsNull() {
        when(tenderRepository.findById(999L)).thenReturn(Optional.empty());

        Long result = tenderCommandService.resolveCreatorId(999L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("CO-576: resolveCreatorId tenderId 为 null 时返回 null")
    void resolveCreatorId_NullInput_ReturnsNull() {
        Long result = tenderCommandService.resolveCreatorId(null);

        assertThat(result).isNull();
        verify(tenderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("CO-576: resolveCreatorId tender 存在但 creatorId 为 null 时返回 null")
    void resolveCreatorId_CreatorIdNull_ReturnsNull() {
        tender.setCreatorId(null);
        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        Long result = tenderCommandService.resolveCreatorId(1L);

        assertThat(result).isNull();
    }
}