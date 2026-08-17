// Input: OpenAiScoreAnalyzer 的四路召回入口 + mock LLM/chunker 依赖
// Output: 召回一/三传 null section 时 toCandidate 不抛 NPE（回归：项目 226 线上崩溃）
// Pos: Test/scoreparse/infrastructure/openai

package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentConfigurationResolver;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiStructuredOutputService;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.when;

/**
 * 回归测试：召回一/召回三调用 toCandidate(criterion, null)，section 为 null 时
 * 直接访问 section.sectionTitle() 抛 NPE，导致整个解析任务异常终止
 * （2026-08-17 项目 226 线上事故，召回三有候选即必炸）。
 */
@ExtendWith(MockitoExtension.class)
class OpenAiScoreAnalyzerTest {

    @Mock
    private OpenAiStructuredOutputService structuredOutputService;

    @Mock
    private OpenAiBidAgentConfigurationResolver configurationResolver;

    @Mock
    private StructuralDocumentChunker structuralChunker;

    /** 单行文本：行首编号 + 维度 + 条件得分语义（同时命中召回一提取与召回三语义段落过滤）。 */
    private static final String SCORE_LINE = "1 技术方案完整性 提供完整的实施方案得10分";

    @Test
    void recallCandidates_nullSectionDoesNotThrowNpe() {
        OpenAiScoreAnalyzer analyzer = new OpenAiScoreAnalyzer(
                structuredOutputService, configurationResolver, structuralChunker);
        // chunker 返回空 → 召回四走"切片为空跳过"分支，不触发 LLM 调用（metadata 为 null，用 any() 匹配）
        when(structuralChunker.chunk(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        List<ScoreCandidate> candidates =
                analyzer.recallCandidates(SCORE_LINE, null, null);

        // 召回一/三应从该行产出候选（不为空），且 section 相关字段（contextNote/sourceText/location）为空串而非 NPE
        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).contextNote()).isNotNull();
        assertThat(candidates.get(0).sourceText()).isNotNull();
        assertThat(candidates.get(0).location()).isNotNull();
    }
}
