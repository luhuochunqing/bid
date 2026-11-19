package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class BaiduOcrFailureDiagnosticsTest {
    @Test void allPagesFailingAuthenticationRetainBaselineAndDiagnostics() throws Exception {
        var baseline = baseline();
        var client = new BaiduTableOcrClient(enabled(), new ObjectMapper()) {
            @Override public BaiduTableResponse extractTablesFromPdf(byte[] bytes, Integer page) throws IOException {
                throw new IOException("Failed to fetch Baidu access token: HTTP 401");
            }
        };
        var actual = new BaiduTableArtifactEnricher(client).enrichIfEnabled(baseline, pdf(), "tender.pdf");
        assertThat(actual.artifact()).isEqualTo(baseline.artifact());
        assertThat(actual.textBlocks()).isEqualTo(baseline.textBlocks());
        assertThat(actual.documents()).isEqualTo(baseline.documents());
        assertThat(actual.warnings()).contains("existing-warning");
        assertThat(actual.warnings()).anyMatch(s -> s.startsWith("BAIDU_OCR_PAGE_FAILED:1:") && s.contains("401"));
    }

    @Test void providerBusinessErrorWithoutAnExceptionIsAlsoVisible() throws Exception {
        var client = new BaiduTableOcrClient(enabled(), new ObjectMapper()) {
            @Override public BaiduTableResponse extractTablesFromPdf(byte[] bytes, Integer page) {
                return new BaiduTableResponse(0, List.of(), 110, "Access token invalid", 1L);
            }
        };
        var actual = new BaiduTableArtifactEnricher(client).enrichIfEnabled(baseline(), pdf(), "tender.pdf");
        assertThat(actual.warnings()).contains("BAIDU_OCR_PAGE_FAILED:1:PROVIDER_ERROR_110");
    }

    @Test void noBaselineStillReturnsAnArtifactWithFailureEvidence() throws Exception {
        var client = new BaiduTableOcrClient(enabled(), new ObjectMapper()) {
            @Override public BaiduTableResponse extractTablesFromPdf(byte[] bytes, Integer page) throws IOException {
                throw new IOException("HTTP 401");
            }
        };
        var actual = new BaiduTableArtifactEnricher(client).enrichIfEnabled(null, pdf(), "tender.pdf");
        assertThat(actual).isNotNull();
        assertThat(actual.warnings()).anyMatch(s -> s.startsWith("BAIDU_OCR_PAGE_FAILED:"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void retainsPageFailureWhenBaselineContainsOnlyPartialScoreTableEvidence(int incompletePage) throws Exception {
        var partialTable = new DocumentArtifact.CanonicalTable(
                "t-p1", 1, "评分标准", 2, 2, List.of(),
                List.of(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                                "c1", 0, 0, "评分项", "评分项", 1, 1, false, "", 1),
                        new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                                "c2", 1, 0, "营业总收入", "营业总收入", 1, 1, false, "", 2)),
                true);
        var source = baseline();
        var baselineWithTable = new DocumentArtifact(source.artifact(), List.of(partialTable),
                source.warnings(), source.textBlocks(), source.schemaVersion());
        var attemptedPages = new ArrayList<Integer>();
        var client = new BaiduTableOcrClient(enabled(), new ObjectMapper()) {
            @Override public BaiduTableResponse extractTablesFromPdf(byte[] bytes, Integer page) throws IOException {
                attemptedPages.add(page);
                if (page == incompletePage) throw new IOException("HTTP 500");
                return new BaiduTableResponse(0, List.of(), null, null, 1L);
            }
        };
        var actual = new BaiduTableArtifactEnricher(client).enrichIfEnabled(baselineWithTable, pdf(2), "tender.pdf");
        assertThat(attemptedPages).containsExactly(1, 2);
        assertThat(actual.tables()).containsExactly(partialTable);
        assertThat(actual.warnings()).containsExactly(
                "existing-warning", "BAIDU_OCR_PAGE_FAILED:" + incompletePage + ":HTTP_500");
    }

    private static BaiduOcrProperties enabled() {
        var properties = new BaiduOcrProperties();
        properties.setGatewayEnabled(true);
        properties.setEnabled(true);
        return properties;
    }

    private static DocumentArtifact baseline() {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "sidecar"),
                List.of(), List.of("existing-warning"), List.of(
                        new DocumentArtifact.TextBlockEvidence("block", 1, "评分", "原文规则", 99d,
                                new DocumentArtifact.BoundingBox(0, 0, 10, 10, 100, 100))), "2.2");
    }

    private static byte[] pdf() throws IOException {
        return pdf(1);
    }

    private static byte[] pdf(int pageCount) throws IOException {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            for (int page = 0; page < pageCount; page++) document.addPage(new PDPage());
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
