package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultimodalStructuredExtractionHandlerTest {

    @Test void actualOcrRetryReceivesSelectedBlocksAndPhysicalBounds() throws Exception {
        var block = new DocumentArtifact.TextBlockEvidence("ocr",2,"评分办法","服务（3分）最高3分",99d,
                new DocumentArtifact.BoundingBox(1,2,20,30,100,100));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("scan","sha","PDF","v2"),
                List.of(),List.of(),List.of(block),"2.2");
        var sidecar = new SidecarDocumentArtifactExtractor("http://localhost:9","",1,new ObjectMapper()) {
            public DocumentArtifact extract(byte[] bytes,String name) { return artifact; }
        };
        int[] retries={0};
        var model=new TextAwareScoreFactExtractorTestAdapter() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extractWithTextAndProgress(DocumentArtifact input,String text,Runnable heartbeat) {
                return new ExtractionResult(null,List.of(),"first",0);
            }
            public ExtractionResult recheckWithTextAndProgress(DocumentArtifact input,String text,Runnable heartbeat) {
                retries[0]++;
                assertThat(input.textBlocks()).singleElement().satisfies(b -> {
                    assertThat(b.bounds()).isEqualTo(block.bounds());
                    assertThat(b.sourcePage()).isEqualTo(2);
                });
                assertThat(com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog.build(input)).hasSize(1);
                return new ExtractionResult(null,List.of(),"retry",0);
            }
        };
        MultimodalStructuredExtractionHandler.extract(new TenderTextSource("scan.pdf","memory:scan","服务（3分）最高3分"),
                new LoadedBidFile.InMemory(new byte[]{1},"scan.pdf","application/pdf"),sidecar,model);
        assertThat(retries[0]).isEqualTo(1);
    }

    @Test
    void mixedFileFailurePreservesSuccessfulArtifactAndReturnedDiagnostics() throws Exception {
        for (int failedIndex : List.of(0, 1)) {
            var sidecar = new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                public DocumentArtifact extract(byte[] bytes, String name) throws java.io.IOException {
                    if (name.equals("file" + failedIndex + ".pdf")) throw new java.io.IOException("private-service-url-and-secret");
                    return artifact();
                }
            };
            var model = new TextAwareScoreFactExtractorTestAdapter() {
                public boolean supportsTextOnly() { return true; }
                public ExtractionResult extractWithTextAndProgress(DocumentArtifact input,String text,Runnable heartbeat) {
                    assertThat(input.tables()).extracting(DocumentArtifact.CanonicalTable::tableId)
                            .containsExactly("doc" + (1-failedIndex) + "-t1");
                    return new ExtractionResult(BigDecimal.TEN, List.of(new MultimodalScoreFact("商务", "1", "", "销售额", BigDecimal.TEN,
                            "销售额达到1亿元得10分", "OBJECTIVE",1,"",List.of(),"销售额达到1亿元得10分",.99)),"test",0);
                }
            };
            var files = List.<LoadedBidFile>of(new LoadedBidFile.InMemory(new byte[]{1},"file0.pdf","application/pdf"),
                    new LoadedBidFile.InMemory(new byte[]{2},"file1.pdf","application/pdf"));
            var sources = List.of(new TenderTextSource("file0.pdf","memory:0","销售额达到1亿元得10分"),
                    new TenderTextSource("file1.pdf","memory:1",""));
            var result = MultimodalStructuredExtractionHandler.extractWithTextModelHeartbeat(sources.getFirst(),files,sources,
                    sidecar,model,()->{});
            assertThat(result.sourceDiagnostics()).contains("SOURCE_ARTIFACT_UNAVAILABLE:doc" + failedIndex + ":IOException");
            assertThat(result.sourceDiagnostics().toString()).doesNotContain("private-service-url-and-secret");
            assertThat(result.facts()).isNotEmpty();
            assertThat(result.facts().getFirst().sourceMetadata().diagnostics()).containsAll(result.sourceDiagnostics());
            assertThat(result.originalVerified()).isFalse();
        }
    }

    @Test
    void allSourceFailuresRemainDiagnosableWhenModelReturnsNoFacts() throws Exception {
        var sidecar = new SidecarDocumentArtifactExtractor("http://localhost:9","",1,new ObjectMapper()) {
            public DocumentArtifact extract(byte[] bytes,String name) throws java.io.IOException {
                throw new java.io.IOException("secret");
            }
        };
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extractTextAndProgress(String text,Runnable heartbeat) {
                return new ExtractionResult(null,List.of(),"test",0);
            }
        };
        var result = MultimodalStructuredExtractionHandler.extract(new TenderTextSource("bid.pdf","memory:0","正文"),
                new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","application/pdf"),sidecar,model);
        assertThat(result.facts()).isEmpty();
        assertThat(result.sourceDiagnostics()).contains("SOURCE_ARTIFACT_UNAVAILABLE:doc0:IOException");
    }

    @Test
    void anchoredRetryOnlyReceivesMissingSourceAndPreservesFirstFact() throws Exception {
        var source = originalHundredPointArtifact(false);
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        int[] retryCount = {0};
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extractWithTextAndProgress(DocumentArtifact input, String text, Runnable heartbeat) {
                return new ExtractionResult(new BigDecimal("100"), List.of(unbound("1", "10")), "first", 0);
            }
            public ExtractionResult recheckWithTextAndProgress(DocumentArtifact input, String text, Runnable heartbeat) {
                retryCount[0]++;
                assertThat(text).contains("技术方案").doesNotContain("销售额");
                assertThat(input.tables().stream().flatMap(t -> t.cells().stream()))
                        .noneMatch(cell -> cell.cellId().contains("c1-"));
                return new ExtractionResult(null, List.of(unbound("2", "90")), "retry", 0);
            }
            private MultimodalScoreFact unbound(String code,String weight) {
                var f = sourceFact(code,weight);
                return new MultimodalScoreFact(f.sectionTitle(), f.code(), f.parentCode(), f.dimension(), f.weight(),
                        f.ruleText(), f.scoreType(), f.pageNumber(), "", List.of(), f.quotedText(), f.confidence());
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(retryCount[0]).isEqualTo(1);
        assertThat(result.facts()).extracting(f -> f.code()).containsExactly("1", "2");
        assertThat(result.facts().stream().map(f -> f.weight()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
    }

    @Test
    void modelFailureRechecksWithModelBeforeShowingIncompleteFallback() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        int[] rechecks = {0};
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            @Override
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("Request failed");
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact input) {
                rechecks[0]++;
                return new ExtractionResult(BigDecimal.TEN, List.of(fact()), "recheck-model", 0);
            }
        };

        StructuredTableResult result = extract(artifactExtractor, model);

        assertThat(rechecks[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void textExtractionReceivesArtifactBeforeModelAndBindsOriginalEvidence() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            @Override
            public boolean supportsTextOnly() {
                return true;
            }

            @Override
            public ExtractionResult extractWithTextAndProgress(DocumentArtifact input, String text, Runnable heartbeat) {
                assertThat(input).isNotNull();
                assertThat(input.tables()).extracting(DocumentArtifact.CanonicalTable::tableId).containsExactly("doc0-t1");
                return new ExtractionResult(BigDecimal.TEN,
                        List.of(new MultimodalScoreFact("商务", "1", "", "销售额", BigDecimal.TEN,
                                "销售额达到1亿元得10分", "OBJECTIVE", 1, "", List.of(),
                                "销售额达到1亿元得10分", 0.99)), "text", 0);
            }
        };

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "销售额达到1亿元得10分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"), artifactExtractor, model, null);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.facts()).singleElement().satisfies(fact -> {
            assertThat(fact.sourceTableId()).isEqualTo("doc0-t1");
            assertThat(fact.sourceCellIds()).containsExactly("doc0-c1");
        });
        assertThat(result.qualifications()).singleElement().satisfies(qualification -> {
            assertThat(qualification.scopeStatus()).isEqualTo("IN_SCHEME");
            assertThat(qualification.candidateEligible()).isTrue();
            assertThat(qualification.factId()).isEqualTo(result.facts().get(0).factId());
        });
    }

    @Test
    void incompleteModelRecallUsesCompleteRecheckResult() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        DocumentArtifact source = originalHundredPointArtifact(false);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        int[] rechecks = {0};
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            @Override
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact input) {
                assertThat(input).isSameAs(source);
                rechecks[0]++;
                return new ExtractionResult(new BigDecimal("100"),
                        List.of(sourceFact("1", "10"), sourceFact("2", "90")), "recheck", 0);
            }
        };

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "本项目评分总分：100分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(rechecks[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("10"), new BigDecimal("90"));
        assertThat(result.candidates()).extracting(candidate -> candidate.dim())
                .containsExactly("销售额", "技术方案");
    }

    @Test
    void shrinkingRecheckTotalCannotPublishMissingNinetyPointItem() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        DocumentArtifact source = originalHundredPointArtifact(false);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        int[] calls = {0};
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            @Override
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }

            @Override
            public ExtractionResult recheck(DocumentArtifact input) {
                assertThat(input).isSameAs(source);
                calls[0]++;
                return new ExtractionResult(new BigDecimal("10"), List.of(sourceFact("1", "10")), "recheck", 0);
            }
        };

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "本项目评分总分：100分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(calls[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.facts()).hasSize(1);
        assertThat(result.facts()).hasSize(1);
        assertThat(result.facts().get(0).weightEvidence().explanation()).contains("WEIGHT_SUM_MISMATCH");
    }

    @Test
    void wrongItemWeightAfterRecheckCannotPublishEvenWhenTotalMatches() throws Exception {
        var source = originalHundredPointArtifact(false);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "100")), "recheck", 0);
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.facts()).hasSize(1);
        assertThat(result.facts().get(0).weightEvidence().explanation()).contains("WEIGHT_SUM_MISMATCH");
    }

    @Test
    void balancedWrongItemWeightsStillCannotPublish() throws Exception {
        var source = originalHundredPointArtifact(false);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("100"),
                        List.of(sourceFact("1", "20"), sourceFact("2", "80")), "recheck", 0);
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.facts()).hasSize(1);
        assertThat(result.facts().get(0).weightEvidence().explanation()).contains("WEIGHT_SUM_MISMATCH");
    }

    @Test
    void incompleteFirstAndFailedRecheckStayReviewRequired() throws Exception {
        var source = originalHundredPointArtifact(false);
        int[] calls = {0};
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                calls[0]++;
                throw new IllegalStateException("model unavailable");
            }
        };
        var result = extractSource(source, "本项目评分总分100分", model);
        assertThat(calls[0]).isEqualTo(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void weakerNonEmptyRecheckDoesNotReplaceInitialFacts() throws Exception {
        var source = originalHundredPointArtifact(false);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("90"),
                        List.of(sourceFact("1", "10"), sourceFact("2", "90")), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("100"), List.of(sourceFact("1", "10")), "recheck", 0);
            }
        };

        var result = extractSource(source, "本项目评分总分100分", model);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.facts()).hasSize(2);
        assertThat(result.facts()).extracting(fact -> fact.code()).containsExactly("1", "2");
    }

    @Test
    void initialAndRecheckUseTenderTextWithoutRenderingPageImages() throws Exception {
        var ruleCell = new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "rule-p2", 0, 0, "技术方案最高得10分", "技术方案最高得10分", 1, 1, false, "pdf:p=2", 2, "score-r0");
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "PDF", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score", 1, "评分办法", 1, 1,
                        List.of(), List.of(ruleCell), true)), List.of());
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        SidecarImageExtractor imageExtractor = mock(SidecarImageExtractor.class);
        List<String> receivedText = new java.util.ArrayList<>();
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extractWithText(DocumentArtifact input, String sourceText) {
                receivedText.add(sourceText);
                return new ExtractionResult(new BigDecimal("9"), List.of(fact()), "first", 0);
            }
            public ExtractionResult recheckWithText(DocumentArtifact input, String sourceText) {
                receivedText.add(sourceText);
                return new ExtractionResult(BigDecimal.TEN, List.of(fact()), "recheck", 0);
            }
            private MultimodalScoreFact fact() {
                return new MultimodalScoreFact("评分办法", "1", "", "技术方案", BigDecimal.TEN,
                        "技术方案最高得10分", "OBJECTIVE", 2, "score", List.of("rule-p2"),
                        "技术方案最高得10分", 0.9);
            }
        };

        var result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "本项目评分总分10分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, imageExtractor);

        assertThat(receivedText).containsExactly("本项目评分总分10分", "本项目评分总分10分");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        org.mockito.Mockito.verifyNoInteractions(imageExtractor);
    }

    @Test
    void truncatedPageImagesDoNotBlockDeterministicTextFallback() throws Exception {
        DocumentArtifact complete = loadCompleteArtifact();
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(complete);
        SidecarImageExtractor imageExtractor = mock(SidecarImageExtractor.class);
        when(imageExtractor.renderPages(any(byte[].class), eq("bid.pdf"), any()))
                .thenReturn(new SidecarImageExtraction(List.of(), true));
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact artifact, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(), "first", images.size());
            }
            public ExtractionResult recheck(DocumentArtifact artifact, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("100"), List.of(), "recheck", images.size());
            }
        };

        var result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", ""),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, imageExtractor);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).isNotEmpty();
        org.mockito.Mockito.verifyNoInteractions(imageExtractor);
    }

    @Test
    void missingOriginalTotalNeverFallsBackToDefaultHundred() throws Exception {
        var full = loadCompleteArtifact();
        var table = full.tables().get(0);
        var noSummary = new DocumentArtifact(full.artifact(),
                List.of(new DocumentArtifact.CanonicalTable(table.tableId(), table.sourcePage(),
                        table.sourceSection(), table.rows() - 1, table.columns(), table.mergedRegions(),
                        table.cells().stream().filter(c -> c.row() < 4).toList(), true)), full.warnings());
        assertThat(ScoreFactShadowPipeline.run(noSummary, "").completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("first failed");
            }
            public ExtractionResult recheck(DocumentArtifact input) { return new ExtractionResult(
                    new BigDecimal("100"), List.of(), "recheck", 0); }
        };
        var result = extractSource(noSummary, "", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void eightyPointOriginalWithThirtyAndFiftyAutoPublishesWithoutRecheck() throws Exception {
        var cells = List.of(
                sourceCell("e1-name", 0, 0, "商务服务"), sourceCell("e1-rule", 0, 1, "售后保障最高得30分"),
                sourceCell("e1-score", 0, 2, "30分"), sourceCell("e2-name", 1, 0, "技术方案"),
                sourceCell("e2-rule", 1, 1, "技术方案最高得50分"), sourceCell("e2-score", 1, 2, "50分"));
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score-t1", 3, "本项目评分办法", 2, 3,
                        List.of(), cells, false)), List.of());
        int[] rechecks = {0};
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return new ExtractionResult(new BigDecimal("80"), List.of(
                        new MultimodalScoreFact("本项目评分办法", "1", "", "商务服务", new BigDecimal("30"),
                                "售后保障最高得30分", "OBJECTIVE", 3, "score-t1", List.of("e1-rule"),
                                "售后保障最高得30分", 0.9),
                        new MultimodalScoreFact("本项目评分办法", "2", "", "技术方案", new BigDecimal("50"),
                                "技术方案最高得50分", "SUBJECTIVE", 3, "score-t1", List.of("e2-rule"),
                                "技术方案最高得50分", 0.9)), "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                rechecks[0]++;
                throw new AssertionError("valid first extraction must not recheck");
            }
        };
        var result = extractSource(source, "本项目评分总分80分", model);
        assertThat(rechecks[0]).isZero();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("30"), new BigDecimal("50"));
    }

    @Test
    void deterministicFallbackWithConflictingItemWeightCannotPublish() throws Exception {
        var full = loadCompleteArtifact();
        var table = full.tables().get(0);
        var cells = table.cells().stream().map(c -> {
            String replacement = switch (c.cellId()) {
                case "t-sanitized-r1-c2" -> "整体方案最高得90分";
                case "t-sanitized-r1-c3" -> "100";
                case "t-sanitized-r4-c3" -> "110";
                default -> c.rawText();
            };
            return new DocumentArtifact.CanonicalTable.CanonicalCellDto(c.cellId(), c.row(), c.column(),
                    replacement, replacement, c.rowspan(), c.colspan(), c.isMergedContinue(), c.sourceXmlPath(),
                    c.sourcePage(), c.logicalRowId());
        }).toList();
        var source = new DocumentArtifact(full.artifact(), List.of(new DocumentArtifact.CanonicalTable(
                table.tableId(), table.sourcePage(), table.sourceSection(), table.rows(), table.columns(),
                table.mergedRegions(), cells, true)), full.warnings());
        assertThat(ScoreFactShadowPipeline.run(source, "评分总分110分").completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input, List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                throw new IllegalStateException("first failed");
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                return new ExtractionResult(new BigDecimal("110"), List.of(), "recheck", 0);
            }
        };
        var result = extractSource(source, "评分总分110分", model);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    private static StructuredTableResult extractSource(DocumentArtifact source, String tenderText,
                                                       MultimodalScoreFactExtractor model) throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        return MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", tenderText),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);
    }

    private static MultimodalScoreFact sourceFact(String code, String weight) {
        String rule = "1".equals(code) ? "销售额达到1亿元得10分" : "技术方案按完整性与可行性综合评议，最高得90分";
        String name = "1".equals(code) ? "销售额" : "技术方案";
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, "score-t1", List.of("c" + code + "-rule"), rule, 0.9);
    }

    private static DocumentArtifact originalHundredPointArtifact(boolean fallbackAvailable) {
        var cells = List.of(
                sourceCell("total", 0, 0, "本项目评分总分：100分"),
                sourceCell("c1-name", 1, 0, "销售额"),
                sourceCell("c1-rule", 1, 1, "销售额达到1亿元得10分"),
                sourceCell("c1-weight", 1, 2, "10分"),
                sourceCell("c2-name", 2, 0, "技术方案"),
                sourceCell("c2-rule", 2, 1, "技术方案按完整性与可行性综合评议，最高得90分"),
                sourceCell("c2-weight", 2, 2, "90分"));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score-t1", 3, "本项目评分办法", 3, 3,
                        List.of(), cells, fallbackAvailable)), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto sourceCell(
            String id, int row, int column, String text) {
        return sourceCell("score-t1", 3, id, row, column, text);
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto sourceCell(
            String table, int page, String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column,
                text, text, 1, 1, false, "pdf:page=" + page + ";table=" + table, page, table + "-r" + row);
    }

    @Test
    void validModelFactsAreAutoApproved() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        MultimodalScoreFactExtractor model = textAware((artifact, images) -> new MultimodalScoreFactExtractor.ExtractionResult(
                BigDecimal.TEN, List.of(fact()), "test-model", images.size()));

        StructuredTableResult result = extract(artifactExtractor, model);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void missingModelProducesReviewFactsAndNeverAutoApproves() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());

        StructuredTableResult result = extract(artifactExtractor, null);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void modelFailureProducesReviewFactsAndNeverAutoApproves() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact());
        MultimodalScoreFactExtractor model = textAware((artifact, images) -> { throw new IllegalStateException("boom"); });

        StructuredTableResult result = extract(artifactExtractor, model);

        assertThat(result.evidencePipeline()).isTrue();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void incompleteModelRecallFallsBackToCompleteDeterministicTableFacts() throws Exception {
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        DocumentArtifact artifact = loadCompleteArtifact();
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(artifact);
        MultimodalScoreFactExtractor model = textAware((input, images) -> new MultimodalScoreFactExtractor.ExtractionResult(
                new BigDecimal("100"), List.of(new MultimodalScoreFact(
                        "方案能力", "A1", "", "方案能力", new BigDecimal("90"),
                        "整体方案完整可行", "SUBJECTIVE", null, "t-sanitized-merged-origin",
                        List.of("t-sanitized-r1-c2"), "整体方案完整可行", 0.99)),
                "test-model", images.size()));

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "评分总分100分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("90"), new BigDecimal("5"), new BigDecimal("5"));
    }

    @Test
    void multiTableModelTotalCannotBeUsedAsDocumentWideDefault() throws Exception {
        DocumentArtifact source = new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(
                        new DocumentArtifact.CanonicalTable("t1", 1, "商务", 1, 2, List.of(),
                                List.of(sourceCell("t1", 1, "t1-rule", 0, 1, "商务服务最高得50分")), true),
                        new DocumentArtifact.CanonicalTable("t2", 2, "技术", 1, 2, List.of(),
                                List.of(sourceCell("t2", 2, "t2-rule", 0, 1, "技术方案最高得50分")), true)),
                List.of());
        SidecarDocumentArtifactExtractor artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        MultimodalScoreFactExtractor model = textAware((input, images) -> new MultimodalScoreFactExtractor.ExtractionResult(
                new BigDecimal("100"), List.of(
                        new MultimodalScoreFact("商务", "1", "", "商务服务", new BigDecimal("50"),
                                "商务服务最高得50分", "OBJECTIVE", 1, "t1", List.of("t1-rule"),
                                "商务服务最高得50分", 0.9),
                        new MultimodalScoreFact("技术", "1", "", "技术方案", new BigDecimal("50"),
                                "技术方案最高得50分", "SUBJECTIVE", 2, "t2", List.of("t2-rule"),
                                "技术方案最高得50分", 0.9)), "model", 0));

        StructuredTableResult result = MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "评分办法"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);

        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().declaredTotalScore()).isNull();
        assertThat(result.completeness().declaredTotalSource())
                .isEqualTo(com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN);
        assertThat(result.candidates()).isEmpty();
    }

    private static StructuredTableResult extract(SidecarDocumentArtifactExtractor artifactExtractor,
                                                  MultimodalScoreFactExtractor model) {
        return MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", "评分办法，总分10分"),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);
    }

    private static MultimodalScoreFactExtractor textAware(
            BiFunction<DocumentArtifact, List<com.xiyu.bid.scoreparse.domain.DocumentImage>,
                    MultimodalScoreFactExtractor.ExtractionResult> extraction) {
        return new TextAwareScoreFactExtractorTestAdapter() {
            @Override
            public ExtractionResult extract(DocumentArtifact artifact,
                                            List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                return extraction.apply(artifact, images);
            }
        };
    }

    private static MultimodalScoreFact fact() {
        return new MultimodalScoreFact("商务", "1", "", "销售额", BigDecimal.TEN,
                "销售额达到1亿元得10分", "OBJECTIVE", 1, "t1", List.of("c1"),
                "销售额达到1亿元得10分", 0.99);
    }

    private static DocumentArtifact artifact() {
        var total = sourceCell("t1", 1, "total", 0, 0, "本项目评分总分：10分");
        var cell = sourceCell("t1", 1, "c1", 1, 1, "销售额达到1亿元得10分");
        var table = new DocumentArtifact.CanonicalTable(
                "t1", 1, "商务", 2, 2, List.of(), List.of(total, cell), true);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(table), List.of());
    }

    @Test void samePhysicalUnitFromTwoExtractionSetsIsNeverCountedTwice() throws Exception {
        var artifact=loadCompleteArtifact();
        var deterministic=fallbackWithOriginalModel(false).facts().getFirst();
        var duplicate=new MultimodalScoreFact("本项目评分办法",deterministic.code(),"",deterministic.dimension(),deterministic.weight(),deterministic.sourceText(),"SUBJECTIVE",1,deterministic.sourceTableId(),deterministic.sourceCellIds(),deterministic.sourceText(),.8);
        var result=MultimodalStructuredExtractionHandler.run("probe.pdf","本项目评分总分100分",artifact,
                com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(artifact,"本项目评分总分100分"),null,()->{},
                new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"),List.of(duplicate),"probe",0),true,true,true);
        var snapshot=com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot.from("same-unit",result.facts(),result.qualifications(),result.sourceDiagnostics());
        assertThat(snapshot.items()).hasSize(5);
        assertThat(snapshot.items().stream().map(com.xiyu.bid.scoreparse.domain.ExtractedScoreItem::itemId).distinct()).hasSize(5);
        assertThat(snapshot.items().stream().filter(com.xiyu.bid.scoreparse.domain.ExtractedScoreItem::computable).map(com.xiyu.bid.scoreparse.domain.ExtractedScoreItem::effectiveMaximum).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");
    }

    static StructuredTableResult fallbackWithOriginalModel(boolean anchored) throws Exception {
        var artifact=loadCompleteArtifact();
        var raw=new MultimodalScoreFact("本项目评分办法","raw-unverified","","审查原始未核验候选",new BigDecimal("6"),"模型原始规则最高6分","SUBJECTIVE",null,"",List.of(),"未定位的原文引文",.6,com.xiyu.bid.scoreparse.domain.ChildAggregation.SUM);
        if(anchored) {
            var sidecar=new SidecarDocumentArtifactExtractor("http://localhost:9","",1,new ObjectMapper()) {
                @Override public DocumentArtifact extract(byte[] bytes,String name) {return artifact;}
            };
            var model=new MultimodalScoreFactExtractor() {
                public boolean supportsTextOnly(){return true;}
                public ExtractionResult extract(DocumentArtifact a,List<com.xiyu.bid.scoreparse.domain.DocumentImage> images){throw new AssertionError("text path expected");}
                public ExtractionResult extractWithText(DocumentArtifact a,String text){return new ExtractionResult(new BigDecimal("100"),List.of(raw),"fixed-extraction-port",0);}
            };
            var source=new TenderTextSource("probe.pdf",null,"本项目评分总分100分");
            return MultimodalStructuredExtractionHandler.extractWithTextModelHeartbeat(source,
                    List.of(new LoadedBidFile.InMemory(new byte[]{1},"probe.pdf","application/pdf")),List.of(source),sidecar,model,()->{});
        }
        return MultimodalStructuredExtractionHandler.run("probe.pdf","本项目评分总分100分",artifact,
            com.xiyu.bid.scoreparse.domain.OriginalScoringContext.resolve(artifact,"本项目评分总分100分"),null,()->{},
            new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"),List.of(raw),"probe",0),true,true,anchored);
    }
    @Test void anchoredFallbackRetainsModelProposalWithoutMixingScoringSets() throws Exception {
        var result=fallbackWithOriginalModel(true);
        assertThat(result.facts()).hasSize(5).anyMatch(f -> f.dimension().equals("审查原始未核验候选"));
        var snapshot=com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot.from("fallback",result.facts(),result.qualifications(),result.sourceDiagnostics());
        assertThat(snapshot.items().stream().filter(i -> i.name().equals("审查原始未核验候选")).findFirst().orElseThrow().diagnostics()).contains("ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED");
        assertThat(snapshot.items().stream().filter(com.xiyu.bid.scoreparse.domain.ExtractedScoreItem::computable).map(com.xiyu.bid.scoreparse.domain.ExtractedScoreItem::effectiveMaximum).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(fallbackWithOriginalModel(false).facts()).hasSize(4);
    }

    private static DocumentArtifact loadCompleteArtifact() throws Exception {
        try (InputStream input = MultimodalStructuredExtractionHandlerTest.class.getResourceAsStream(
                "/scoreparse-golden/merged-parent-first-leaf-table-v2.json")) {
            return new ObjectMapper().readValue(input, DocumentArtifact.class);
        }
    }
}
