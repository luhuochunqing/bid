package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 复现测试：评分汇总误判修复。
 * 验证包含单项分值说明与整表满分的合计行不被当作多总分冲突，并允许确定性回补成功。
 */
class ScoreSummaryMisjudgementReproductionTest {

    private static final String TENDER_SECTION = "本项目评分办法 / 技术评审细则";
    private static final String TENDER_TEXT = "# 本项目评分办法\n总分100分。\n";

    @Test
    void controlGroup_cleanHundredSummary_autoApprovesAndCompletesFallback() {
        DocumentArtifact artifact = buildArtifact(List.of("合计", "满分100分", "100", ""));

        // A. 确定性抽取与校验
        ScoreFactShadowPipeline.ShadowResult shadow = ScoreFactShadowPipeline.run(artifact, TENDER_TEXT);
        assertThat(shadow.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(shadow.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(shadow.completeness().leafWeightSum()).isEqualByComparingTo("100");
        assertThat(shadow.completeness().leafCount()).isEqualTo(10);
        assertThat(shadow.completeness().defects()).isEmpty();

        // B. 确定性回补
        var verification = new OriginalScoreEvidencePolicy.Verification("doc-1", "scheme-1", null, List.of());
        var modelValidation = new MultimodalScoreFactValidator.ValidationReport(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, List.of(), verification);
        var originalContext = OriginalScoringContext.resolve(artifact, TENDER_TEXT);
        var fallbackResult = MultimodalReviewFallback.completeDeterministic(
                originalContext, artifact, TENDER_TEXT, modelValidation);

        assertThat(fallbackResult).isNotNull();
        assertThat(fallbackResult.candidates()).hasSize(10);
        assertThat(fallbackResult.candidates()).allSatisfy(candidate ->
                assertThat(candidate.weight()).isEqualByComparingTo("10"));
        assertThat(fallbackResult.originalVerified()).isTrue();
    }

    @Test
    void problemGroup_mixedPerItemAndTableTotalSummary_autoApprovesAndCompletesFallback() {
        DocumentArtifact artifact = buildArtifact(List.of("合计", "每项 满分10分，满分100分", "100", ""));

        // A. 确定性抽取与校验
        ScoreFactShadowPipeline.ShadowResult shadow = ScoreFactShadowPipeline.run(artifact, TENDER_TEXT);
        assertThat(shadow.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(shadow.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(shadow.completeness().leafWeightSum()).isEqualByComparingTo("100");
        assertThat(shadow.completeness().leafCount()).isEqualTo(10);
        assertThat(shadow.completeness().defects()).isEmpty();

        // B. 确定性回补
        var verification = new OriginalScoreEvidencePolicy.Verification("doc-1", "scheme-1", null, List.of());
        var modelValidation = new MultimodalScoreFactValidator.ValidationReport(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, List.of(), verification);
        var originalContext = OriginalScoringContext.resolve(artifact, TENDER_TEXT);
        var fallbackResult = MultimodalReviewFallback.completeDeterministic(
                originalContext, artifact, TENDER_TEXT, modelValidation);

        assertThat(fallbackResult).isNotNull();
        assertThat(fallbackResult.candidates()).hasSize(10);
        assertThat(fallbackResult.candidates()).allSatisfy(candidate ->
                assertThat(candidate.weight()).isEqualByComparingTo("10"));
        assertThat(fallbackResult.originalVerified()).isTrue();
    }

    private static DocumentArtifact buildArtifact(List<String> summaryRow) {
        String tableId = "t-tech-01";
        List<List<String>> rows = new ArrayList<>();
        // 表头：序号 / 评审项目 / 分值 / 评审内容
        rows.add(List.of("序号", "评审项目", "分值", "评审内容"));
        // 10 个独立评分项
        for (int i = 1; i <= 10; i++) {
            rows.add(List.of(
                    String.valueOf(i),
                    "技术方案" + i,
                    "10",
                    "技术方案" + i + "符合要求得10分，不符合要求得0分。"
            ));
        }
        // 合计行
        rows.add(summaryRow);

        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<String> rowValues = rows.get(r);
            for (int c = 0; c < rowValues.size(); c++) {
                String val = rowValues.get(c);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        tableId + "-r" + r + "-c" + c,
                        r, c, val, val, 1, 1, false,
                        "pdf:page=1;table=" + tableId + ";r=" + r + ";c=" + c,
                        1, tableId + "-r" + r));
            }
        }

        DocumentArtifact.CanonicalTable table = new DocumentArtifact.CanonicalTable(
                tableId, 1, TENDER_SECTION, rows.size(), 4, List.of(), cells, true);

        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("test.pdf", "sha256:test", "pdf", "2.0"),
                List.of(table),
                List.of()
        );
    }
}
