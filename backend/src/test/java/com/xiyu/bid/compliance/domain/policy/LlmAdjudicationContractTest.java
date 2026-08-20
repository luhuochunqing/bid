// Input: 固定输入（TenderRequirements + KnowledgeMatch + 投标/招标分页文档）+ 固定 LLM 通道结果（ChatOutcome 四值）
// Output: 断言语义项裁决结构（PASS/FAIL/MANUAL + reasonCode 前缀 + evidence）、单项失败 LOW_CONFIDENCE、Noop 全 MANUAL、parser 解析契约
// Pos: compliance/domain/policy/ - LLM 混合裁决契约测试（spec 049 US5 / T045，research.md R6，PRD §3.5）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.service.LlmVerdictParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("契约：固定输入 + 固定 PASS 响应 → 语义项 PASS，证据保持真实定位")
    void testAdjudicate_FixedPassResponse_SemanticItemsPass() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(
                prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "语义比对通过，条款响应完整", "")));

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
    @DisplayName("单项 LLM 失败：通道返回 Failure → 该项 MANUAL + [LOW_CONFIDENCE]，不阻断其他项")
    void testAdjudicate_PortFails_ItemDegradesToManualLowConfidence() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> LlmVerdict.failure("LLM 调用超时"));

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(), (progress, step) -> { });

        assertThat(outcomes).hasSize(SEMANTIC_CODES.size());
        assertThat(outcomes.values()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.conclusion()).startsWith("[LOW_CONFIDENCE]");
        });
    }

    @Test
    @DisplayName("响应不可解析（Invalid）→ MANUAL + [LOW_CONFIDENCE]（reasonCode ∈ PRD §3.5 五值集合）")
    void testAdjudicate_MalformedJson_DegradesToManualLowConfidence() {
        LlmAdjudicator adjudicator = new LlmAdjudicator(prompt -> LlmVerdict.invalid());

        Map<String, QualityCheckItemResult> outcomes = adjudicator.adjudicate(
                bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(), (progress, step) -> { });

        assertThat(outcomes.values()).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.conclusion()).startsWith("[LOW_CONFIDENCE]");
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
        List<Integer> progressTrail = new java.util.ArrayList<>();

        adjudicator.adjudicate(bidDoc(), tenderDoc(), requirements(), List.of(), Set.of(),
                (progress, step) -> progressTrail.add(progress));

        assertThat(progressTrail).hasSize(SEMANTIC_CODES.size());
        assertThat(progressTrail.get(progressTrail.size() - 1)).isEqualTo(100);
        for (int i = 1; i < progressTrail.size(); i++) {
            assertThat(progressTrail.get(i)).isGreaterThanOrEqualTo(progressTrail.get(i - 1));
        }
    }

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
    @DisplayName("parser 契约：非法 JSON / 缺 status / MANUAL 缺 conclusion 一律 empty")
    void testParse_InvalidResponses_ReturnEmpty() {
        assertThat(LlmVerdictParser.parse("这不是 JSON")).isEmpty();
        assertThat(LlmVerdictParser.parse("{\"conclusion\":\"无状态\"}")).isEmpty();
        assertThat(LlmVerdictParser.parse("{\"status\":\"MANUAL\",\"conclusion\":\"\"}")).isEmpty();
        assertThat(LlmVerdictParser.parse(null)).isEmpty();
    }
}
