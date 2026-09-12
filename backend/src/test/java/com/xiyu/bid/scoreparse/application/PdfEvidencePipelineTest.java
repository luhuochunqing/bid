// Input: PDF tender source + DocumentArtifact v2
// Output: Evidence Pipeline routing regression for PDF score tables
// Pos: scoreparse/application — project 1381 root-cause guard
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfEvidencePipelineTest {

    @Test
    void pdfTender_usesDocumentArtifactEvidencePipelineInsteadOfSkippingToLegacyMarkdown()
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 score table".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "project-1381-like.pdf",
                "memory://project-1381-like.pdf",
                "评审标准表：商务能力 10分，技术方案 20分，报价得分 70分。");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, url -> pdfBytes) {
            @Override
            public byte[] loadBytes(String fileUrl) {
                return pdfBytes;
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

        StructuredTableExtractionHandler.Result result = StructuredTableExtractionHandler.extract(
                source, resolver, artifactExtractor, null);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.structureIncomplete()).isFalse();
        assertThat(result.facts()).hasSize(3);
        assertThat(result.candidates())
                .extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("70"));
    }

    @Test
    void pdfTenderWithoutExtractableScoreTables_fallsBackToLegacyRecallInsteadOfFailingTheTask()
            throws IOException, InterruptedException {
        byte[] pdfBytes = "%PDF-1.4 text only".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TenderTextSource source = new TenderTextSource(
                "text-only.pdf", "memory://text-only.pdf", "评分标准以普通文本描述。");
        InitiationTenderTextResolver resolver = new InitiationTenderTextResolver(
                null, null, null, null, null, null, url -> pdfBytes) {
            @Override
            public byte[] loadBytes(String fileUrl) {
                return pdfBytes;
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

        StructuredTableExtractionHandler.Result result = StructuredTableExtractionHandler.extract(
                source, resolver, artifactExtractor, null);

        assertThat(result.evidencePipeline()).isFalse();
        assertThat(result.structureIncomplete()).isFalse();
        assertThat(result.candidates()).isEmpty();
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
}
