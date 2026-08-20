// Input: ScoreCandidateMapper.fromLlmCandidate（LLM 输出 + 来源原文）
// Output: 原文对齐回填行为验证（detail/dim/sourceText 保真）
// Pos: Test/scoreparse/infrastructure/openai

package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCandidateMapperTest {

    private ScoreCandidateOutput.Candidate llmCandidate(
            String dim, String detail, String sourceText) {
        ScoreCandidateOutput.Candidate candidate = new ScoreCandidateOutput.Candidate();
        candidate.code = "A1";
        candidate.dim = dim;
        candidate.detail = detail;
        candidate.weight = 10.0;
        candidate.sourceText = sourceText;
        return candidate;
    }

    @Test
    void fromLlmCandidate_detailAligned_backfillsOriginalFragment() {
        String chunk = "A1 技术方案　投标人须提供完整的实施方案，覆盖采购、实施、验收全流程。 10分";
        ScoreCandidate mapped = ScoreCandidateMapper.fromLlmCandidate(
                llmCandidate("技术方案",
                        "投标人须提供完整的实施方案，覆盖采购、实施、验收全流程。",
                        "投标人须提供完整的实施方案 覆盖采购、实施、验收全流程。"),
                chunk);
        // detail 回填为原文（含全角空格），sourceText 与 detail 同源（原文背书包含关系）
        assertThat(mapped.detail())
                .isEqualTo("投标人须提供完整的实施方案，覆盖采购、实施、验收全流程。");
        assertThat(mapped.sourceText()).isEqualTo(mapped.detail());
        assertThat(mapped.dim()).isEqualTo("技术方案");
        assertThat(mapped.weight()).isEqualTo(BigDecimal.valueOf(10.0));
    }

    @Test
    void fromLlmCandidate_llmParaphrased_keepsLlmOutputWithoutBacking() {
        String chunk = "A1 技术方案 投标人须提供近三年类似项目业绩合同复印件，每个得2分。";
        String paraphrased = "提供类似项目业绩，每个2分";
        ScoreCandidate mapped = ScoreCandidateMapper.fromLlmCandidate(
                llmCandidate("技术方案", paraphrased, paraphrased), chunk);
        // 概括改写无法对齐 → 保留 LLM 输出；detail 非 sourceText 子串关系不再被强化
        assertThat(mapped.detail()).isEqualTo(paraphrased);
        assertThat(mapped.sourceText()).isEqualTo(paraphrased);
    }

    @Test
    void fromLlmCandidate_nullSourceText_degradesToLlmOnly() {
        ScoreCandidate mapped = ScoreCandidateMapper.fromLlmCandidate(
                llmCandidate("商务部分", "商务响应偏离情况", "商务响应偏离情况"), null);
        assertThat(mapped.detail()).isEqualTo("商务响应偏离情况");
        assertThat(mapped.sourceText()).isEqualTo("商务响应偏离情况");
    }
}
