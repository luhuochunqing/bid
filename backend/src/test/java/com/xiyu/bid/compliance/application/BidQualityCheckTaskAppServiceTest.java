package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.dto.QualityCheckReportDTO;
import com.xiyu.bid.compliance.dto.QualityCheckTaskDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private BidQualityCheckAsyncExecutor asyncExecutor;

    private BidQualityCheckTaskAppService taskAppService;

    @BeforeEach
    void setUp() {
        taskAppService = new BidQualityCheckTaskAppService(resultRepository, targetLoader, asyncExecutor);
    }

    /** spec 049 前置校验：stub 项目存在已上传投标文件. */
    private void stubBidDocumentExists(final Long projectId) {
        ProjectDocument doc = new ProjectDocument();
        doc.setId(1L);
        doc.setName("bid.pdf");
        doc.setFileUrl("bid-agent://tender-documents/p" + projectId + "/bid.pdf");
        when(targetLoader.findLatestBidDocument(projectId)).thenReturn(doc);
    }

    @Test
    @DisplayName("首次触发核查时创建新任务并异步执行")
    void testTriggerQualityCheck_NewTask() {
        Long projectId = 100L;
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(eq(projectId), any(), any()))
                .thenReturn(Collections.emptyList());
        stubBidDocumentExists(projectId);
        when(targetLoader.loadBidDocument(projectId))
                .thenReturn(DocumentContent.fromText("bid.pdf", "hash_bid_1", " 投标文件正文"));
        when(targetLoader.loadTenderDocument(projectId))
                .thenReturn(DocumentContent.fromText("tender.pdf", "hash_tender_1", "招标要求"));
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_bid_1"), eq("hash_tender_1"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.empty());

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
        assertThat(taskDTO.getTaskId()).isNotBlank();
        verify(asyncExecutor).executeAsync(eq(1L), eq(projectId), any(String.class));
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
    @DisplayName("文件哈希一致且 force=false 时命中缓存秒级返回")
    void testTriggerQualityCheck_CacheHit() {
        Long projectId = 100L;
        when(resultRepository.findByProjectIdAndCheckTypeAndStatusIn(eq(projectId), any(), any()))
                .thenReturn(Collections.emptyList());
        stubBidDocumentExists(projectId);
        when(targetLoader.loadBidDocument(projectId))
                .thenReturn(DocumentContent.fromText("bid.pdf", "hash_cached", "已解析内容"));
        when(targetLoader.loadTenderDocument(projectId))
                .thenReturn(DocumentContent.fromText("tender.pdf", "hash_tender", "招标要求"));

        ComplianceCheckResult cached = ComplianceCheckResult.builder()
                .id(3L)
                .projectId(projectId)
                .taskId("cached_task_789")
                .status(ComplianceCheckResult.TaskStatus.COMPLETED)
                .progress(100)
                .build();

        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_cached"), eq("hash_tender"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.of(cached));

        QualityCheckTaskDTO taskDTO = taskAppService.triggerQualityCheck(projectId, false);

        assertThat(taskDTO.getTaskId()).isEqualTo("cached_task_789");
        assertThat(taskDTO.getStatus()).isEqualTo(ComplianceCheckResult.TaskStatus.COMPLETED);
    }
}
