package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
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

    private static BaiduOcrProperties enabled() {
        var properties = new BaiduOcrProperties();
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
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
