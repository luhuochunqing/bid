// Input: mock repository + notification service + event publisher
// Output: 编排服务扫描/去重/接收人/事件发布行为验证
// Pos: backend/src/test/java/com/xiyu/bid/workbench/service/ - 编排服务单测
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.infrastructure.sdk.OpportunityPredictionEventPublisher;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商机预测提醒编排服务测试：Mock 掉 repository / 通知 / 事件发布，
 * 验证"扫描 → 判定 → 通知 → 落日志 → 发事件"主链路与去重分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityPredictionReminderService 测试")
class OpportunityPredictionReminderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);
    private static final Long SYSTEM_ACTOR = 999L;
    private static final List<Long> ROLE_RECIPIENTS = List.of(10L, 11L);

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectResultRepository projectResultRepository;
    @Mock private ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private OpportunityPredictionRecipientResolver recipientResolver;
    @Mock private OpportunityPredictionReminderTransactionService transactionService;
    @Mock private OpportunityPredictionEventPublisher eventPublisher;
    @Mock private SystemActorResolver systemActorResolver;

    private OpportunityPredictionReminderService service;

    @BeforeEach
    void setUp() {
        service = new OpportunityPredictionReminderService(
                projectRepository, projectResultRepository, projectInitiationDetailsRepository,
                tenderRepository, recipientResolver, transactionService,
                eventPublisher, systemActorResolver);
        lenient().when(systemActorResolver.resolveCached()).thenReturn(SYSTEM_ACTOR);
        lenient().when(recipientResolver.resolve()).thenReturn(new OpportunityPredictionRecipientResolver.RecipientSnapshot(
                Set.of(50L, 60L, 70L, 80L, 90L), ROLE_RECIPIENTS, 5, 5));
        lenient().when(transactionService.completeEventPublish(any(), any(), any())).thenReturn(true);
        lenient().when(eventPublisher.publish(any())).thenReturn(true);
    }

    /** 构造"已到预测时点"的项目：地方国企 + 截止时间已过（预测 = 截止-3月 < 今天）。 */
    private Project reachedProject(Long id, Long tenderId, Long managerId) {
        return Project.builder()
                .id(id)
                .name("测试项目" + id)
                .tenderId(tenderId)
                .customerType("LOCAL_SOE")
                .customer("客户" + id)
                .managerId(managerId)
                .status(Project.Status.WON)
                .build();
    }

    @Test
    @DisplayName("命中提醒窗口：通知 + 落日志 + 发事件，接收人含角色与项目经理")
    void shouldNotifyWhenPredictedReached() {
        Project project = reachedProject(1L, 100L, 50L);
        ProjectResult result = ProjectResult.builder()
                .projectId(1L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1)) // 预测 = 2026-03-01 < 今天
                .servicePeriodYears(new BigDecimal("3"))
                .build();
        Tender tender = Tender.builder()
                .id(100L)
                .purchaserName("招标主体X")
                .projectType("集采")                 // 与立项详情冲突，验证立项详情优先
                .bidOpeningTime(LocalDateTime.of(2025, 8, 15, 9, 0))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(result));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(
                ProjectInitiationDetails.builder().projectId(1L).projectLeaderName("王五")
                        .projectType("OFFICE").build()));
        when(tenderRepository.findAllById(Set.of(100L))).thenReturn(List.of(tender));
        when(transactionService.findState(1L)).thenReturn(Optional.empty());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.notified());
        assertEquals(0, outcome.skipped());

        ArgumentCaptor<CreateNotificationRequest> reqCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        ArgumentCaptor<OpportunityPredictionReminderLog> logCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionReminderLog.class);
        verify(transactionService).createNotificationAndClaim(
                logCaptor.capture(), reqCaptor.capture(), eq(SYSTEM_ACTOR), any(), any());
        CreateNotificationRequest req = reqCaptor.getValue();
        assertEquals(NotificationType.OPPORTUNITY_PREDICTION.name(), req.type());
        assertEquals("Project", req.sourceEntityType());
        assertEquals(1L, req.sourceEntityId());
        assertEquals("【商机预测】招标主体X", req.title());
        assertTrue(req.body().contains("请到CRM创建商机"));
        assertEquals(Set.of(10L, 11L, 50L), Set.copyOf(req.recipientUserIds()));
        assertEquals("/bidding/customer-opportunities", req.payload().get("targetUrl"));

        assertEquals(1L, logCaptor.getValue().getProjectId());
        assertEquals(LocalDate.of(2026, 3, 1), logCaptor.getValue().getPredictedBidDate());
        verify(transactionService).completeEventPublish(eq(1L), any(), any());

        ArgumentCaptor<OpportunityPredictionEventPayload> evtCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionEventPayload.class);
        verify(eventPublisher).publish(evtCaptor.capture());
        OpportunityPredictionEventPayload evt = evtCaptor.getValue();
        assertEquals(1L, evt.projectId());
        assertEquals("测试项目1", evt.projectName());
        assertEquals("WON", evt.resultType());               // 后端码，publisher 序列化时转中文
        assertEquals("客户1", evt.customer());                // 招标主体取项目客户
        assertEquals("王五", evt.projectLeaderName());        // 来自 project_initiation_details
        assertEquals("LOCAL_SOE", evt.customerType());        // 后端码，publisher 序列化时转中文
        assertEquals("OFFICE", evt.projectType());             // 后端码，publisher 序列化时转中文
        assertEquals("2025-08-15", evt.bidOpenTime());        // tender.bidOpeningTime → yyyy-MM-dd
        assertEquals(new BigDecimal("3"), evt.servicePeriodYears());
        assertEquals(LocalDate.of(2026, 6, 1), evt.servicePeriodEndDate());
        assertEquals(LocalDate.of(2026, 3, 1), evt.predictedBidDate());
    }

    @Test
    @DisplayName("招标主体回退：Project.customer 为空时取关联标讯采购人（对齐表格 ownerUnit 列）")
    void shouldFallbackToTenderPurchaserWhenCustomerBlank() {
        Project project = Project.builder()
                .id(5L)
                .name("测试项目5")
                .tenderId(500L)
                .customerType("LOCAL_SOE")
                .customer(null)                       // 项目客户为空 → 触发回退
                .managerId(80L)
                .status(Project.Status.WON)
                .build();
        ProjectResult result = ProjectResult.builder()
                .projectId(5L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1)) // 预测 = 2026-03-01 < 今天
                .servicePeriodYears(new BigDecimal("3"))
                .build();
        Tender tender = Tender.builder()
                .id(500L)
                .purchaserName("回退采购人Y")
                .bidOpeningTime(LocalDateTime.of(2025, 8, 15, 9, 0))
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(5L))).thenReturn(List.of(result));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(5L))).thenReturn(List.of());
        when(tenderRepository.findAllById(Set.of(500L))).thenReturn(List.of(tender));
        when(transactionService.findState(5L)).thenReturn(Optional.empty());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.notified());
        ArgumentCaptor<OpportunityPredictionEventPayload> evtCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionEventPayload.class);
        verify(eventPublisher).publish(evtCaptor.capture());
        // customer 为空 → 回退关联标讯采购人，与表格 ownerUnit 列一致
        assertEquals("回退采购人Y", evtCaptor.getValue().customer());
    }

    @Test
    @DisplayName("项目负责人/客户类型/项目类型回退：项目字段缺失时取关联标讯（对齐表格 enrich 链）")
    void shouldFallbackToTenderForLeaderNameCustomerTypeAndProjectType() {
        Project project = Project.builder()
                .id(6L)
                .name("测试项目6")
                .tenderId(600L)
                .customerType(null)                    // 项目客户类型为空 → 触发 customerType 回退
                .customer("客户6")
                .managerId(90L)
                .status(Project.Status.WON)
                .build();
        ProjectResult result = ProjectResult.builder()
                .projectId(6L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1)) // 央企规则预测 = 2025-12-01 < 今天（customerType 兜底为 CENTRAL_SOE，提前 6 月）
                .servicePeriodYears(new BigDecimal("3"))
                .build();
        Tender tender = Tender.builder()
                .id(600L)
                .purchaserName("采购人Z")
                .projectManagerName("标讯项目经理赵六")   // 立项详情缺失时项目负责人回退源
                .customerType("央企")                   // Project.customerType 空时客户类型回退源（中文别名，验证归一化为 CENTRAL_SOE）
                .projectType("工业品")                 // 立项详情缺失时项目类型回退源（中文别名）
                .bidOpeningTime(LocalDateTime.of(2025, 8, 15, 9, 0))
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(6L))).thenReturn(List.of(result));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(6L))).thenReturn(List.of(
                ProjectInitiationDetails.builder().projectId(6L).projectType("   ").build()));
        when(tenderRepository.findAllById(Set.of(600L))).thenReturn(List.of(tender));
        when(transactionService.findState(6L)).thenReturn(Optional.empty());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.notified());
        ArgumentCaptor<OpportunityPredictionEventPayload> evtCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionEventPayload.class);
        verify(eventPublisher).publish(evtCaptor.capture());
        OpportunityPredictionEventPayload evt = evtCaptor.getValue();
        // 立项详情有效值缺失 → 项目负责人回退标讯项目经理
        assertEquals("标讯项目经理赵六", evt.projectLeaderName());
        // Project.customerType 为 null → 客户类型回退标讯并经 normalizeCustomerType 归一化（央企→CENTRAL_SOE，对齐表格 populateFromTender）
        assertEquals("CENTRAL_SOE", evt.customerType());
        // 立项详情项目类型为空白 → 回退标讯并归一化（工业品→INDUSTRIAL）
        assertEquals("INDUSTRIAL", evt.projectType());
        // 预测逻辑同样用兜底 customerType：央企规则提前 6 月（2026-06-01 → 2025-12-01），而非默认 3 月
        assertEquals(LocalDate.of(2025, 12, 1), evt.predictedBidDate());
    }

    @Test
    @DisplayName("已提醒过：不重复通知、不落日志、不发事件")
    void shouldSkipWhenAlreadyNotified() {
        Project project = reachedProject(2L, 200L, 60L);
        ProjectResult result = ProjectResult.builder()
                .projectId(2L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(2L))).thenReturn(List.of(result));
        when(tenderRepository.findAllById(Set.of(200L))).thenReturn(List.of());
        when(transactionService.findState(2L)).thenReturn(Optional.of(
                new OpportunityPredictionReminderTransactionService.ReminderState(
                        LocalDate.of(2026, 3, 1), LocalDateTime.of(2026, 8, 20, 9, 0))));

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        assertEquals(0, outcome.notified());
        verify(transactionService, never()).createNotificationAndClaim(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("预测时间未到：不通知")
    void shouldSkipWhenPredictedInFuture() {
        Project project = reachedProject(3L, 300L, 70L);
        ProjectResult result = ProjectResult.builder()
                .projectId(3L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2027, 1, 1)) // 预测 = 2026-10-01 > 今天
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(3L))).thenReturn(List.of(result));
        when(tenderRepository.findAllById(Set.of(300L))).thenReturn(List.of());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        assertEquals(0, outcome.notified());
        verify(transactionService, never()).createNotificationAndClaim(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("无接收人（实时角色空且项目经理不在 OSS 在职集合）：跳过")
    void shouldSkipWhenNoRecipients() {
        Project project = reachedProject(4L, 400L, 80L);
        ProjectResult result = ProjectResult.builder()
                .projectId(4L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(4L))).thenReturn(List.of(result));
        when(recipientResolver.resolve()).thenReturn(OpportunityPredictionRecipientResolver.RecipientSnapshot.empty());
        when(transactionService.findState(4L)).thenReturn(Optional.empty());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        assertEquals(0, outcome.notified());
        verify(transactionService, never()).createNotificationAndClaim(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("无项目：扫描空结果")
    void shouldReturnEmptyWhenNoProjects() {
        when(projectRepository.findAll()).thenReturn(List.of());
        var outcome = service.runScan(TODAY);
        assertEquals(0, outcome.scanned());
        assertEquals(0, outcome.notified());
        verify(recipientResolver, never()).resolve();
    }
}
