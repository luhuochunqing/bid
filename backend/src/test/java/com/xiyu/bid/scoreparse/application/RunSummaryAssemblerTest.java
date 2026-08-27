// Input: RunSummaryAssembler 纯函数（recall 遥测 + 归并视图 + 权重终态）
// Output: JSON 字段完整性 / 对齐率与幻觉率阈值告警判定（spec 057 FR-04/FR-06）
// Pos: Test/scoreparse/application

package com.xiyu.bid.scoreparse.application;

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
}
