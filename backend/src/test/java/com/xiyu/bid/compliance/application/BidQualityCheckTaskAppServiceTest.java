// Input: Mock 依赖（Repository, Loaders, AsyncExecutor）
// Output: 验证任务流转、毫秒级响应、事务 afterCommit 同步与快照回填
// Pos: compliance/application/ - 质量核查任务编排服务单元测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.dto.QualityCheckReportDTO;
import com.xiyu.bid.compliance.dto.QualityCheckTaskDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidQualityCheckTaskAppServiceTest {

    @Mock
    private ComplianceCheckResultRepository resultRepository;

    @Mock
    private ComplianceTargetLoader targetLoader;

    @Mock
    private TenderDocumentLoader tenderDocumentLoader;

    @Mock
    private BidQualityCheckAsyncExecutor asyncExecutor;

    private BidQualityCheckTaskAppService taskAppService;

    @BeforeEach
    void setUp() {
        taskAppService = new BidQualityCheckTaskAppService(
                resultRepository, targetLoader, tenderDocumentLoader, asyncExecutor);
    }

    private void stubBidDocumentExists(final Long projectId) {
        ProjectDocument doc = new ProjectDocument();
        doc.setId(1L);
        doc.setName("bid.pdf");
        doc.setFileUrl("bid-agent://tender-documents/p" + projectId + "/bid.pdf");
        when(targetLoader.findAllBidDocuments(projectId)).thenReturn(List.of(doc));
    }

    @Test
    @DisplayName("首次触发核查时毫秒级创建新任务并异步执行")
    void testTriggerQualityCheck_NewTask() {
        Long projectId = 100L;
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(eq(projectId), any(), any()))
                .thenReturn(Collections.emptyList());
        stubBidDocumentExists(projectId);

        ComplianceCheckResult saved = ComplianceCheckResult.builder()
                .id(1L)
                .projectId(projectId)
                .taskId("test_task_123")
                .status(ComplianceCheckResult.TaskStatus.INIT)
                .progress(0)
                .build();
        when(resultRepository.save(any())).thenReturn(saved);

        QualityCheckTaskDTO taskDTO = taskAppService.triggerQualityCheck(projectId, false);

        assertThat(taskDTO).isNotNull();
        assertThat(taskDTO.getTaskId()).isEqualTo("test_task_123");
        verify(asyncExecutor).executeAsync(eq(1L), eq(projectId), any(String.class), eq(false));
    }

    @Test
    @DisplayName("活动事务下在 afterCommit 钩子中触发异步执行")
    void testTriggerQualityCheck_ActiveTransaction_TriggersAfterCommit() {
        Long projectId = 101L;
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(eq(projectId), any(), any()))
                .thenReturn(Collections.emptyList());
        stubBidDocumentExists(projectId);

        ComplianceCheckResult saved = ComplianceCheckResult.builder()
                .id(2L)
                .projectId(projectId)
                .taskId("tx_task_456")
                .status(ComplianceCheckResult.TaskStatus.INIT)
                .progress(0)
                .build();
        when(resultRepository.save(any())).thenReturn(saved);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            QualityCheckTaskDTO taskDTO = taskAppService.triggerQualityCheck(projectId, true);
            assertThat(taskDTO).isNotNull();
            assertThat(taskDTO.getTaskId()).isEqualTo("tx_task_456");

            // 提交前尚未触发
            org.mockito.Mockito.verify(asyncExecutor, org.mockito.Mockito.never())
                    .executeAsync(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

            // 模拟事务提交
            for (org.springframework.transaction.support.TransactionSynchronization syn :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                syn.afterCommit();
            }

            verify(asyncExecutor).executeAsync(eq(2L), eq(projectId), any(String.class), eq(true));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("存在运行中的任务时直接返回该进行中任务")
    void testTriggerQualityCheck_ExistingRunningTask() {
        Long projectId = 100L;
        ComplianceCheckResult running = ComplianceCheckResult.builder()
                .id(2L)
                .projectId(projectId)
                .taskId("running_task_456")
                .status(ComplianceCheckResult.TaskStatus.CHECKING)
                .progress(60)
                .build();

        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(eq(projectId), any(), any()))
                .thenReturn(List.of(running));

        QualityCheckTaskDTO taskDTO = taskAppService.triggerQualityCheck(projectId, false);

        assertThat(taskDTO.getTaskId()).isEqualTo("running_task_456");
        assertThat(taskDTO.getStatus()).isEqualTo(ComplianceCheckResult.TaskStatus.CHECKING);
        assertThat(taskDTO.getProgress()).isEqualTo(60);
    }

    @Test
    @DisplayName("项目ID为空时抛出非法参数异常")
    void testTriggerQualityCheck_NullProjectId() {
        assertThatThrownBy(() -> taskAppService.triggerQualityCheck(null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("历史快照（旧名称/无 description）读取时按 itemCode 回填标准名称与检查项说明")
    void testToReportDTO_LegacySnapshot_BackfillsCanonicalDefinition() {
        // 旧快照：title 为旧名称，无 description 字段（新增字段前的持久化格式）
        String legacyJson = """
                [{"itemCode":"AUTHORIZATION_LETTER","category":"BASIC_INFO",
                  "title":"法定代表人身份证明与授权委托书","status":"PASS","severity":"CRITICAL",
                  "order":3,"conclusion":"内容完备","suggestion":"","evidences":[]}]
                """;
        ComplianceCheckResult entity = ComplianceCheckResult.builder()
                .id(9L)
                .projectId(100L)
                .taskId("legacy_task")
                .status(ComplianceCheckResult.TaskStatus.COMPLETED)
                .checkDetails(legacyJson)
                .build();

        QualityCheckReportDTO report = BidQualityCheckTaskAppService.toReportDTO(entity);

        assertThat(report.getItems()).hasSize(1);
        assertThat(report.getItems().get(0).title())
                .isEqualTo(BidQualityCheckItem.AUTHORIZATION_LETTER.getTitle());
        assertThat(report.getItems().get(0).description())
                .isEqualTo(BidQualityCheckItem.AUTHORIZATION_LETTER.getDescription());
        assertThat(report.getItems().get(0).conclusion()).isEqualTo("内容完备");
        assertThat(report.getItems().get(0).details()).isEmpty();
    }

    @Test
    @DisplayName("结构化资质比对详情反序列化与下发")
    void testToReportDTO_WithStructuredQualificationDetails() {
        String jsonWithDetails = """
                [{"itemCode":"QUALIFICATION_MATCH","category":"BASIC_INFO",
                  "title":"资质与证书有效性","description":"检查投标资质","status":"PASS","severity":"CRITICAL",
                  "order":4,"conclusion":"全部匹配","suggestion":"","evidences":[],
                  "details":[{"requirement":"ISO9001","matchedCertName":"ISO9001质量认证","status":"MATCHED",
                             "bidPageNumber":15,"bidQuote":"第15页证明","remainingDays":300,"remark":"证书有效"}]}]
                """;
        ComplianceCheckResult entity = ComplianceCheckResult.builder()
                .id(10L)
                .projectId(100L)
                .taskId("task_with_details")
                .status(ComplianceCheckResult.TaskStatus.COMPLETED)
                .checkDetails(jsonWithDetails)
                .build();

        QualityCheckReportDTO report = BidQualityCheckTaskAppService.toReportDTO(entity);

        assertThat(report.getItems()).hasSize(1);
        assertThat(report.getItems().get(0).details()).hasSize(1);
        var detail = report.getItems().get(0).details().get(0);
        assertThat(detail.requirement()).isEqualTo("ISO9001");
        assertThat(detail.matchedCertName()).isEqualTo("ISO9001质量认证");
        assertThat(detail.status()).isEqualTo("MATCHED");
        assertThat(detail.bidPageNumber()).isEqualTo(15);
        assertThat(detail.remainingDays()).isEqualTo(300);
    }
}
