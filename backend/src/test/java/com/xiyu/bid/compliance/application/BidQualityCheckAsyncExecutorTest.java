package com.xiyu.bid.compliance.application;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.KnowledgeBaseOnDemandLoader;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.compliance.service.TenderRequirementsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidQualityCheckAsyncExecutorTest {

    @Mock
    private ComplianceCheckResultRepository resultRepository;

    @Mock
    private ComplianceTargetLoader targetLoader;

    @Mock
    private KnowledgeBaseOnDemandLoader kbLoader;

    @Mock
    private TenderDocumentLoader tenderDocumentLoader;

    @Mock
    private TenderRequirementsExtractor requirementsExtractor;

    @Mock
    private AiProvider aiProvider;

    @Mock
    private BidQualityCheckProgressUpdater progressUpdater;

    private BidQualityCheckAsyncExecutor asyncExecutor;

    @BeforeEach
    void setUp() {
        asyncExecutor = new BidQualityCheckAsyncExecutor(
                resultRepository,
                targetLoader,
                kbLoader,
                tenderDocumentLoader,
                requirementsExtractor,
                aiProvider,
                progressUpdater
        );
    }

    @Test
    @DisplayName("投标文件不可解析时置为 FAILED 并终止任务")
    void testExecuteAsync_UnparsableBidDocument() {
        Long resultId = 1L;
        Long projectId = 100L;
        String taskId = "task-001";

        when(targetLoader.loadBidDocument(projectId)).thenReturn(DocumentContent.empty());

        asyncExecutor.executeAsync(resultId, projectId, taskId, false);

        verify(progressUpdater).updateProgress(eq(resultId), eq(ComplianceCheckResult.TaskStatus.PARSING), eq(20), anyString());
        verify(progressUpdater).markFailed(eq(resultId), contains("解析失败"));
        verify(progressUpdater, never()).completeTask(any(), any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("force=false 且 SHA-256 命中缓存时复用缓存结果")
    void testExecuteAsync_CacheHit() {
        Long resultId = 1L;
        Long projectId = 100L;
        String taskId = "task-001";

        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid", "正文");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender", "要求");

        when(targetLoader.loadBidDocument(projectId)).thenReturn(bidDoc);
        when(tenderDocumentLoader.loadTenderDocument(projectId)).thenReturn(tenderDoc);

        ComplianceCheckResult cached = ComplianceCheckResult.builder()
                .id(99L)
                .taskId("cached-task-99")
                .status(ComplianceCheckResult.TaskStatus.COMPLETED)
                .build();
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_bid"), eq("hash_tender"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.of(cached));

        asyncExecutor.executeAsync(resultId, projectId, taskId, false);

        verify(progressUpdater).updateProgress(eq(resultId), eq(ComplianceCheckResult.TaskStatus.PARSING), eq(20), anyString());
        verify(progressUpdater).updateFileMetadata(eq(resultId), eq("bid.pdf"), eq("hash_bid"), eq("hash_tender"));
        verify(progressUpdater).applyCachedResult(eq(resultId), eq(cached));
        verify(progressUpdater, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("正常全流程执行并调用 progressUpdater 完成任务")
    void testExecuteAsync_SuccessfulExecution() {
        Long resultId = 1L;
        Long projectId = 100L;
        String taskId = "task-001";

        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_bid_new", "正文包含西域资质");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender_new", "招标要求");

        when(targetLoader.loadBidDocument(projectId)).thenReturn(bidDoc);
        when(tenderDocumentLoader.loadTenderDocument(projectId)).thenReturn(tenderDoc);
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_bid_new"), eq("hash_tender_new"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.empty());

        TenderRequirements reqs = TenderRequirements.empty();
        when(requirementsExtractor.extract(tenderDoc)).thenReturn(reqs);
        when(kbLoader.queryKnowledgeBases(any())).thenReturn(KnowledgeQueryOutcome.empty());

        asyncExecutor.executeAsync(resultId, projectId, taskId, false);

        verify(progressUpdater).updateProgress(eq(resultId), eq(ComplianceCheckResult.TaskStatus.PARSING), eq(20), anyString());
        verify(progressUpdater).updateProgress(eq(resultId), eq(ComplianceCheckResult.TaskStatus.CHECKING), eq(60), anyString());
        verify(progressUpdater).completeTask(eq(resultId), any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}
