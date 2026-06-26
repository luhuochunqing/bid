package com.xiyu.bid.integration.external;

import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

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

    private TenderIntegrationCommandSupport support;

    @BeforeEach
    void setUp() {
        support = new TenderIntegrationCommandSupport(
                crmTenderLinkService,
                autoAssignmentService,
                assignmentNotifier,
                eventPublisher,
                tenderRepository,
                projectManagerIdResolver);
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
        when(autoAssignmentService.autoAssignIfPossible(tender)).thenReturn(result);
        when(projectManagerIdResolver.resolveByFullName("郑蓉蓉")).thenReturn(2556L);

        support.tryAutoAssign(tender);

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
        when(autoAssignmentService.autoAssignIfPossible(tender)).thenReturn(result);
        when(projectManagerIdResolver.resolveByFullName("王凯毅")).thenReturn(5052L);

        support.tryAutoAssign(tender);

        assertThat(tender.getProjectManagerId()).isEqualTo(5052L);
        assertThat(tender.getProjectManagerName()).isEqualTo("王凯毅");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.TRACKING);
        verify(tenderRepository).save(tender);
        verify(assignmentNotifier).notifyAutoAssigned(tender);
    }

    @Test
    @DisplayName("tryAutoAssign: 未匹配到负责人时不改动")
    void tryAutoAssign_noMatch_noChanges() {
        Tender tender = new Tender();
        tender.setId(200L);
        tender.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        tender.setTitle("未匹配标讯");

        when(autoAssignmentService.autoAssignIfPossible(tender))
                .thenReturn(AssignmentResult.noMatch());

        support.tryAutoAssign(tender);

        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getProjectManagerName()).isNull();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        verify(tenderRepository, never()).save(any());
    }
}
