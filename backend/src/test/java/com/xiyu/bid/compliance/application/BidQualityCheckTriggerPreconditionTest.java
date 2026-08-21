// Input: BidQualityCheckTaskAppService 依赖 mock（有/无投标文件两种场景）
// Output: 验证 trigger 同步前置校验——无文件 4xx 不落任务，有文件正常建任务
// Pos: compliance/application/ - trigger 前置校验单测（spec 049 US1 / T018）
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
        when(targetLoader.findLatestBidDocument(PROJECT_ID)).thenReturn(null);

        assertThatThrownBy(() -> taskAppService.triggerQualityCheck(PROJECT_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先上传投标文件");

        verify(resultRepository, never()).save(any());
        verify(asyncExecutor, never()).executeAsync(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("文档记录存在但 fileUrl 为空时同样拒绝触发")
    void trigger_bidDocumentWithoutFileUrl_throwsAndDoesNotCreateTask() {
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(
                any(), any(), any())).thenReturn(Collections.emptyList());
        ProjectDocument doc = new ProjectDocument();
        doc.setId(7L);
        doc.setFileUrl("");
        when(targetLoader.findLatestBidDocument(PROJECT_ID)).thenReturn(doc);

        assertThatThrownBy(() -> taskAppService.triggerQualityCheck(PROJECT_ID, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先上传投标文件");

        verify(resultRepository, never()).save(any());
        verify(asyncExecutor, never()).executeAsync(any(), any(), any(), anyBoolean());
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
        when(targetLoader.findLatestBidDocument(PROJECT_ID)).thenReturn(doc);

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
