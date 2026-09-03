// Input: RunSummaryAssembler 9 参重载（pipelineVersion/decisionStatus/fallbackReason）
// Output: Phase 0 新链路口径键透出、旧键顺序保持、字段兼容（spec 059 FR-01）
// Pos: Test/scoreparse/application
// 维护声明: 维护者按项目SOP；新增键必须同步 plan.md 与 DTO 注释，禁止在调用方隐式新增

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunSummaryAssemblerEvidencePipelineTest {

    private static RecallOutcome recall(int hit, int total) {
        return new RecallOutcome(List.of(), 2,
                Map.of("lane1", 3, "lane4", total), hit, total, "gemini-3.5-flash");
    }

    private static RunSummaryAssembler.MergeView view() {
        return new RunSummaryAssembler.MergeView(false, false, 0, 0, 0, 0);
    }

    private static String assembleJson(String pipelineVersion, ScoreParseDecision decision,
            String fallbackReason) {
        WeightSumCheck.Result weight = new WeightSumCheck().checkCandidates(List.of());
        return RunSummaryAssembler.assemble(recall(2, 4), 7, view(), weight, 0,
                "gemini-3.5-flash", pipelineVersion, decision == null ? "" : decision.name(),
                fallbackReason).json();
    }

    @Test
    void pipelineVersion_legacyFallback_serializedAsIs() {
        String json = assembleJson(ScoreParsePipeline.LEGACY_FALLBACK,
                ScoreParseDecision.AUTO_APPROVED, null);
        assertThat(json)
                .contains("\"pipelineVersion\":\"LEGACY_FALLBACK\"")
                .contains("\"decisionStatus\":\"AUTO_APPROVED\"")
                .contains("\"fallbackReason\":\"\"");
    }

    @Test
    void pipelineVersion_evidenceV1_serializationPreservesField() {
        String json = assembleJson(ScoreParsePipeline.EVIDENCE_PIPELINE_V1,
                ScoreParseDecision.REVIEW_REQUIRED, "RESORT_TO_NORMALIZATION");
        assertThat(json)
                .contains("\"pipelineVersion\":\"EVIDENCE_PIPELINE_V1\"")
                .contains("\"decisionStatus\":\"REVIEW_REQUIRED\"")
                .contains("\"fallbackReason\":\"RESORT_TO_NORMALIZATION\"");
    }

    @Test
    void nullPipelineVersionAndDecision_emitsEmptyStrings_notNullLiterals() {
        // null 调用方不应产生 "null" 字面量，避免下游解析器破窗。
        String json = assembleJson(null, null, null);
        assertThat(json)
                .contains("\"pipelineVersion\":\"\"")
                .contains("\"decisionStatus\":\"\"")
                .contains("\"fallbackReason\":\"\"")
                .doesNotContain("\"pipelineVersion\":null")
                .doesNotContain("\"decisionStatus\":null")
                .doesNotContain("\"fallbackReason\":null");
    }

    @Test
    void legacyKeysRemainInOrder_pipelineKeysAppendedAtEnd() {
        // 旧字段必须保持原有顺序，否则可能破坏既有回放/审计断言。
        String json = assembleJson(ScoreParsePipeline.LEGACY_FALLBACK,
                ScoreParseDecision.AUTO_APPROVED, null);
        int llmIdx = json.indexOf("\"llmModel\"");
        int lanesIdx = json.indexOf("\"lanes\"");
        int itemsIdx = json.indexOf("\"items\"");
        int totalIdx = json.indexOf("\"finalTotalWeight\"");
        int pipeIdx = json.indexOf("\"pipelineVersion\"");
        int decisionIdx = json.indexOf("\"decisionStatus\"");
        int fallbackIdx = json.indexOf("\"fallbackReason\"");
        assertThat(llmIdx).isLessThan(lanesIdx);
        assertThat(itemsIdx).isLessThan(totalIdx);
        assertThat(totalIdx).isLessThan(pipeIdx);
        assertThat(pipeIdx).isLessThan(decisionIdx);
        assertThat(decisionIdx).isLessThan(fallbackIdx);
    }
}