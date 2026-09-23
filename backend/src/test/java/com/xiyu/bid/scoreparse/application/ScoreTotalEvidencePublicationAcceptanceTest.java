package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreTotalEvidencePublicationAcceptanceTest {
    @Test
    void missingSchemeTotalFromAssessmentFactorTableCannotPublishPartialModelResult() throws Exception {
        var source = assessmentFactorTable();
        var extractor = mock(SidecarDocumentArtifactExtractor.class);
        when(extractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            @Override
            public ExtractionResult extract(DocumentArtifact ignored, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return partial();
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact ignored) {
                return partial();
            }

            private ExtractionResult partial() {
                return new ExtractionResult(new BigDecimal("10"), List.of(fact("1", "销售额", "10",
                        "满分10分，销售额达到1亿元得10分。")), "model", 0);
            }
        };
        var result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf",
                        "# 本项目评分办法\n## 评分说明\n评分明细如下。"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                extractor, model, null);

        assertThat(result.completeness().declaredTotalScore()).isNull();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        var items = mock(ScoreItemPersistenceService.class);
        StructuredTableExtractionHandler.markEvidencePipelineCompleted(
                new com.xiyu.bid.scoreparse.entity.ScoreParseTask(), "task-a1", result,
                mock(com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository.class),
                mock(ScoreParseTaskStateService.class), mock(ScoreParseProgressService.class), items,
                mock(EstimatedScoreService.class));
        verify(items, never()).persistItems(any(), any(), any());
        verify(items).persistFactsAndEvidence(any(), any(), any());
    }

    @Test
    void explicitBodyTotalAndCompleteModelResultStillPublishes() throws Exception {
        var facts = List.of(fact("1", "销售额", "10", "满分10分，销售额达到1亿元得10分。"),
                fact("2", "技术方案", "90", "技术方案最高得90分。"));
        var result = SchemeScopePublicationTest.modelExtract(assessmentFactorTable(),
                "# 本项目评分办法\n## 评分说明\n本项目评分总分100分。", facts, "100");
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).hasSize(2);
    }

    private static DocumentArtifact assessmentFactorTable() {
        List<List<String>> rows = List.of(
                List.of("序号", "评审因素", "分值", "评审标准"),
                List.of("1", "销售额", "10", "满分10分，销售额达到1亿元得10分。"),
                List.of("2", "技术方案", "90", "技术方案最高得90分。"));
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).size(); column++) {
                String value = rows.get(row).get(column);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        "t-items-r" + row + "-c" + column, row, column, value, value,
                        1, 1, false, "pdf:page=1;table=t-items"));
            }
        }
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta(
                "bid.pdf", "sha-role-heading", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t-items", 1,
                        "本项目评分办法 / 评分明细", rows.size(), rows.get(0).size(), List.of(), cells, true)),
                List.of());
    }

    private static MultimodalScoreFact fact(String code, String name, String weight, String rule) {
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 1, "t-items", List.of("t-items-r" + code + "-c3"), rule, 0.9);
    }
}
