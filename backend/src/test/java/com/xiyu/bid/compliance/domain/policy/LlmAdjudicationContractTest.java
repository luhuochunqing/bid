// Input: 固定输入（TenderRequirements + KnowledgeMatch + 投标/招标分页文档）+ 固定 LLM 通道结果（ChatOutcome 四值）
// Output: 断言语义项裁决结构（PASS/FAIL/MANUAL + reasonCode 前缀 + evidence）、单项失败 LOW_CONFIDENCE、Noop 全 MANUAL、prompt 包含 --- [第 X 页] --- 物理页码标识
// Pos: compliance/domain/policy/ - LLM 混合裁决契约测试（spec 049 US5 / T045，research.md R6，PRD §3.5，R1 全标书分析契约）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.service.LlmVerdictParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAdjudicationContractTest {

    private static final List<String> SEMANTIC_CODES = List.of(
            BidQualityCheckItem.QUALIFICATION_MATCH.getCode(),
            BidQualityCheckItem.QUALIFICATION_VETO.getCode(),
            BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode(),
            BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode(),
            BidQualityCheckItem.QUALIFICATION_REVIEW.getCode(),
            BidQualityCheckItem.SCORING_RESPONSE.getCode(),
            BidQualityCheckItem.CLARIFICATION_RESPONSE.getCode());

    private static DocumentContent bidDoc() {
        return DocumentContent.fromText("bid.pdf", "hash_bid",
                "投标函\f技术方案与实施方案详细说明\f澄清答疑响应确认函");
    }

    private static DocumentContent tenderDoc() {
        return DocumentContent.fromText("tender.pdf", "hash_t",
                "投标人须具备 ISO9001 认证\f评分办法：技术方案占 30 分");
    }

    private static TenderRequirements requirements() {
        return new TenderRequirements(
                List.of(new TenderRequirements.ScoringItem("技术方案", 30.0, "方案完整性")),
                List.of("ISO9001 认证"),
                List.of(), List.of(), List.of(), false);
    }

    // =========================================================================
    // 1. Prompt 结构与全标书 RAG 契约测试
    // =========================================================================

    @Test
    @DisplayName("Prompt 契约：buildPrompt 包含【招标文件相关内容】与【投标文件相关内容】，并包含 --- [第 X 页] --- 物理页码标识")
    void testBuildPrompt_ContainsPageTags_And_PassesAllDocContentWithinBudget() {
        String prompt = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.SCORING_RESPONSE,
                bidDoc(),
                tenderDoc(),
                requirements(),
                List.of());

        assertThat(prompt).contains("你是投标文件质量核查专家");
        assertThat(prompt).contains("【招标要求（LLM 结构化提取）】");
        assertThat(prompt).contains("【企业知识库真实记录】");
        assertThat(prompt).contains("【招标文件相关内容】");
        assertThat(prompt).contains("【投标文件相关内容】");

        // 验证保留了 --- [第 X 页] --- 页码标签
        assertThat(prompt).contains("--- [第 1 页] ---");
        assertThat(prompt).contains("--- [第 2 页] ---");
        assertThat(prompt).contains("--- [第 3 页] ---");
        assertThat(prompt).contains("技术方案与实施方案详细说明");
        assertThat(prompt).contains("澄清答疑响应确认函");
    }

    @Test
    @DisplayName("Prompt 契约：长标书（超过旧版 6000 字符硬截断限制）的中后段关键内容完整透传至 Prompt")
    void testBuildPrompt_WithLongDoc_PullsMiddleAndBackPagesIntoPrompt() {
        // 构造一个包含 20 页、前 5 页超过 6000 字符的标书文档
        List<DocumentContent.PageContent> pages = new ArrayList<>();
        pages.add(new DocumentContent.PageContent(1, "封面".repeat(1000), Collections.emptyList()));
        pages.add(new DocumentContent.PageContent(2, "目录".repeat(1000), Collections.emptyList()));
        pages.add(new DocumentContent.PageContent(3, "声明".repeat(1000), Collections.emptyList()));
        pages.add(new DocumentContent.PageContent(10, "技术方案分卷：详述实施组织设计与技术路线", List.of("第十章 技术方案")));
        pages.add(new DocumentContent.PageContent(20, "澄清文件确认函：承诺无条件响应答疑纪要", List.of("第二十章 澄清确认")));

        DocumentContent longBidDoc = new DocumentContent("long_bid.pdf", "hash_long", "", pages);

        String prompt = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.SCORING_RESPONSE,
                longBidDoc,
                tenderDoc(),
                requirements(),
                List.of());

        // 验证中后段关键内容未被截断丢弃
        assertThat(prompt).contains("--- [第 10 页] ---");
        assertThat(prompt).contains("技术方案分卷：详述实施组织设计与技术路线");
    }

    @Test
    @DisplayName("Prompt 契约：文档为空时，Prompt 相应部分呈现 '（无内容）'")
    void testBuildPrompt_WhenDocsEmpty_ContainsNoContentFallback() {
        String prompt = LlmAdjudicator.buildPrompt(
                BidQualityCheckItem.QUALIFICATION_MATCH,
                DocumentContent.empty(),
                DocumentContent.empty(),
                TenderRequirements.empty(),
                List.of());

        assertThat(prompt).contains("【招标文件相关内容】\n（无内容）");
        assertThat(prompt).contains("【投标文件相关内容】\n（无内容）");
    }

    // =========================================================================
    // 2. LLM 裁决与 ChatOutcome 四值状态契约测试
    // =========================================================================

    @Test
    @DisplayName("契约：固定输入 + 固定 PASS 响应 → 语义项 PASS，证据保持真实定位")
    void testAdjudicate_FixedPassResponse_SemanticItemsPass() {
        List<String> capturedPrompts = new ArrayList<>();
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> {
            capturedPrompts.add(prompt);
            return LlmVerdict.success(new LlmVerdict("PASS", "", "语义比对通过，条款响应完整", ""));
        });

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(),
                List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "7",
                        "资质证书: ISO9001质量管理体系认证（等级: 甲级）", true)),
                Set.of(), (progress, step) -> { });

        assertThat(outcomes.keySet()).containsExactlyInAnyOrderElementsOf(SEMANTIC_CODES);
        QualityCheckItemResult scoring = outcomes.get(BidQualityCheckItem.SCORING_RESPONSE.getCode());
        assertThat(scoring.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(scoring.conclusion()).contains("语义比对通过");
        assertThat(scoring.evidences()).allSatisfy(ev ->
                assertThat(ev.sourceType()).isIn("BID", "TENDER", "KNOWLEDGE_BASE"));

        // 验证传递给 ChatPort 的 Prompt 全部包含 --- [第 X 页] ---
        assertThat(capturedPrompts).hasSize(SEMANTIC_CODES.size());
        assertThat(capturedPrompts).allSatisfy(p -> assertThat(p).contains("--- [第 1 页] ---"));
    }

    @Test
    @DisplayName("契约：固定 FAIL 响应 → FAIL 且必附 suggestion 与证据")
    void testAdjudicate_FixedFailResponse_SemanticItemFails() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(
                prompt -> LlmVerdict.success(new LlmVerdict("FAIL", "", "未响应付款方式实质性条款", "请补充30天付款承诺")));

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(), (progress, step) -> { });

        QualityCheckItemResult resp = outcomes.get(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode());
        assertThat(resp.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(resp.suggestion()).isNotBlank();
    }

    @Test
    @DisplayName("单项 LLM 失败：通道返回 Failure → 该项 MANUAL + [LOW_CONFIDENCE]，结论为用户可读文案，不阻断其他项")
    void testAdjudicate_PortFails_ItemDegradesToManualLowConfidence() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> LlmVerdict.failure("LLM 调用超时"));

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(), (progress, step) -> { });

        assertThat(outcomes).hasSize(SEMANTIC_CODES.size());
        assertThat(outcomes.values()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.conclusion()).startsWith("[LOW_CONFIDENCE]");
            assertThat(result.conclusion()).contains("AI 本次未能自动裁决该条款");
            assertThat(result.conclusion()).doesNotContain("LLM 调用超时");
        });
    }

    @Test
    @DisplayName("响应不可解析（Invalid）→ MANUAL + [LOW_CONFIDENCE] 且结论为用户可读文案（不暴露技术细节）")
    void testAdjudicate_MalformedJson_DegradesToManualLowConfidence() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> LlmVerdict.invalid());

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(), (progress, step) -> { });

        assertThat(outcomes.values()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.conclusion()).startsWith("[LOW_CONFIDENCE]");
            assertThat(result.conclusion()).contains("AI 本次未能自动裁决该条款");
            assertThat(result.conclusion()).doesNotContain("响应结构非法");
        });
    }

    @Test
    @DisplayName("AI 未启用（Noop）：语义项全部 MANUAL 并注明原因")
    void testAdjudicate_AiDisabled_AllSemanticItemsManualWithReason() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> LlmVerdict.noop());

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(), (progress, step) -> { });

        assertThat(outcomes.keySet()).containsExactlyInAnyOrderElementsOf(SEMANTIC_CODES);
        assertThat(outcomes.values()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.conclusion()).contains("AI");
        });
    }

    @Test
    @DisplayName("进度上报：每个语义项裁决后回调 progress 单调递增至 100")
    void testAdjudicate_ProgressReporterInvokedMonotonically() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(
                prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "通过", "")));
        List<Integer> progressTrail = new ArrayList<>();

        adjudicator.adjudicate(bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(),
                (progress, step) -> progressTrail.add(progress));

        assertThat(progressTrail).hasSize(SEMANTIC_CODES.size());
        assertThat(progressTrail.get(progressTrail.size() - 1)).isEqualTo(100);
        for (int i = 1; i < progressTrail.size(); i++) {
            assertThat(progressTrail.get(i)).isGreaterThanOrEqualTo(progressTrail.get(i - 1));
        }
    }

    // =========================================================================
    // 3. Parser 容错与解析契约测试
    // =========================================================================

    @Test
    @DisplayName("parser 契约：合法 JSON 响应 → verdict（status 归一大写，MANUAL 必附 conclusion）")
    void testParse_ValidJson_ReturnsValidatedVerdict() {
        assertThat(LlmVerdictParser.parse(
                "{\"status\":\"manual\",\"reasonCode\":\"AMBIGUOUS\",\"conclusion\":\"表述含糊\",\"suggestion\":\"请澄清\"}"))
                .hasValueSatisfying(v -> {
                    assertThat(v.status()).isEqualTo("MANUAL");
                    assertThat(v.reasonCode()).isEqualTo("AMBIGUOUS");
                    assertThat(v.conclusion()).isEqualTo("表述含糊");
                });
    }

    @Test
    @DisplayName("parser 容错：代码围栏包裹或前后多余文字不影响解析")
    void testParse_FencedOrWrappedJson_StillParsed() {
        assertThat(LlmVerdictParser.parse(
                "```json\n{\"status\":\"PASS\",\"conclusion\":\"通过\"}\n```"))
                .hasValueSatisfying(v -> assertThat(v.status()).isEqualTo("PASS"));
        assertThat(LlmVerdictParser.parse(
                "裁决结果如下：{\"status\":\"FAIL\",\"conclusion\":\"未响应\"}，请知悉。"))
                .hasValueSatisfying(v -> assertThat(v.status()).isEqualTo("FAIL"));
    }

    @Test
    @DisplayName("parser 容错：中文状态同义归一到三值集合")
    void testParse_ChineseStatus_NormalizedToCanonical() {
        assertThat(LlmVerdictParser.parse("{\"status\":\"通过\",\"conclusion\":\"齐备\"}"))
                .hasValueSatisfying(v -> assertThat(v.status()).isEqualTo("PASS"));
        assertThat(LlmVerdictParser.parse("{\"status\":\"不通过\",\"conclusion\":\"缺失\"}"))
                .hasValueSatisfying(v -> assertThat(v.status()).isEqualTo("FAIL"));
        assertThat(LlmVerdictParser.parse("{\"status\":\"待确认\",\"conclusion\":\"含糊\"}"))
                .hasValueSatisfying(v -> assertThat(v.status()).isEqualTo("MANUAL"));
    }

    @Test
    @DisplayName("parser 容错：MANUAL 缺 conclusion 时兜底默认文案，不再整体拒绝")
    void testParse_ManualWithoutConclusion_FallsBackToDefault() {
        assertThat(LlmVerdictParser.parse("{\"status\":\"MANUAL\"}"))
                .hasValueSatisfying(v -> {
                    assertThat(v.status()).isEqualTo("MANUAL");
                    assertThat(v.conclusion()).isNotBlank();
                });
    }

    @Test
    @DisplayName("parser 契约：无 JSON 对象 / 缺 status / 未知状态 一律 empty")
    void testParse_InvalidResponses_ReturnEmpty() {
        assertThat(LlmVerdictParser.parse("这不是 JSON")).isEmpty();
        assertThat(LlmVerdictParser.parse("{\"conclusion\":\"无状态\"}")).isEmpty();
        assertThat(LlmVerdictParser.parse("{\"status\":\"UNKNOWN\",\"conclusion\":\"x\"}")).isEmpty();
        assertThat(LlmVerdictParser.parse(null)).isEmpty();
    }
}
