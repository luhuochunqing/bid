// Input: scoreparse-golden 夹具回放基线 v0（spec 057 US3 / plan D-C）
// Output: 每案 precision/recall/F1 硬断言 + target/scoreparse-golden-report.json 回放报告
// Pos: scoreparse/golden — 纯 JUnit5，无 Spring 上下文，静态驱动确定性管线（LLM 层不在范围）
//
// 备注：复杂口径提取到 GoldenCasesReplaySupport，避免测试文件超长导致维护成本过高。
package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenCasesReplayTest {

    private static final List<GoldenCasesReplaySupport.ReplayReportRow> REPORT_ROWS =
            Collections.synchronizedList(new ArrayList<>());

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("case-dual-section-renumber"),
                Arguments.of("case-weight-gap-mixed"),
                Arguments.of("case-1347-compound-percent"),
                Arguments.of("case-bracket-vs-pure-conflict"),
                Arguments.of("case-declared-total-80"),
                Arguments.of("case-review-required-80-gap"),
                Arguments.of("case-1374-redundant-formula"),
                Arguments.of("case-3col-embedded-bracket-weight"),
                Arguments.of("case-1381-pdf-preliminary-and-summary"),
                Arguments.of("case-pdf-cert-list-single-item"),
                Arguments.of("case-1117-performance-evidence"),
                Arguments.of("case-multi-table-tech-commercial"),
                Arguments.of("case-category-weight-noise-before-score"),
                Arguments.of("case-sidecar-stream-failure"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void replayGoldenCase(String caseName) throws Exception {
        String markdown = GoldenCasesReplaySupport.readFixture(caseName + ".md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(
                GoldenCasesReplaySupport.readFixture(caseName + ".expected.json"));

        List<GoldenCasesReplaySupport.ExpectedItem> expectedItems =
                GoldenCasesReplaySupport.readExpectedItems(expected);
        double threshold = expected.path("threshold").asDouble();

        GoldenCasesReplaySupport.CandidatePool pool = GoldenCasesReplaySupport.collectCandidates(markdown);

        List<ScoreCandidate> merged = new ScoreItemMergePolicy().merge(pool.allCandidates());
        List<ScoreCandidate> curated = GoldenCasesReplaySupport.filterInvalidWeights(
                new GhostCandidateFilter().filter(merged));

        BigDecimal expectedTotal = GoldenCasesReplaySupport.expectedTotal(expected);
        var normalization = GoldenCasesReplaySupport.normalizeCandidates(curated);
        BigDecimal preTotal = GoldenCasesReplaySupport.sumWeights(curated);
        BigDecimal postTotal;
        if (preTotal.subtract(expectedTotal).abs()
                .compareTo(GoldenCasesReplaySupport.normalizationTolerance()) <= 0) {
            postTotal = preTotal;
        } else if (normalization.originalTotal() != null) {
            assertTrue(preTotal.compareTo(normalization.originalTotal()) == 0,
                    () -> caseName + " originalTotal 应等于归一化前合计");
            assertTrue(preTotal.subtract(expectedTotal).abs()
                            .compareTo(GoldenCasesReplaySupport.normalizationTolerance()) > 0,
                    () -> caseName + " 合计在容差内却触发了归一化");
            postTotal = GoldenCasesReplaySupport.sumWeights(normalization.normalizedCandidates());
            assertTrue(postTotal.compareTo(GoldenCasesReplaySupport.expectedOneHundred()) == 0,
                    () -> caseName + " 归一化后权重合计必须=100，实际=" + postTotal);
        } else {
            assertTrue(preTotal.subtract(expectedTotal).abs()
                            .compareTo(GoldenCasesReplaySupport.normalizationTolerance()) <= 0,
                    () -> caseName + " 合计偏离声明总分却未触发归一化？preTotal=" + preTotal
                            + " expectedTotal=" + expectedTotal);
            postTotal = preTotal;
        }

        var match = GoldenCasesReplaySupport.matchPredictions(curated, expectedItems);
        GoldenCasesReplaySupport.Prf prf = match.prf();
        assertTrue(prf.f1() >= threshold,
                () -> caseName + " 金标回放 F1=" + prf.f1() + " < threshold=" + threshold
                        + System.lineSeparator() + GoldenCasesReplaySupport.describeMismatch(
                        curated,
                        expectedItems,
                        match.predictedMatched(),
                        match.expectedMatched()));

        GoldenCasesReplaySupport.assertOptionalContracts(caseName, expected, expectedItems, curated, preTotal);

        Map<String, Integer> laneCounts = GoldenCasesReplaySupport.laneCounts(pool);
        String decisionStatus = preTotal.compareTo(expectedTotal) == 0
                ? "AUTO_APPROVED" : "REVIEW_REQUIRED";

        REPORT_ROWS.add(new GoldenCasesReplaySupport.ReplayReportRow(
                caseName,
                laneCounts,
                prf,
                preTotal,
                postTotal,
                normalization.originalTotal(),
                decisionStatus,
                true,
                GoldenCasesReplaySupport.evidenceCoverage(pool)));
    }

    @AfterAll
    static void writeReplayReport() {
        Path reportPath = Paths.get(System.getProperty("user.dir"), "target",
                "scoreparse-golden-report.json");
        GoldenCasesReplaySupport.writeReplayReport(reportPath, REPORT_ROWS);
    }

    @org.junit.jupiter.api.Test
    void mirrorMatchesMapperContract_fixedVector() {
        ScoringCriterion criterion = new ScoringCriterion(
                "2-1", "技术", "方案完整可行得10分", new java.math.BigDecimal("10"), null);

        var candidate = GoldenCasesReplaySupport.toCandidate(criterion, null);
        assertAll(
                () -> assertEquals("2-1", candidate.code()),
                () -> assertEquals("技术", candidate.dim()),
                () -> assertEquals("方案完整可行得10分", candidate.detail()),
                () -> assertEquals(new java.math.BigDecimal("10"), candidate.weight())
        );
    }

    @org.junit.jupiter.api.Test
    void mirrorMatchesMapperContract_preservesEvidenceWithoutFormattingCorruption() {
        ScoringCriterion criterion = new ScoringCriterion(
                "2-2", "商务及技术评分", "包含换行（10\n分）的评分细则", new java.math.BigDecimal("10"), null);

        var candidate = GoldenCasesReplaySupport.toCandidate(criterion, null);
        assertEquals("2-2", candidate.code());
        assertEquals("商务及技术评分", candidate.dim());
        assertEquals("包含换行（10\n分）的评分细则", candidate.detail());
    }
}
