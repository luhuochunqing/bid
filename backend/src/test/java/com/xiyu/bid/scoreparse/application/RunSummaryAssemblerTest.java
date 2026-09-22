// Input: RunSummaryAssembler 纯函数（recall 遥测 + 归并视图 + 权重终态）
// Output: JSON 字段完整性 / 对齐率与幻觉率阈值告警判定（spec 057 FR-04/FR-06）
// Pos: Test/scoreparse/application

package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.AiProviderDiagnostics;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunSummaryAssemblerTest {

    private static RecallOutcome recall(int hit, int total) {
        return new RecallOutcome(List.of(), 2,
                Map.of("lane1", 3, "lane4", total), hit, total, "gemini-3.5-flash");
    }

    private static RunSummaryAssembler.MergeView view(boolean invoked, int poolBacked,
            int hallucinated, int restored) {
        return new RunSummaryAssembler.MergeView(invoked, false, poolBacked, hallucinated, restored);
    }

    private static RunSummaryAssembler.Assembled assemble(RecallOutcome r,
            RunSummaryAssembler.MergeView m) {
        WeightSumCheck.Result weight = new WeightSumCheck().checkCandidates(r.candidates());
        return RunSummaryAssembler.assemble(r, 7, m, weight, r.candidates().size(), r.llmModel());
    }

    @Test
    void alignedAtFloorBoundary_noWarn_belowWarns() {
        // 命中 2/4 = 50% 恰在地板值：不告警；1/3 ≈33%：告警
        assertThat(assemble(recall(2, 4),
                view(false, 0, 0, 0)).warnLowAlignment()).isFalse();
        assertThat(assemble(recall(1, 3),
                view(false, 0, 0, 0)).warnLowAlignment()).isTrue();
    }

    @Test
    void hallucinationRatioOverCeil_warns_onlyWhenInvoked() {
        // dropped 1 of 分母(kept1+restored1+dropped1)=3 ≈33% > 30% → 告警；未裁决时恒不告警
        assertThat(assemble(recall(0, 0),
                view(true, 1, 1, 1)).warnHighHallucination()).isTrue();
        assertThat(assemble(recall(0, 0),
                view(false, 1, 99, 1)).warnHighHallucination()).isFalse();
    }

    @Test
    void jsonCarriesLanesTableSkippedModel_andEscapesQuotes() {
        RecallOutcome tricky = new RecallOutcome(List.of(), 1,
                Map.of("lane1", 2, "lane2", 0, "lane3", 5, "lane4", 9),
                8, 9, "bad\"model\\name");
        String json = assemble(tricky, view(false, 0, 0, 0)).json();
        assertThat(json)
                .contains("\"lane1\":2", "\"tableRows\":7", "\"skippedChunks\":1",
                        "\"aligned\":{\"hit\":8,\"total\":9}",
                        "\"finalTotalWeight\":0")
                .contains("bad\\\"model\\\\name");
    }

    @Test
    void jsonCarriesParentOnlyDimCount() {
        // 0 项纯父级维度
        String json0 = assemble(recall(2, 4), new RunSummaryAssembler.MergeView(false, false, 0, 0, 0, 0)).json();
        assertThat(json0).contains("\"parentOnlyDimCount\":0");

        // 1 项纯父级维度
        String json1 = assemble(recall(2, 4), new RunSummaryAssembler.MergeView(false, false, 0, 0, 0, 1)).json();
        assertThat(json1).contains("\"parentOnlyDimCount\":1");

        // 多项纯父级维度
        String json3 = assemble(recall(2, 4), new RunSummaryAssembler.MergeView(true, false, 5, 1, 2, 3)).json();
        assertThat(json3).contains("\"parentOnlyDimCount\":3");
    }

    @Test
    void jsonCarriesAiProviderDiagnostics_andPreservesOrder() {
        AiProviderDiagnostics diag = AiProviderDiagnostics.success(
                "deepseek", "deepseek-chat", 1500L,
                List.of(new AiProviderDiagnostics.AiCallDetail("TABLE_EXTRACTION", "deepseek", "deepseek-chat", "SUCCESS", 1500L, null, null))
        );
        WeightSumCheck.Result weight = new WeightSumCheck().checkCandidates(List.of());
        String json = RunSummaryAssembler.assemble(recall(2, 4), 7, view(false, 0, 0, 0),
                weight, 0, "", "EVIDENCE_PIPELINE_V1", "AUTO_APPROVED", "",
                List.of(Map.of("tableId", "t1")), List.of("diag1"), diag).json();

        assertThat(json)
                .contains("\"llmModel\":\"deepseek-chat\"")
                .contains("\"aiProviderDiagnostics\":{")
                .contains("\"provider\":\"deepseek\"")
                .contains("\"model\":\"deepseek-chat\"")
                .contains("\"latencyMs\":1500");

        int sourceDiagIdx = json.indexOf("\"sourceDiagnostics\"");
        int aiDiagIdx = json.indexOf("\"aiProviderDiagnostics\"");
        assertThat(sourceDiagIdx).isGreaterThan(0);
        assertThat(aiDiagIdx).isGreaterThan(sourceDiagIdx);
    }

    @Test
    void jsonOmitsAiProviderDiagnosticsWhenEmptyOrNull() {
        WeightSumCheck.Result weight = new WeightSumCheck().checkCandidates(List.of());
        String jsonNull = RunSummaryAssembler.assemble(recall(2, 4), 7, view(false, 0, 0, 0),
                weight, 0, "gpt-4", "EVIDENCE_PIPELINE_V1", "AUTO_APPROVED", "",
                List.of(), List.of(), null).json();
        assertThat(jsonNull).doesNotContain("\"aiProviderDiagnostics\"");

        String jsonEmpty = RunSummaryAssembler.assemble(recall(2, 4), 7, view(false, 0, 0, 0),
                weight, 0, "gpt-4", "EVIDENCE_PIPELINE_V1", "AUTO_APPROVED", "",
                List.of(), List.of(), AiProviderDiagnostics.empty()).json();
        assertThat(jsonEmpty).doesNotContain("\"aiProviderDiagnostics\"");
    }
}
