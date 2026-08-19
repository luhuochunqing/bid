// Input: 评分项 + 投标文件文本 + 图片清单与预算（mock analyzer）
// Output: US1 配给/降级分支断言（空图纯文本 / 预算内携带 / 超预算截断 / 损坏跳过 / 多模态异常纯文本重试 / 主观项不带图）
// Pos: Test/scoreparse/application — spec 048 T018（降级矩阵见 contracts/transport-multimodal.md）
package com.xiyu.bid.scoreparse.application;

import com.openai.errors.BadRequestException;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec 048 US1：客观项打分图片配给与降级（T018）。
 * <p>US1 最简配给：预算内全量携带（index 顺序确定性截断）；
 * 降级矩阵：无图/损坏项 → 纯文本；多模态不被支持（BadRequestException / RESPONSES 风格 ISE）
 * → 纯文本重试一次 + log.warn + Sentry，不阻断任务。
 */
@ExtendWith(MockitoExtension.class)
class ScoreItemAssessorTest {

    private static final String BID_TEXT = "第一章 资质证明 …… 具备 CMMI 5 级认证证书扫描件。";

    @Mock
    private OpenAiScoreAnalyzer scoreAnalyzer;

    private ScoreParseTask task;

    @BeforeEach
    void setUp() {
        task = ScoreParseTask.builder()
                .id(5L).taskId("task-uuid").projectId(1L)
                .taskType("SCORING").status("PROCESSING").build();
    }

    @Test
    void assess_noImages_callsTextOnlyAnalyzerPath() {
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 1000));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(assessment(8.0, 80, "文本依据", null, false, null, null));

        assessor.assess(task, objectiveItem(), BID_TEXT, List.of());

        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString());
        verify(scoreAnalyzer, never())
                .assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList());
    }

    @Test
    void assess_imagesWithinBudget_carriesAllToMultimodalOverload() {
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 1000));
        DocumentImage first = image(0, "aaaa");
        DocumentImage second = image(1, "bbbb");
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList()))
                .thenReturn(assessment(8.0, 80, "证书扫描件图示满足", null, false, null, null));

        ScoreResult result = assessor.assess(task, objectiveItem(), BID_TEXT, List.of(first, second));

        ArgumentCaptor<List<DocumentImage>> captor = imagesCaptor();
        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString(), captor.capture());
        assertThat(captor.getValue()).containsExactly(first, second);
        assertThat(result.getEvidence()).isEqualTo("证书扫描件图示满足");
    }

    @Test
    void assess_exceedsImageCountBudget_truncatesInIndexOrder() {
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(2, 1000));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList()))
                .thenReturn(assessment(8.0, 80, "依据", null, false, null, null));

        assessor.assess(task, objectiveItem(), BID_TEXT,
                List.of(image(0, "aaaa"), image(1, "bbbb"), image(2, "cccc")));

        ArgumentCaptor<List<DocumentImage>> captor = imagesCaptor();
        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString(), captor.capture());
        assertThat(captor.getValue()).containsExactly(image(0, "aaaa"), image(1, "bbbb"));
    }

    @Test
    void assess_exceedsByteBudget_stopsAtOverflow() {
        // 预算 8 字节：前两张各 4 字节可携带，第三张溢出即截断（确定性）
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 8));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList()))
                .thenReturn(assessment(8.0, 80, "依据", null, false, null, null));

        assessor.assess(task, objectiveItem(), BID_TEXT,
                List.of(image(0, "aaaa"), image(1, "bbbb"), image(2, "cccc")));

        ArgumentCaptor<List<DocumentImage>> captor = imagesCaptor();
        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString(), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void assess_corruptImagesSkipped_allCorruptDegradesToTextOnly() {
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 1000));
        DocumentImage blankBase64 = new DocumentImage(0, "image/png", "资质", 1, 640, 480, " ");
        DocumentImage nullMime = new DocumentImage(1, null, "资质", 2, 640, 480, "aGk=");
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(assessment(0.0, 0, null, null, true, "未找到证书图示", "补充"));

        assessor.assess(task, objectiveItem(), BID_TEXT, List.of(blankBase64, nullMime));

        // 全部损坏 → 空配给 → 纯文本路径
        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString());
        verify(scoreAnalyzer, never())
                .assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList());
    }

    @Test
    void assess_multimodalBadRequest_degradesToTextRetryOnce() {
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 1000));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList()))
                .thenThrow(mock(BadRequestException.class));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(assessment(8.0, 80, "纯文本依据", null, false, null, null));

        ScoreResult result = assessor.assess(task, objectiveItem(), BID_TEXT, List.of(image(0, "aaaa")));

        // 降级不阻断：纯文本重试恰好一次，结果正常产出
        assertThat(result.getEvidence()).isEqualTo("纯文本依据");
        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void assess_multimodalUnsupportedApiStyle_degradesToTextRetryOnce() {
        // apiStyle=RESPONSES 时 service 抛 ISE（T007 契约），调用层降级纯文本
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 1000));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList()))
                .thenThrow(new IllegalStateException("multimodal image content requires CHAT_COMPLETIONS"));
        when(scoreAnalyzer.assessObjective(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(assessment(8.0, 80, "降级依据", null, false, null, null));

        assertThatCode(() -> assessor.assess(task, objectiveItem(), BID_TEXT, List.of(image(0, "aaaa"))))
                .doesNotThrowAnyException();

        verify(scoreAnalyzer).assessObjective(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void assess_subjectiveItem_neverCarriesImages() {
        ScoreItemAssessor assessor = new ScoreItemAssessor(scoreAnalyzer, budget(3, 1000));
        when(scoreAnalyzer.assessSubjective(anyString(), anyString()))
                .thenReturn(assessment(null, null, null, null, false, null, "建议补充"));

        assessor.assess(task, subjectiveItem(), BID_TEXT, List.of(image(0, "aaaa")));

        verify(scoreAnalyzer).assessSubjective(anyString(), anyString());
        verify(scoreAnalyzer, never()).assessObjective(anyString(), any(BigDecimal.class), anyString());
        verify(scoreAnalyzer, never())
                .assessObjective(anyString(), any(BigDecimal.class), anyString(), anyList());
    }

    private static ImageBudget budget(int maxImages, long maxBytes) {
        return new ImageBudget(maxImages, maxBytes);
    }

    private static DocumentImage image(int index, String base64) {
        return new DocumentImage(index, "image/png", "资质", index + 1, 640, 480, base64);
    }

    private ScoreItem objectiveItem() {
        return item("OBJECTIVE");
    }

    private ScoreItem subjectiveItem() {
        return item("SUBJECTIVE");
    }

    private ScoreItem item(String scoreType) {
        return ScoreItem.builder()
                .id(10L).projectId(1L).parseTaskId(1L)
                .itemIndex(1).code("A1").dim("资质").detail("具备 CMMI 5 级认证证书")
                .weight(new BigDecimal("10")).scoreType(scoreType)
                .statusStage1("PENDING").build();
    }

    private static ScoreAssessmentOutput assessment(Double actualScore, Integer matchRatio, String evidence,
                                                    String quote, Boolean quoteMissing,
                                                    String missedReason, String suggestion) {
        ScoreAssessmentOutput output = new ScoreAssessmentOutput();
        output.actualScore = actualScore;
        output.matchRatio = matchRatio;
        output.evidence = evidence;
        output.quote = quote;
        output.quoteMissing = quoteMissing;
        output.missedReason = missedReason;
        output.suggestion = suggestion;
        return output;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<DocumentImage>> imagesCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }
}
