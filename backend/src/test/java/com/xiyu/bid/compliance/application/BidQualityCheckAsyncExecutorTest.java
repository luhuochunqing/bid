// Input: Mock 各依赖服务（AiProvider, Loaders, ProgressUpdater, Repository）
// Output: 验证异步任务全流程、缓存命中复用、buildLlmPort 结构化调用与精确重试契约、解析失败聚告警
// Pos: compliance/application/ - 异步执行器单元测试（包含 5 组 buildLlmPort 契约测试）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // =========================================================================
    // 1. Pipeline Lifecycle Tests
    // =========================================================================

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
    @DisplayName("正常全流程执行并调用 progressUpdater 完成任务（结构化 LLM 通道集成）")
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
        when(kbLoader.queryKnowledgeBases(any(), any())).thenReturn(KnowledgeQueryOutcome.empty());
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"核查通过\"}");

        asyncExecutor.executeAsync(resultId, projectId, taskId, false);

        verify(progressUpdater).updateProgress(eq(resultId), eq(ComplianceCheckResult.TaskStatus.PARSING), eq(20), anyString());
        verify(progressUpdater).updateProgress(eq(resultId), eq(ComplianceCheckResult.TaskStatus.CHECKING), eq(60), anyString());
        verify(progressUpdater).completeTask(eq(resultId), any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("聚告警：语义项解析失败达到阈值时输出 ERROR 聚告警（判定配置性问题，lessons #201）")
    void testExecuteAsync_ParseFailuresAtThreshold_EmitsAggregateAlert() {
        Long resultId = 1L;
        Long projectId = 100L;
        String taskId = "task-alert";

        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_alert", "正文包含西域资质");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender_alert", "招标要求");

        when(targetLoader.loadBidDocument(projectId)).thenReturn(bidDoc);
        when(tenderDocumentLoader.loadTenderDocument(projectId)).thenReturn(tenderDoc);
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_alert"), eq("hash_tender_alert"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(requirementsExtractor.extract(tenderDoc)).thenReturn(TenderRequirements.empty());
        when(kbLoader.queryKnowledgeBases(any(), any())).thenReturn(KnowledgeQueryOutcome.empty());
        // 全部语义项返回非法 JSON：每项 2 次调用均失败 → 7/7 计入失败计数
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("不是合法 JSON 文本");

        Logger executorLogger = (Logger) LoggerFactory.getLogger(BidQualityCheckAsyncExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try {
            asyncExecutor.executeAsync(resultId, projectId, taskId, false);
        } finally {
            executorLogger.detachAppender(appender);
        }

        List<ILoggingEvent> alerts = appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().contains("聚告警"))
                .toList();
        assertThat(alerts).as("达到阈值应输出一条 ERROR 聚告警").hasSize(1);
        assertThat(alerts.get(0).getFormattedMessage()).contains(taskId);
    }

    @Test
    @DisplayName("聚告警：失败数低于阈值时不输出 ERROR 聚告警")
    void testExecuteAsync_ParseFailuresBelowThreshold_NoAlert() {
        Long resultId = 1L;
        Long projectId = 100L;
        String taskId = "task-no-alert";

        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "hash_no_alert", "正文包含西域资质");
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_tender_no_alert", "招标要求");

        when(targetLoader.loadBidDocument(projectId)).thenReturn(bidDoc);
        when(tenderDocumentLoader.loadTenderDocument(projectId)).thenReturn(tenderDoc);
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_no_alert"), eq("hash_tender_no_alert"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(requirementsExtractor.extract(tenderDoc)).thenReturn(TenderRequirements.empty());
        when(kbLoader.queryKnowledgeBases(any(), any())).thenReturn(KnowledgeQueryOutcome.empty());
        // 前 13 次调用合法，其后全部非法 → 仅 2 个项失败，低于阈值 3
        String legal = "{\"status\":\"PASS\",\"conclusion\":\"核查通过\"}";
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn(legal, legal, legal, legal, legal, legal, legal, legal, legal, legal, legal, legal, legal)
                .thenReturn("不是合法 JSON 文本");

        Logger executorLogger = (Logger) LoggerFactory.getLogger(BidQualityCheckAsyncExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try {
            asyncExecutor.executeAsync(resultId, projectId, taskId, false);
        } finally {
            executorLogger.detachAppender(appender);
        }

        assertThat(appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().contains("聚告警"))
                .count())
                .as("低于阈值不应聚告警")
                .isZero();
    }

    // =========================================================================
    // 2. buildLlmPort & Retry Contract Tests
    // =========================================================================

    @Test
    @DisplayName("buildLlmPort：首次结构化输出成功 → 0 重试，返回 Success 且调用 chatCompletionJsonObject")
    void testBuildLlmPort_SuccessfulStructuredResponse_ReturnsSuccessWithoutRetry() {
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"资质完全齐备\"}");

        LlmVerdict.ChatOutcome<LlmVerdict> outcome = asyncExecutor.buildLlmPort().chat("裁决 prompt");

        assertThat(outcome).isInstanceOf(LlmVerdict.Success.class);
        LlmVerdict verdict = ((LlmVerdict.Success<LlmVerdict>) outcome).verdict();
        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.conclusion()).isEqualTo("资质完全齐备");

        verify(aiProvider, times(1)).chatCompletionJsonObject(eq("裁决 prompt"), eq(4096));
        verify(aiProvider, never()).chatCompletion(anyString(), anyInt());
    }

    @Test
    @DisplayName("buildLlmPort：首次解析失败 → 追加严格 JSON 约束重试 1 次，重试成功返回 Success")
    void testBuildLlmPort_ParseFailureThenSuccess_RetriesOnceWithStrictSuffix() {
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("裁决结果如下：这不是合法 JSON 字符串")
                .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"重试后解析成功\"}");

        LlmVerdict.ChatOutcome<LlmVerdict> outcome = asyncExecutor.buildLlmPort().chat("裁决 prompt");

        assertThat(outcome).isInstanceOf(LlmVerdict.Success.class);
        LlmVerdict verdict = ((LlmVerdict.Success<LlmVerdict>) outcome).verdict();
        assertThat(verdict.status()).isEqualTo("PASS");
        assertThat(verdict.conclusion()).isEqualTo("重试后解析成功");

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(aiProvider, times(2)).chatCompletionJsonObject(prompts.capture(), anyInt());
        assertThat(prompts.getAllValues().get(0)).isEqualTo("裁决 prompt");
        assertThat(prompts.getAllValues().get(1))
                .startsWith("裁决 prompt")
                .contains("仅输出一个 JSON 对象");
        verify(aiProvider, never()).chatCompletion(anyString(), anyInt());
    }

    @Test
    @DisplayName("buildLlmPort：重试后仍非法 → Invalid，最多重试 1 次")
    void testBuildLlmPort_StillInvalidAfterRetry_DegradesToInvalid() {
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("完全不是 JSON")
                .thenReturn("还是不是 JSON");

        LlmVerdict.ChatOutcome<LlmVerdict> outcome = asyncExecutor.buildLlmPort().chat("prompt");

        assertThat(outcome).isInstanceOf(LlmVerdict.Invalid.class);
        verify(aiProvider, times(2)).chatCompletionJsonObject(anyString(), anyInt());
        verify(aiProvider, never()).chatCompletion(anyString(), anyInt());
    }

    @Test
    @DisplayName("buildLlmPort：AI 未启用（Noop）不重试，仅调用 1 次")
    void testBuildLlmPort_AiDisabled_NoRetry() {
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenThrow(new IllegalStateException("AI 未启用"));

        LlmVerdict.ChatOutcome<LlmVerdict> outcome = asyncExecutor.buildLlmPort().chat("prompt");

        assertThat(outcome).isInstanceOf(LlmVerdict.Noop.class);
        verify(aiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
        verify(aiProvider, never()).chatCompletion(anyString(), anyInt());
    }

    @Test
    @DisplayName("buildLlmPort：调用异常（Failure）不重试，仅调用 1 次")
    void testBuildLlmPort_CallException_NoRetry() {
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenThrow(new RuntimeException("网关超时 504"));

        LlmVerdict.ChatOutcome<LlmVerdict> outcome = asyncExecutor.buildLlmPort().chat("prompt");

        assertThat(outcome).isInstanceOf(LlmVerdict.Failure.class);
        assertThat(((LlmVerdict.Failure<LlmVerdict>) outcome).message()).isEqualTo("网关超时 504");
        verify(aiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
        verify(aiProvider, never()).chatCompletion(anyString(), anyInt());
    }

    @Test
    @DisplayName("双轨融合 1358 实战：即使 LLM 提取降级为 missingSentinel，确定性规则依然捕获增值电信与等保三级并执行知识库比对")
    void testExecuteAsync_DualTrack_Incident1358_ExtractsQualificationsEvenWhenLlmReturnsMissing() {
        Long resultId = 2L;
        Long projectId = 1358L;
        String taskId = "task-1358";

        String text1358 = """
                3.供应商资格要求
                3.1 通用资格条件
                3.1.1 供应商须为中华人民共和国境内合法注册的独立法人或其他组织，具有独立承担民事责任能力，具有独立订立合同的权利。
                3.2 专用资格条件
                3.2.1 资质要求：供应商应具有以下证书：
                ①增值电信业务经营许可证，业务种类及覆盖范围需包括信息服务业务（互联网信息服务）及在线数据处理与交易业务（经营类电子商务）；
                ②公安部信息系统安全等级保护备案证明第三级或第三级以上证书。
                3.2.3 人员要求：/
                3.2.4 财务要求：/
                """;

        DocumentContent bidDoc = DocumentContent.fromText("bid-1358.pdf", "hash_bid_1358", "正文第42页包含增值电信业务经营许可证，第45页包含公安部等保三级证明");
        DocumentContent tenderDoc = DocumentContent.fromText("tender-1358.pdf", "hash_tender_1358", text1358);

        when(targetLoader.loadBidDocument(projectId)).thenReturn(bidDoc);
        when(tenderDocumentLoader.loadTenderDocument(projectId)).thenReturn(tenderDoc);
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_bid_1358"), eq("hash_tender_1358"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.empty());

        // 模拟 LLM 提取降级为 missingSentinel
        when(requirementsExtractor.extract(tenderDoc)).thenReturn(TenderRequirements.missingSentinel());
        when(kbLoader.queryKnowledgeBases(any(), any())).thenReturn(KnowledgeQueryOutcome.empty());
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"资质匹配\"}");

        asyncExecutor.executeAsync(resultId, projectId, taskId, false);

        // 验证：双轨提取生效，知识库查询接收到了确定性提取的增值电信与等保三级证书要求
        org.mockito.ArgumentCaptor<List<String>> certsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(kbLoader).queryKnowledgeBases(certsCaptor.capture(), any());
        List<String> queriedCerts = certsCaptor.getValue();

        assertThat(queriedCerts).isNotEmpty();
        assertThat(queriedCerts).anyMatch(c -> c.contains("增值电信业务经营许可证"));
        assertThat(queriedCerts).anyMatch(c -> c.contains("公安部信息系统安全等级保护备案证明"));

        // 验证任务顺利推进并完成
        verify(progressUpdater).completeTask(eq(resultId), any(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"①具有电力工程施工总承包特级资质。", "①投标人须具有ISO9001认证。"})
    @DisplayName("已知强制及未知要求的纯检索未命中均保持人工确认")
    void testExecuteAsync_QualificationLookupMiss_RequiresReview(String requirement) throws Exception {
        Long resultId = 3L;
        Long projectId = 1359L;
        String taskId = "task-1359";

        String tenderText = """
                3.2 专用资格条件
                3.2.1 资质要求：
                %s
                """.formatted(requirement);

        DocumentContent bidDoc = DocumentContent.fromText("bid-1359.pdf", "hash_bid_1359", "正文仅包含普通说明，无任何电力特级资质");
        DocumentContent tenderDoc = DocumentContent.fromText("tender-1359.pdf", "hash_tender_1359", tenderText);

        when(targetLoader.loadBidDocument(projectId)).thenReturn(bidDoc);
        when(tenderDocumentLoader.loadTenderDocument(projectId)).thenReturn(tenderDoc);
        when(resultRepository.findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                eq("hash_bid_1359"), eq("hash_tender_1359"), eq(ComplianceCheckResult.TaskStatus.COMPLETED)))
                .thenReturn(Optional.empty());

        when(requirementsExtractor.extract(tenderDoc)).thenReturn(TenderRequirements.missingSentinel());
        when(kbLoader.queryKnowledgeBases(any(), any())).thenReturn(KnowledgeQueryOutcome.empty());
        when(kbLoader.findAllQualifications()).thenReturn(List.of()); // 资质库空，标书无证明 -> UNMATCHED
        // 模拟 LLM 误判 PASS
        when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"AI 通读标书后误判通过\"}");

        asyncExecutor.executeAsync(resultId, projectId, taskId, false);

        org.mockito.ArgumentCaptor<String> detailsJsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

        verify(progressUpdater).completeTask(
                eq(resultId),
                any(ComplianceCheckResult.Status.class),
                anyInt(),
                detailsJsonCaptor.capture(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt());

        // 解析实际落库JSON中的资质单项，不以其他检查项的FAIL字符串替代断言。
        String json = detailsJsonCaptor.getValue();
        var items = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        var qualification = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(n -> n.path("itemCode").asText().equals("QUALIFICATION_MATCH")).findFirst().orElseThrow();
        assertThat(qualification.path("status").asText()).isEqualTo("MANUAL_REVIEW");
        assertThat(qualification.path("details").get(0).path("status").asText()).isEqualTo("ATTENTION");
        assertThat(qualification.path("details").get(0).path("finding").asText()).isEqualTo("LOOKUP_MISS");
    }
}
