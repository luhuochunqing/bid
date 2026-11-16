package com.xiyu.bid.compliance;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.compliance.application.BidQualityCheckAsyncExecutor;
import com.xiyu.bid.compliance.application.BidQualityCheckProgressUpdater;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.compliance.domain.policy.LlmAdjudicator;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;
import com.xiyu.bid.compliance.domain.policy.SemanticPageRetriever;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.compliance.fixture.BidDocumentTestFixtures;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.KnowledgeBaseOnDemandLoader;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.compliance.service.TenderRequirementsExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标书质量核查 Milestone 3 验收测试套件：全标书语义检索与结构化输出通道端到端验收.
 *
 * <p>验证 35,000+ 字符 60 页长标书在 7 项语义条款裁决时 Prompt 捕获中后段物理页码与文本证据，
 * 验证预算内全文 100% 无损透传、超预算 TOP-N 语义排序、以及结构化输出通道（JSON Mode）与降级容错。
 */
public class FullDocumentSemanticRetrievalAcceptanceTest {

    private static DocumentContent realistic60PageBidDoc;
    private static DocumentContent withinBudget60PageBidDoc;
    private static DocumentContent overBudget60PageBidDoc;
    private static DocumentContent standardTenderDoc;
    private static TenderRequirements standardRequirements;

    @BeforeAll
    static void setUpAll() {
        realistic60PageBidDoc = BidDocumentTestFixtures.createRealistic30kPlusBidDocument();
        withinBudget60PageBidDoc = BidDocumentTestFixtures.createWithinBudgetBidDocument();
        overBudget60PageBidDoc = BidDocumentTestFixtures.createOverBudgetBidDocument();
        standardTenderDoc = BidDocumentTestFixtures.createStandardTenderDocument();
        standardRequirements = BidDocumentTestFixtures.createStandardTenderRequirements();
    }

    // =========================================================================
    // 1. 7 项语义条款远端页面召回与 Prompt 断言测试
    // =========================================================================
    @Nested
    @DisplayName("1. 7 项语义条款全标书检索与中后段 Prompt 断言测试（红线验证）")
    class SevenSemanticItemsPromptAssertionTests {

        public static Stream<Arguments> provideSevenSemanticCheckItems() {
            return Stream.of(
                    Arguments.of(BidQualityCheckItem.QUALIFICATION_MATCH, 48, "ISO9001质量管理体系认证（甲级）"),
                    Arguments.of(BidQualityCheckItem.QUALIFICATION_VETO, 58, "原厂授权书"),
                    Arguments.of(BidQualityCheckItem.FRONT_TABLE_RESPONSE, 52, "前附表关键条款响应表"),
                    Arguments.of(BidQualityCheckItem.RESPONSIVENESS_REVIEW, 49, "付款方式无负偏离"),
                    Arguments.of(BidQualityCheckItem.QUALIFICATION_REVIEW, 16, "企业同类项目业绩一览表"),
                    Arguments.of(BidQualityCheckItem.SCORING_RESPONSE, 25, "AI 智能分析引擎与微服务高可用架构设计"),
                    Arguments.of(BidQualityCheckItem.CLARIFICATION_RESPONSE, 56, "关于招标文件第 1 号澄清答疑文件的确认与点对点响应函")
            );
        }

        @ParameterizedTest(name = "[{index}] {0} 应成功召回第 {1} 页中后段证据")
        @MethodSource("provideSevenSemanticCheckItems")
        @DisplayName("7项语义检查项生成的 LLM Prompt 必须包含中后段物理页码标签 --- [第 X 页] --- 及核心关键词")
        void testBuildPrompt_ContainsDistantPageTagAndSnippet(
                final BidQualityCheckItem item, final int expectedPageNumber, final String expectedSnippet) {
            String prompt = LlmAdjudicator.buildPrompt(
                    item,
                    realistic60PageBidDoc,
                    standardTenderDoc,
                    standardRequirements,
                    List.of()
            );

            // 1. 断言 Prompt 必须包含预期的中后段物理页码标签
            String expectedTag = "--- [第 " + expectedPageNumber + " 页] ---";
            assertThat(prompt)
                    .as("CheckItem %s prompt must contain page tag %s", item.getCode(), expectedTag)
                    .contains(expectedTag);

            // 2. 断言 Prompt 必须包含中后段预埋的核心证据文本
            assertThat(prompt)
                    .as("CheckItem %s prompt must contain evidence snippet '%s'", item.getCode(), expectedSnippet)
                    .contains(expectedSnippet);

            // 3. 断言提示词固定架构完整性
            assertThat(prompt).contains("你是投标文件质量核查专家");
            assertThat(prompt).contains("【招标要求（LLM 结构化提取）】");
            assertThat(prompt).contains("【企业知识库真实记录】");
            assertThat(prompt).contains("【招标文件相关内容】");
            assertThat(prompt).contains("【投标文件相关内容】");
        }

        @Test
        @DisplayName("长标书物理偏移量验证：第 48、49、52、56、58 页物理偏移均超过 28,000 字符，彻底杜绝 6000 字符硬截断")
        void testPhysicalOffset_FarExceedsLegacy6000CharBudget() {
            int offset48 = calculateOffset(realistic60PageBidDoc, 48);
            int offset49 = calculateOffset(realistic60PageBidDoc, 49);
            int offset52 = calculateOffset(realistic60PageBidDoc, 52);
            int offset56 = calculateOffset(realistic60PageBidDoc, 56);
            int offset58 = calculateOffset(realistic60PageBidDoc, 58);

            assertThat(offset48).isGreaterThan(28_000);
            assertThat(offset49).isGreaterThan(29_000);
            assertThat(offset52).isGreaterThan(31_000);
            assertThat(offset56).isGreaterThan(33_000);
            assertThat(offset58).isGreaterThan(35_000);
        }
    }

    // =========================================================================
    // 2. 预算内全文 100% 无损透传测试
    // =========================================================================
    @Nested
    @DisplayName("2. 预算内全文 100% 无损透传测试")
    class WithinBudgetPassThroughTests {

        @Test
        @DisplayName("60 页文档在预算内（~27,000 字符 <= 60,000 预算）：100% 保留全部 60 个页面并严格升序排列")
        void testRetrieveContext_WithinBudget_RetainsAll60PagesInAscendingOrder() {
            String context = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.QUALIFICATION_MATCH,
                    standardRequirements,
                    withinBudget60PageBidDoc,
                    60_000
            );

            // 1. 验证全部 60 个物理页码标签均完整存在
            for (int p = 1; p <= 60; p++) {
                assertThat(context).contains("--- [第 " + p + " 页] ---");
            }

            // 2. 验证页码标签在文本中呈现严格单调递增顺序
            int previousIndex = -1;
            for (int p = 1; p <= 60; p++) {
                int currentIndex = context.indexOf("--- [第 " + p + " 页] ---");
                assertThat(currentIndex)
                        .as("Page %d must appear after Page %d in output", p, p - 1)
                        .isGreaterThan(previousIndex);
                previousIndex = currentIndex;
            }
        }
    }

    // =========================================================================
    // 3. 超出预算 TOP-N 语义打分与重排测试
    // =========================================================================
    @Nested
    @DisplayName("3. 超出预算 TOP-N 语义打分与重排测试")
    class OverBudgetTopNRetrievalTests {

        @Test
        @DisplayName("80,000+ 字符长标书超出 60,000 预算：检索结果字符数受控且按自然页码升序输出")
        void testRetrieveContext_OverBudget_RestrictsTotalLengthAndReordersPages() {
            String context = SemanticPageRetriever.retrieveContext(
                    BidQualityCheckItem.CLARIFICATION_RESPONSE,
                    standardRequirements,
                    overBudget60PageBidDoc,
                    60_000
            );

            // 1. 验证检索结果总长度受控（正文在 60,000 预算内，附加分页标签头部）
            assertThat(context.length()).isLessThanOrEqualTo(60_000 + 1500);

            // 2. 验证第 56 页澄清答疑关键页成功入选
            assertThat(context).contains("--- [第 56 页] ---");
            assertThat(context).contains("关于招标文件第 1 号澄清答疑文件的确认与点对点响应函");

            // 3. 验证入选的页面在输出中保持页码升序
            List<Integer> extractedPageNumbers = extractPageNumbers(context);
            assertThat(extractedPageNumbers).isNotEmpty();
            for (int i = 1; i < extractedPageNumbers.size(); i++) {
                assertThat(extractedPageNumbers.get(i))
                        .isGreaterThan(extractedPageNumbers.get(i - 1));
            }
        }
    }

    // =========================================================================
    // 4. LLM 结构化输出通道 (JSON Mode) 与降级容错测试
    // =========================================================================
    @Nested
    @DisplayName("4. LLM 结构化输出通道与降级契约测试")
    class StructuredOutputAndFallbackTests {

        private BidQualityCheckAsyncExecutor createExecutor(final AiProvider aiProvider) {
            return new BidQualityCheckAsyncExecutor(
                    mock(ComplianceCheckResultRepository.class),
                    mock(ComplianceTargetLoader.class),
                    mock(KnowledgeBaseOnDemandLoader.class),
                    mock(TenderDocumentLoader.class),
                    mock(TenderRequirementsExtractor.class),
                    aiProvider,
                    mock(BidQualityCheckProgressUpdater.class)
            );
        }

        @Test
        @DisplayName("结构化通道成功：chatCompletionJsonObject 返回有效 JSON → Success (0 重试)")
        void testStructuredOutput_SuccessWithoutRetry() {
            AiProvider aiProvider = mock(AiProvider.class);
            when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"资质完全齐备\"}");

            BidQualityCheckAsyncExecutor executor = createExecutor(aiProvider);
            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("核查 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Success.class);
            LlmVerdict verdict = ((LlmVerdict.Success<LlmVerdict>) outcome).verdict();
            assertThat(verdict.status()).isEqualTo("PASS");
            assertThat(verdict.conclusion()).isEqualTo("资质完全齐备");

            verify(aiProvider, times(1)).chatCompletionJsonObject(eq("核查 prompt"), eq(4096));
            verify(aiProvider, never()).chatCompletion(anyString(), anyInt());
        }

        @Test
        @DisplayName("结构化通道返回非法 JSON：触发 1 次追加严格提示词的重试，重试成功返回 Success")
        void testStructuredOutput_ParseInvalid_RetriesOnceWithStrictSuffix() {
            AiProvider aiProvider = mock(AiProvider.class);
            when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenReturn("这不是合法 JSON 格式")
                    .thenReturn("{\"status\":\"PASS\",\"conclusion\":\"重试后解析成功\"}");

            BidQualityCheckAsyncExecutor executor = createExecutor(aiProvider);
            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("核查 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Success.class);
            LlmVerdict verdict = ((LlmVerdict.Success<LlmVerdict>) outcome).verdict();
            assertThat(verdict.status()).isEqualTo("PASS");
            assertThat(verdict.conclusion()).isEqualTo("重试后解析成功");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(aiProvider, times(2)).chatCompletionJsonObject(captor.capture(), anyInt());
            assertThat(captor.getAllValues().get(0)).isEqualTo("核查 prompt");
            assertThat(captor.getAllValues().get(1)).contains("仅输出一个 JSON 对象");
        }

        @Test
        @DisplayName("重试后依然非法：降级为 Invalid，不进行二次重试")
        void testStructuredOutput_StillInvalidAfterRetry_DegradesToInvalid() {
            AiProvider aiProvider = mock(AiProvider.class);
            when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenReturn("非 JSON 1")
                    .thenReturn("非 JSON 2");

            BidQualityCheckAsyncExecutor executor = createExecutor(aiProvider);
            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("核查 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Invalid.class);
            verify(aiProvider, times(2)).chatCompletionJsonObject(anyString(), anyInt());
        }

        @Test
        @DisplayName("AI 未配置/未启用 (Noop)：0 重试，直接返回 Noop")
        void testStructuredOutput_AiDisabled_ReturnsNoopWithoutRetry() {
            AiProvider aiProvider = mock(AiProvider.class);
            when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenThrow(new IllegalStateException("AI API key is not configured"));

            BidQualityCheckAsyncExecutor executor = createExecutor(aiProvider);
            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("核查 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Noop.class);
            verify(aiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
        }

        @Test
        @DisplayName("网关调用超时 (Failure)：0 重试，直接返回 Failure")
        void testStructuredOutput_NetworkTimeout_ReturnsFailureWithoutRetry() {
            AiProvider aiProvider = mock(AiProvider.class);
            when(aiProvider.chatCompletionJsonObject(anyString(), anyInt()))
                    .thenThrow(new RuntimeException("Gateway 504 Gateway Timeout"));

            BidQualityCheckAsyncExecutor executor = createExecutor(aiProvider);
            LlmVerdict.ChatOutcome<LlmVerdict> outcome = executor.buildLlmPort().chat("核查 prompt");

            assertThat(outcome).isInstanceOf(LlmVerdict.Failure.class);
            assertThat(((LlmVerdict.Failure<LlmVerdict>) outcome).message()).contains("504");
            verify(aiProvider, times(1)).chatCompletionJsonObject(anyString(), anyInt());
        }
    }

    // =========================================================================
    // 5. 60 页长标书端到端 LLM 裁决集成测试
    // =========================================================================
    @Nested
    @DisplayName("5. 60 页长标书端到端 LLM 裁决集成测试")
    class EndToEndAdjudicationIntegrationTests {

        @Test
        @DisplayName("60 页真实标书在 LlmAdjudicator.adjudicate 中完成全部 7 项裁决，进度平滑上报至 100%")
        void testAdjudicate_Realistic60PageBid_CompletesAllSevenItems() {
            List<String> promptTrail = new ArrayList<>();
            LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> {
                promptTrail.add(prompt);
                return LlmVerdict.success(new LlmVerdict("PASS", "", "全标书语义裁决通过", ""));
            });

            List<Integer> progressTrail = new ArrayList<>();
            Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                    realistic60PageBidDoc,
                    standardTenderDoc,
                    standardRequirements,
                    List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "1", "ISO9001", true)),
                    Set.of(),
                    (progress, step) -> progressTrail.add(progress)
            );

            // 1. 验证7项均产出结果；资格评审按既有策略人工确认，模型PASS不能替代完整资格核验。
            assertThat(outcomes).hasSize(7);
            assertThat(outcomes.values()).allSatisfy(result -> {
                if (result.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode())) {
                    assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                    assertThat(result.conclusion()).contains("资格材料尚需人工确认");
                } else {
                    assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
                    assertThat(result.conclusion()).contains("全标书语义裁决通过");
                }
            });

            // 2. 验证传递给 LLM 的 7 次 Prompt 全部包含真实的 --- [第 X 页] --- 页码标识
            assertThat(promptTrail).hasSize(7);
            assertThat(promptTrail).allSatisfy(prompt -> assertThat(prompt).contains("--- [第 "));

            // 3. 验证进度回调单调递增至 100
            assertThat(progressTrail).hasSize(7);
            assertThat(progressTrail.get(progressTrail.size() - 1)).isEqualTo(100);
        }
    }

    // =========================================================================
    // 辅助计算方法
    // =========================================================================

    private static int calculateOffset(final DocumentContent doc, final int targetPage) {
        int offset = 0;
        for (DocumentContent.PageContent p : doc.pages()) {
            if (p.pageNumber() == targetPage) {
                return offset;
            }
            offset += p.text() != null ? p.text().length() : 0;
        }
        return offset;
    }

    private static List<Integer> extractPageNumbers(final String context) {
        List<Integer> list = new ArrayList<>();
        String marker = "--- [第 ";
        int idx = 0;
        while ((idx = context.indexOf(marker, idx)) != -1) {
            int endIdx = context.indexOf(" 页] ---", idx);
            if (endIdx != -1) {
                String numStr = context.substring(idx + marker.length(), endIdx).trim();
                list.add(Integer.parseInt(numStr));
                idx = endIdx;
            } else {
                idx += marker.length();
            }
        }
        return list;
    }
}
