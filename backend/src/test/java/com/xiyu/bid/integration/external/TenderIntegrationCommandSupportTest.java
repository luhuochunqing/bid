package com.xiyu.bid.integration.external;

import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenderIntegrationCommandSupportTest {

    @Mock private CrmTenderLinkService crmTenderLinkService;
    @Mock private TenderAutoAssignmentService autoAssignmentService;
    @Mock private TenderAssignmentNotifier assignmentNotifier;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TenderRepository tenderRepository;
    @Mock private ProjectManagerIdResolver projectManagerIdResolver;
    @Mock private ProjectManagerDepartmentEnricher departmentEnricher;
    @Mock private UserRepository userRepository;

    private TenderIntegrationCommandSupport support;

    @BeforeEach
    void setUp() {
        support = new TenderIntegrationCommandSupport(
                crmTenderLinkService,
                autoAssignmentService,
                assignmentNotifier,
                eventPublisher,
                tenderRepository,
                projectManagerIdResolver,
                departmentEnricher,
                userRepository);
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("applyAssignmentResult: 用姓名解析 User.id，不用工号 Long.valueOf")
    void applyAssignmentResult_resolvesByFullName_notByEmployeeNumber() {
        Tender tender = new Tender();
        AssignmentResult result = AssignmentResult.success(
                "crm-123",
                "06234",
                "郑蓉蓉",
                "dept-1",
                "销售一部");

        when(projectManagerIdResolver.resolveByFullName("郑蓉蓉")).thenReturn(2556L);

        support.applyAssignmentResult(tender, result);

        assertThat(tender.getProjectManagerId()).isEqualTo(2556L);
        assertThat(tender.getProjectManagerName()).isEqualTo("郑蓉蓉");
        assertThat(tender.getDepartment()).isEqualTo("销售一部");
    }

    @Test
    @DisplayName("applyAssignmentResult: 姓名解析不到时 projectManagerId 保持 null，不报错")
    void applyAssignmentResult_nameNotFound_keepsNullId() {
        Tender tender = new Tender();
        AssignmentResult result = AssignmentResult.success(
                "crm-123",
                "99999",
                "不存在的人",
                null,
                null);

        when(projectManagerIdResolver.resolveByFullName("不存在的人")).thenReturn(null);

        support.applyAssignmentResult(tender, result);

        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getProjectManagerName()).isEqualTo("不存在的人");
    }

    @Test
    @DisplayName("applyAssignmentResult: projectManagerName 为 null 时不调用解析器")
    void applyAssignmentResult_nullName_skipsResolution() {
        Tender tender = new Tender();
        AssignmentResult result = AssignmentResult.success(
                "crm-123",
                "06234",
                null,
                null,
                null);

        support.applyAssignmentResult(tender, result);

        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getProjectManagerName()).isNull();
        verify(projectManagerIdResolver, never()).resolveByFullName(any());
    }

    @Test
    @DisplayName("tryAutoAssign: EVALUATED 状态标讯匹配到负责人，状态转换失败但负责人仍保存")
    void tryAutoAssign_evaluatedStatus_statusTransitionFailsButManagerSaved() {
        Tender tender = new Tender();
        tender.setId(579L);
        tender.setStatus(Tender.Status.EVALUATED);
        tender.setTitle("测试商机1212");

        AssignmentResult result = AssignmentResult.success(
                null,
                "06234",
                "郑蓉蓉",
                null,
                null);
        when(autoAssignmentService.autoAssignIfPossible(tender, null)).thenReturn(result);
        when(projectManagerIdResolver.resolveByFullName("郑蓉蓉")).thenReturn(2556L);

        support.tryAutoAssign(tender, null);

        assertThat(tender.getProjectManagerId()).isEqualTo(2556L);
        assertThat(tender.getProjectManagerName()).isEqualTo("郑蓉蓉");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        verify(tenderRepository).save(tender);
        verify(assignmentNotifier, never()).notifyAutoAssigned(any());
    }

    @Test
    @DisplayName("tryAutoAssign: PENDING_ASSIGNMENT 状态标讯匹配到负责人，状态转 TRACKING")
    void tryAutoAssign_pendingStatus_transitionsToTracking() {
        Tender tender = new Tender();
        tender.setId(100L);
        tender.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        tender.setTitle("测试标讯");

        AssignmentResult result = AssignmentResult.success(
                null,
                "08687",
                "王凯毅",
                null,
                null);
        when(autoAssignmentService.autoAssignIfPossible(tender, null)).thenReturn(result);
        when(projectManagerIdResolver.resolveByFullName("王凯毅")).thenReturn(5052L);

        support.tryAutoAssign(tender, null);

        assertThat(tender.getProjectManagerId()).isEqualTo(5052L);
        assertThat(tender.getProjectManagerName()).isEqualTo("王凯毅");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.TRACKING);
        verify(tenderRepository).save(tender);
        verify(assignmentNotifier).notifyAutoAssigned(tender);
    }

    @Test
    @DisplayName("CO-571: tryAutoAssign 状态转 TRACKING 时应将 userId 与 fullName 写入事件")
    void tryAutoAssign_withUserId_propagatesOperatorToEvent() {
        Tender tender = new Tender();
        tender.setId(100L);
        tender.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        tender.setTitle("测试标讯");

        AssignmentResult result = AssignmentResult.success(
                null,
                "08687",
                "王凯毅",
                null,
                null);
        when(autoAssignmentService.autoAssignIfPossible(tender, null)).thenReturn(result);
        when(projectManagerIdResolver.resolveByFullName("王凯毅")).thenReturn(5052L);
        User operator = new User();
        operator.setId(42L);
        operator.setFullName("郑蓉蓉");
        when(userRepository.findById(42L)).thenReturn(Optional.of(operator));

        support.tryAutoAssign(tender, 42L);

        ArgumentCaptor<TenderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(TenderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TenderStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.tenderId()).isEqualTo(100L);
        assertThat(event.oldStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(event.newStatus()).isEqualTo(Tender.Status.TRACKING);
        assertThat(event.operatorId()).isEqualTo(42L);
        assertThat(event.operatorName()).isEqualTo("郑蓉蓉");
    }

    @Test
    @DisplayName("tryAutoAssign: 未匹配到负责人时不改动")
    void tryAutoAssign_noMatch_noChanges() {
        Tender tender = new Tender();
        tender.setId(200L);
        tender.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        tender.setTitle("未匹配标讯");

        when(autoAssignmentService.autoAssignIfPossible(tender, null))
                .thenReturn(AssignmentResult.noMatch());

        support.tryAutoAssign(tender, null);

        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getProjectManagerName()).isNull();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        verify(tenderRepository, never()).save(any());
    }

    @Test
    @DisplayName("tryAutoAssign: 已有 CRM 商机负责人时跳过自动分配，不覆盖")
    void tryAutoAssign_alreadyHasManager_skipAutoAssignment() {
        Tender tender = new Tender();
        tender.setId(581L);
        tender.setStatus(Tender.Status.EVALUATED);
        tender.setTitle("测试商机");
        tender.setProjectManagerId(5052L);
        tender.setProjectManagerName("王凯毅");

        // 即使自动分配能匹配到另一个负责人，也不应该覆盖
        support.tryAutoAssign(tender, null);

        assertThat(tender.getProjectManagerId()).isEqualTo(5052L);
        assertThat(tender.getProjectManagerName()).isEqualTo("王凯毅");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        verify(autoAssignmentService, never()).autoAssignIfPossible(any(), any());
        verify(tenderRepository, never()).save(any());
    }

    @Test
    @DisplayName("tryAutoAssign: 只有 projectManagerName 没 id 时也跳过自动分配")
    void tryAutoAssign_hasNameOnly_skipAutoAssignment() {
        Tender tender = new Tender();
        tender.setId(582L);
        tender.setStatus(Tender.Status.EVALUATED);
        tender.setTitle("测试商机2");
        tender.setProjectManagerName("王凯毅");

        support.tryAutoAssign(tender, null);

        assertThat(tender.getProjectManagerName()).isEqualTo("王凯毅");
        assertThat(tender.getProjectManagerId()).isNull();
        verify(autoAssignmentService, never()).autoAssignIfPossible(any(), any());
    }

    // ===== 防"半关联"：applyCrmFallback 在 crmOpportunityId 为空时不应存入 name =====

    @Test
    @DisplayName("applyCrmFallback: hasCrmId 但 hasCode=false 时，不应单独存入 crmOpportunityName（防半关联）")
    void applyCrmFallback_crmIdOnly_noCode_doesNotSetHalfLinkName() {
        Tender tender = new Tender();
        // 场景：CRM 推送更新时传了 crmId=16 和 crmOpportunityName，但没传 crmOpportunityCode
        // 且 applyCrmLinkAndAssignment 中 CRM API 调用失败，crm_opportunity_id 保持 null

        support.applyCrmFallback(tender, "16", null, "0710商机10");

        // 防半关联：crmOpportunityId 为 null 时，name 也不应被设置
        assertThat(tender.getCrmOpportunityId()).isNull();
        assertThat(tender.getCrmOpportunityName()).isNull();
        // 状态和来源仍正常设置
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
    }

    @Test
    @DisplayName("applyCrmFallback: hasCode=true 时，name 照常存入（正常路径回归）")
    void applyCrmFallback_codeProvided_nameStoredNormally() {
        Tender tender = new Tender();

        support.applyCrmFallback(tender, null, "CC2026070930", "0710商机10");

        // 正常路径：code 非空时 id 和 name 都设置
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC2026070930");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("0710商机10");
    }

    // ===== CO-277 防回归：applyCrmFallback 纯数字 code 不应直接存入 =====

    @Test
    @DisplayName("applyCrmFallback: crmOpportunityCode 是纯数字 id 时不直接存入（PR !2011 回归根因）")
    void applyCrmFallback_numericCode_doesNotStoreNumericId() {
        // 场景：CRM 推送 crmOpportunityCode=21364（纯数字主键 id，CO-277 字段语义）
        // 期望：不直接存入 crm_opportunity_id 列，避免与"关联标讯"按钮设置的 CC 格式编号不一致
        // 导致去重校验失效（tender 1646 vs 1648 案例根因）
        Tender tender = new Tender();

        support.applyCrmFallback(tender, null, "21364", "0711关联商机测试");

        // 纯数字 id 不存入 crm_opportunity_id 列
        assertThat(tender.getCrmOpportunityId()).isNull();
        // 因 crmOpportunityId 为 null，name 也不应被设置（防"半关联"状态）
        assertThat(tender.getCrmOpportunityName()).isNull();
        // 但状态和来源仍正常设置
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
    }

    @Test
    @DisplayName("applyCrmFallback: 已有 CC 格式 crmOpportunityId 时，纯数字 code 不应覆盖")
    void applyCrmFallback_numericCode_doesNotOverwriteExistingCcCode() {
        // 场景：标讯已通过"关联标讯"按钮设置了 CC 格式编号，
        // 后续 CRM 推送 update 时传入纯数字 id，不应覆盖已有的 CC 编号
        Tender tender = new Tender();
        tender.setCrmOpportunityId("CC20260711739");
        tender.setCrmOpportunityName("0711关联商机测试");

        support.applyCrmFallback(tender, null, "21364", "其他商机名");

        // 已有 CC 编号不应被纯数字覆盖
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260711739");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("0711关联商机测试");
    }
}
