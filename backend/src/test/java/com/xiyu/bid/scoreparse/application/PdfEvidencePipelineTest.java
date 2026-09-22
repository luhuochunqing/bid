// Input: PDF tender source + DocumentArtifact v2
// Output: Evidence Pipeline routing regression for PDF score tables
// Pos: scoreparse/application — project 1381 root-cause guard
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfEvidencePipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void pdfTender_usesDocumentArtifactEvidencePipelineInsteadOfSkippingToLegacyMarkdown()
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 score table".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "project-1381-like.pdf",
                "memory://project-1381-like.pdf",
                "评审标准表：商务能力 10分，技术方案 20分，报价得分 70分。");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, null, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.InMemory(pdfBytes, fileName, contentType);
            }
        };
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        assertThat(fileName).endsWith(".pdf");
                        assertThat(content).isEqualTo(pdfBytes);
                        return pdfScoreArtifact();
                    }
                };

        StructuredTableResult result = StructuredTableExtractionHandler.extract(
                source, resolver, artifactExtractor, null);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.structureIncomplete()).isFalse();
        assertThat(result.facts()).hasSize(3);
        assertThat(result.candidates())
                .extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("70"));
    }

    @Test
    void project1381LikePdfSplitScoreTable_usesStructuredTotalWhenMarkdownBreakTagsHide100()
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 project 1381 split score table".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "project-1381-like.pdf",
                "memory://project-1381-like.pdf",
                """
                投标报价得分：等于基准价的得满分30分，办公用品得满分10分。
                | 合计<br>得分 | 满分<br>（100分） | |
                """);
        assertThat(OriginalScoringContext.resolve(project1381ScoreArtifact(), source.text()).total().value())
                .isEqualByComparingTo("100");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, null, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.InMemory(pdfBytes, fileName, contentType);
            }
        };
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        return project1381ScoreArtifact();
                    }
                };

        StructuredTableResult result = StructuredTableExtractionHandler.extract(
                source, resolver, artifactExtractor, null);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100.00");
        assertThat(result.completeness().declaredTotalSource())
                .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100.00");
        assertThat(result.candidates())
                .extracting(candidate -> candidate.dim())
                .containsExactly(
                        "投标报价",
                        "公司综合实力",
                        "类似业绩",
                        "技术方案",
                        "供货方案",
                        "人员配备",
                        "质量管理方案",
                        "售后服务方案");
        assertThat(result.candidates())
                .extracting(candidate -> candidate.weight())
                .containsExactly(
                        new BigDecimal("40"),
                        new BigDecimal("6"),
                        new BigDecimal("10"),
                        new BigDecimal("10"),
                        new BigDecimal("10"),
                        new BigDecimal("6"),
                        new BigDecimal("10"),
                        new BigDecimal("8"));
    }

    @Test
    void project1381LikePdfWithoutStructuredSummary_keepsLossyMarkdownFallbackBehavior()
            throws IOException, InterruptedException {
        StructuredTableResult result = extractProject1381(
                project1381ScoreArtifact(false, null));

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("30.00");
        assertThat(result.completeness().declaredTotalSource())
                .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.TENDER_TEXT);
    }

    @Test
    void conflictingStructuredSummaryTotals_requireReviewInsteadOfGuessing()
            throws IOException, InterruptedException {
        StructuredTableResult result = extractProject1381(
                project1381ScoreArtifact(true, "80"));

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().fallbackReason()).isEqualTo("DECLARED_TOTAL_CONFLICT");
        assertThat(result.completeness().defects())
                .anyMatch(defect -> defect.contains("结构化声明总分冲突")
                        && defect.contains("100") && defect.contains("80"));
    }

    @Test
    void pdfTenderWithoutExtractableScoreTables_fallsBackToLegacyRecallInsteadOfFailingTheTask()
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 text only".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "text-only.pdf", "memory://text-only.pdf", "评分标准以普通文本描述。");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, null, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.InMemory(pdfBytes, fileName, contentType);
            }
        };
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        return new DocumentArtifact(
                                new DocumentArtifact.ArtifactMeta("sha256:pdf", "pdf", "PDF", "pdf-table-v1"),
                                List.of(), List.of("no tables"));
                    }
                };

        StructuredTableResult result = StructuredTableExtractionHandler.extract(
                source, resolver, artifactExtractor, null);

        assertThat(result.evidencePipeline()).isFalse();
        assertThat(result.structureIncomplete()).isFalse();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void multiTableTender_withoutSchemeTotal_localClosuresDoNotAuthorizePublication()
            throws IOException, InterruptedException {
        // 旧夹具只声明技术 60、商务 40 的分表小计，没有原文方案总分；局部通过不能冒充方案级证明。
        StructuredTableResult result = extractMultiTable();

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.facts()).hasSize(5);
        assertThat(result.candidates()).hasSize(5);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.originalVerified()).isFalse();
        assertThat(result.completeness().declaredTotalScore()).isNull();
        assertThat(result.completeness().declaredTotalSource())
                .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100.00");
        // 分表小计仍可分别核验，且两表事实都作为复核材料保留。
        assertThat(result.tableCompletenesses()).hasSize(2);
        assertThat(result.tableCompletenesses())
                .allMatch(table -> table.completeness() != null
                        && table.completeness().decision() == ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.tableCompletenesses())
                .extracting(table -> table.completeness().declaredTotalScore())
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("40.00"));
    }

    @Test
    void oversizedOnDiskSpill_stillUsesEvidencePipelineAndReleasesTempFile() throws Exception {
        TempBidFileStore store = new TempBidFileStore(tempDir);
        Path spill = store.reserve("tender-test-spill");
        byte[] spilled = "spilled-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(spill, spilled);
        store.recordSpillChunk("tender-test-spill", spilled.length);
        TenderTextSource source = new TenderTextSource(
                "oversized.pdf", "obs-direct:big", "评分标准文本。");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, store, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.OnDisk(spill, fileName, contentType);
            }
        };
        java.util.concurrent.atomic.AtomicBoolean onDiskPathUsed = new java.util.concurrent.atomic.AtomicBoolean(false);
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new com.fasterxml.jackson.databind.ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(Path file, String fileName) {
                        onDiskPathUsed.set(true);
                        return pdfScoreArtifact();
                    }
                };

        StructuredTableResult result = StructuredTableExtractionHandler.extract(
                source, resolver, artifactExtractor, null);

        assertThat(onDiskPathUsed.get()).isTrue();
        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.candidates()).isNotEmpty();
        assertThat(Files.exists(spill)).isFalse();
    }

    private static StructuredTableResult extractMultiTable()
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 multi-table tender".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "multi-table.pdf",
                "memory://multi-table.pdf",
                "评分标准由技术评分表和商务评分表两张独立评分表组成。");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, null, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.InMemory(pdfBytes, fileName, contentType);
            }
        };
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        return multiTableScoreArtifact();
                    }
                };
        return StructuredTableExtractionHandler.extract(source, resolver, artifactExtractor, null);
    }

    private static DocumentArtifact multiTableScoreArtifact() {
        List<List<String>> technicalRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "技术方案\n（30分）", "方案优秀得满分30分。"),
                List.of("2", "技术团队\n（20分）", "团队优秀得满分20分。"),
                List.of("3", "技术培训\n（10分）", "培训完整得满分10分。"),
                List.of("合计\n得分", "满分\n（60分）", ""));
        List<List<String>> commercialRows = List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "商务报价\n（20分）", "报价合理得满分20分。"),
                List.of("2", "售后服务\n（20分）", "服务完善得满分20分。"),
                List.of("合计\n得分", "满分\n（40分）", ""));
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:multi-table", "multi-table", "PDF", "pdf-table-v1"),
                List.of(buildScoreTable("m1-t0001", technicalRows),
                        buildScoreTable("m1-t0002", commercialRows)),
                List.of());
    }

    private static DocumentArtifact.CanonicalTable buildScoreTable(String tableId, List<List<String>> rows) {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < rows.get(row).size(); col++) {
                String text = rows.get(row).get(col);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        tableId + "-r" + row + "-c" + col, row, col, text,
                        text.replaceAll("\\s+", " ").trim(), 1, 1, false,
                        "pdf:page=1;table=" + tableId + ";row=" + row + ";col=" + col));
            }
        }
        return new DocumentArtifact.CanonicalTable(
                tableId, 1, "", rows.size(), rows.get(0).size(), List.of(), cells, true);
    }

    private static DocumentArtifact pdfScoreArtifact() {
        List<List<String>> rows = List.of(
                List.of("编号", "评分项", "评分标准", "分值"),
                List.of("1", "商务能力", "证书齐全得满分", "10"),
                List.of("2", "技术方案", "方案完整可行得满分", "20"),
                List.of("3", "报价评分", "报价合理得满分", "70")
        );
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < rows.get(row).size(); col++) {
                String text = rows.get(row).get(col);
                String cellId = "p1-t0000-r" + row + "-c" + col;
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        cellId, row, col, text, text, 1, 1, false,
                        "pdf:page=1;table=0;row=" + row + ";col=" + col));
            }
        }
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:pdf", "pdf", "PDF", "pdf-table-v1"),
                List.of(new DocumentArtifact.CanonicalTable(
                        "p1-t0000", 1, "", rows.size(), 4, List.of(), cells, true)),
                List.of());
    }

    private static StructuredTableResult extractProject1381(DocumentArtifact artifact)
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 project 1381 split score table"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "project-1381-like.pdf",
                "memory://project-1381-like.pdf",
                "投标报价得分：等于基准价的得满分30分，办公用品得满分10分。\n"
                        + "| 合计<br>得分 | 满分<br>（100分） | |");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, null, null, null, 5000000L) {
            @Override
            public LoadedBidFile loadTenderFile(String fileUrl, String fileName, String contentType) {
                return new LoadedBidFile.InMemory(pdfBytes, fileName, contentType);
            }
        };
        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        return artifact;
                    }
                };
        return StructuredTableExtractionHandler.extract(source, resolver, artifactExtractor, null);
    }

    private static DocumentArtifact project1381ScoreArtifact() {
        return project1381ScoreArtifact(true, null);
    }

    private static DocumentArtifact project1381ScoreArtifact(boolean includeSummary, String conflictingTotal) {
        List<List<String>> rows = new ArrayList<>(List.of(
                List.of("序号", "评分因素", "评分标准"),
                List.of("1", "投标报价\n（40分）", "报价规则中含局部满分30分和满分10分。"),
                List.of("2", "公司综合实力\n（6分）", "证书清单每项1分，最高6分。"),
                List.of("3", "类似业绩\n（10分）", "每有一个类似服务案例得2分，满分为10分。"),
                List.of("4", "技术方案\n（10分）", "方案优秀得7.1-10.0分。"),
                List.of("4", "供货方案\n（10分）", "供货保障措施完整得10分。"),
                List.of("5", "人员配备\n（6分）", "人员配置优秀得4.1-6.0分。"),
                List.of("6", "质量管理方案（10分）", "质量管理方案优秀得10分。"),
                List.of("7", "售后服务方案（8分）", "售后服务优秀得8分。")
        ));
        if (includeSummary) {
            rows.add(List.of("合计\n得分", "满分\n（100分）", ""));
        }
        if (conflictingTotal != null) {
            rows.add(List.of("总计\n得分", "满分\n（" + conflictingTotal + "分）", ""));
        }
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < rows.get(row).size(); col++) {
                String text = rows.get(row).get(col);
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        "p24-t0007-r" + row + "-c" + col,
                        row,
                        col,
                        text,
                        text.replaceAll("\\s+", " ").trim(),
                        1,
                        1,
                        false,
                        "pdf:page=" + (row <= 2 ? 24 : 25) + ";table=7;row=" + row + ";col=" + col));
            }
        }
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:project-1381", "project-1381", "PDF", "pdf-table-v1"),
                List.of(new DocumentArtifact.CanonicalTable(
                        "p24-t0007", 24, "", rows.size(), 3, List.of(), cells, true)),
                List.of());
    }
}
