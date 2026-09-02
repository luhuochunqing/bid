// Input: BidQualityCheckTaskAppService 依赖 mock（无投标文件/单册/多册场景）
// Output: 验证 trigger 同步前置校验——无文件 4xx 不落任务，有文件正常建任务且 bidFileName 多册拼接
// Pos: compliance/application/ - trigger 前置校验单测（spec 049 US1 / T018 + 多文件质量核查）
package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.dto.QualityCheckTaskDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec 049 US1 / contracts/bid-quality-api.md §1：
 * 项目无 BID 类 ProjectDocument 时 trigger 同步返回 4xx 且不创建任务.
 */
@ExtendWith(MockitoExtension.class)
class BidQualityCheckTriggerPreconditionTest {

    private static final Long PROJECT_ID = 200L;

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

    @Test
    @DisplayName("无投标文件时 trigger 抛业务异常（4xx）且不落任务记录")
    void trigger_noBidDocument_throwsAndDoesNotCreateTask() {
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(
                any(), any(), any())).thenReturn(Collections.emptyList());
        when(targetLoader.findAllBidDocuments(PROJECT_ID)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> taskAppService.triggerQualityCheck(PROJECT_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先上传投标文件");

        verify(resultRepository, never()).save(any());
        verify(asyncExecutor, never()).executeAsync(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("多册投标文件时毫秒级创建任务，任务记录的 bidFileName 按上传顺序顿号连接")
    void trigger_multipleBidDocuments_bidFileNameJoined() {
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(
                any(), any(), any())).thenReturn(Collections.emptyList());
        ProjectDocument price = new ProjectDocument();
        price.setId(1L);
        price.setName("1-价格册.pdf");
        price.setFileUrl("bid-agent://tender-documents/p200/price.pdf");
        ProjectDocument biz = new ProjectDocument();
        biz.setId(2L);
        biz.setName("2-商务册.pdf");
        biz.setFileUrl("bid-agent://tender-documents/p200/biz.pdf");
        when(targetLoader.findAllBidDocuments(PROJECT_ID)).thenReturn(List.of(price, biz));

        ComplianceCheckResult saved = ComplianceCheckResult.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .taskId("task-002")
                .status(ComplianceCheckResult.TaskStatus.INIT)
                .progress(0)
                .build();
        when(resultRepository.save(any(ComplianceCheckResult.class))).thenReturn(saved);

        QualityCheckTaskDTO task = taskAppService.triggerQualityCheck(PROJECT_ID, true);

        assertThat(task.getTaskId()).isEqualTo("task-002");
        org.mockito.ArgumentCaptor<ComplianceCheckResult> captor =
                org.mockito.ArgumentCaptor.forClass(ComplianceCheckResult.class);
        verify(resultRepository).save(captor.capture());
        assertThat(captor.getValue().getBidFileName()).isEqualTo("1-价格册.pdf、2-商务册.pdf");
    }

    @Test
    @DisplayName("文档记录存在且有 fileUrl 时毫秒级创建任务并启动异步执行")
    void trigger_withBidDocument_createsTask() {
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(
                any(), any(), any())).thenReturn(Collections.emptyList());
        ProjectDocument doc = new ProjectDocument();
        doc.setId(7L);
        doc.setName("投标文件.pdf");
        doc.setFileUrl("bid-agent://tender-documents/p200/bid.pdf");
        when(targetLoader.findAllBidDocuments(PROJECT_ID)).thenReturn(List.of(doc));

        ComplianceCheckResult saved = ComplianceCheckResult.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .taskId("task-001")
                .status(ComplianceCheckResult.TaskStatus.INIT)
                .progress(0)
                .build();
        when(resultRepository.save(any(ComplianceCheckResult.class))).thenReturn(saved);

        QualityCheckTaskDTO task = taskAppService.triggerQualityCheck(PROJECT_ID, true);

        assertThat(task.getTaskId()).isEqualTo("task-001");
        verify(resultRepository).save(any(ComplianceCheckResult.class));
        verify(asyncExecutor).executeAsync(eq(1L), eq(PROJECT_ID), any(String.class), eq(true));
    }
}
