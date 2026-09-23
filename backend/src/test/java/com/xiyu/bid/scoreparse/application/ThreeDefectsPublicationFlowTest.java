package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.assertNotPublished;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.categoryDetails;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.categoryFacts;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.categoryText;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.extract;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.itemMaximumDetails;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.itemMaximumFact;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.itemMaximumTechnicalFact;
import static com.xiyu.bid.scoreparse.application.SchemeScopePublicationTest.modelExtract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreeDefectsPublicationFlowTest {
    @Test void itemMaximumCannotPublishTenPointModelResultWithoutSchemeTotal() throws Exception {
        var result = modelExtract(itemMaximumDetails(), "", List.of(itemMaximumFact()), "10");
        assertThat(result.completeness().declaredTotalScore()).isNull();
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.originalVerified()).isFalse();
        assertNotPublished(result);
    }

    @Test void completeItemMaximumDetailsPublishWithExplicitHundred() throws Exception {
        var result = modelExtract(itemMaximumDetails(), "本项目评分总分100分。",
                List.of(itemMaximumFact(), itemMaximumTechnicalFact()), "100");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("10"), new BigDecimal("90"));
    }

    @Test void conflictingSubtotalsRejectModelRecheckAndDeterministicFallback() throws Exception {
        var original = categoryDetails("30", "70");
        var facts = categoryFacts();
        var model = modelExtract(original, categoryText(), facts, "100");
        var deterministic = extract(original, categoryText());
        for (var result : List.of(model, deterministic)) {
            assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
            assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
            assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");
            assertThat(result.completeness().defects()).anyMatch(d -> d.contains("TABLE_SUBTOTAL_MISMATCH")
                    && d.contains("t-business") && d.contains("30") && d.contains("20"));
            assertThat(result.completeness().defects()).anyMatch(d -> d.contains("TABLE_SUBTOTAL_MISMATCH")
                    && d.contains("t-technical") && d.contains("70") && d.contains("80"));
            assertThat(result.originalVerified()).isFalse();
            assertNotPublished(result);
        }
    }

    @Test void matchingSubtotalsPublishViaModelRecheckAndDeterministicFallback() throws Exception {
        var original = categoryDetails("20", "80");
        var facts = categoryFacts();
        var model = modelExtract(original, categoryText(), facts, "100");
        var deterministic = extract(original, categoryText());
        for (var result : List.of(model, deterministic)) {
            assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
            assertThat(result.originalVerified()).isTrue();
            assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                    .containsExactly(new BigDecimal("20"), new BigDecimal("80"));
        }
    }

    @Test void conflictingSubtotalsRemainRejectedAfterExactlyOneRecheck() throws Exception {
        AtomicInteger rechecks = new AtomicInteger();
        var result = modelExtractResponses(categoryDetails("30", "70"), categoryText(),
                categoryFacts(), categoryFacts(), rechecks);
        assertThat(rechecks).hasValue(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.completeness().defects()).anyMatch(d -> d.contains("TABLE_SUBTOTAL_MISMATCH"));
        assertThat(result.originalVerified()).isFalse();
        assertNotPublished(result);
    }

    @Test void genuinelyCompletedRecheckPublishesMatchingSubtotals() throws Exception {
        AtomicInteger rechecks = new AtomicInteger();
        var original = categoryDetails("20", "80");
        var result = modelExtractResponses(original, categoryText(),
                List.of(categoryFacts().get(0)), categoryFacts(), rechecks);
        assertThat(rechecks).hasValue(1);
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.candidates()).extracting(candidate -> candidate.weight())
                .containsExactly(new BigDecimal("20"), new BigDecimal("80"));
    }

    @Test void siblingScoringHeadingsPublishAsOneHundredPointScheme() throws Exception {
        var original = categoryDetails("20", "80", "商务评分标准", "技术评分标准");
        var result = modelExtract(original, "# 本项目评分办法\n总分100分。\n"
                + "## 商务评分标准\n商务20分明细表。\n"
                + "## 技术评分标准\n技术80分明细表。", categoryFacts(), "100");
        assertThat(result.completeness().declaredTotalScore()).isEqualByComparingTo("100");
        assertThat(result.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
    }

    static StructuredTableResult modelExtractResponses(DocumentArtifact source, String text,
            List<MultimodalScoreFact> first, List<MultimodalScoreFact> rechecked, AtomicInteger calls)
            throws Exception {
        var artifactExtractor = mock(SidecarDocumentArtifactExtractor.class);
        when(artifactExtractor.extract(any(byte[].class), eq("bid.pdf"))).thenReturn(source);
        MultimodalScoreFactExtractor model = new TextAwareScoreFactExtractorTestAdapter() {
            public ExtractionResult extract(DocumentArtifact input,
                    List<com.xiyu.bid.scoreparse.domain.DocumentImage> images) {
                assertThat(input).isSameAs(source);
                return new ExtractionResult(new BigDecimal("100"), first, "first", 0);
            }
            public ExtractionResult recheck(DocumentArtifact input) {
                assertThat(input).isSameAs(source);
                calls.incrementAndGet();
                return new ExtractionResult(new BigDecimal("100"), rechecked, "recheck", 0);
            }
        };
        return MultimodalStructuredExtractionHandler.extract(
                new TenderTextSource("bid.pdf", "obs://bid.pdf", text),
                new LoadedBidFile.InMemory(new byte[]{1}, "bid.pdf", "application/pdf"),
                artifactExtractor, model, null);
    }
}
