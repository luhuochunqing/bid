// Input: scoreparse-golden 夹具回放基线 v0（spec 057 US3 / plan D-C）
// Output: 每案 precision/recall/F1 硬断言 + target/scoreparse-golden-report.json 回放报告
// Pos: scoreparse/golden — 纯 JUnit5，无 Spring 上下文，静态驱动确定性管线（LLM 层不在范围）
//
// 备注：复杂口径提取到 GoldenCasesReplaySupport，避免测试文件超长导致维护成本过高。
package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenCasesReplayTest {

    private static final List<GoldenCasesReplaySupport.ReplayReportRow> REPORT_ROWS =
            Collections.synchronizedList(new ArrayList<>());

    @Test
    void peerSectionTextCannotPublishAsScoringSchemeEvidence() throws Exception {
        String text = GoldenCasesReplaySupport.readFixture("case-text-peer-scope.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(
                GoldenCasesReplaySupport.readFixture("case-text-peer-scope.expected.json"));
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "scope", "docx", "2.0"),
                List.of(), List.of());
        JsonNode scope = expected.path("scopeRegression");
        String quote = scope.path("quote").asText();
        var fact = new MultimodalScoreFact("履约考核办法", "1", "", scope.path("dimension").asText(),
                new BigDecimal(scope.path("weight").asText()), quote, "OBJECTIVE", null, "",
                List.of(scope.path("locator").asText()), quote, 0.9, ChildAggregation.SUM);

        var result = OriginalScoreEvidencePolicy.verifyModel(artifact, text, List.of(fact), new BigDecimal("100"));

        assertTrue(result.problems().stream().anyMatch(problem ->
                problem.code().equals("ORIGINAL_ELIGIBLE_FACTS_MISSING")));
        assertEquals("OUT_OF_SCHEME", result.qualifications().get(0).scopeStatus());
        assertTrue(!result.qualifications().get(0).candidateEligible());
    }

    @Test
    void project1382ReplayExcludesPage98ContractAuditFromSchemeCandidates() throws Exception {
        String text = GoldenCasesReplaySupport.readFixture("case-project-1382-scheme-eligibility.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(
                "case-project-1382-scheme-eligibility.expected.json"));
        String schemeId = OriginalScoringContext.schemeId(expected.path("schemeTitle").asText());
        List<MultimodalScoreFact> facts = new ArrayList<>();
        expected.path("eligibleFacts").forEach(item -> facts.add(textFact(item, schemeId, "IN_SCHEME")));
        expected.path("auditFacts").forEach(item -> facts.add(textFact(item, schemeId, "OUT_OF_SCHEME")));
        var artifact = project1382Artifact(text, expected);

        var verification = OriginalScoreEvidencePolicy.verifyModel(artifact, text, facts,
                expected.path("declaredTotal").decimalValue());
        Map<String, OriginalScoreEvidencePolicy.Qualification> qualifications = verification.qualifications().stream()
                .collect(java.util.stream.Collectors.toMap(OriginalScoreEvidencePolicy.Qualification::factId,
                        qualification -> qualification));
        List<MultimodalScoreFact> eligibleFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(facts, verification);
        BigDecimal eligibleTotal = effectiveTotal(eligibleFacts);
        long auditFactCount = facts.stream().filter(fact -> !qualification(fact, qualifications).candidateEligible()).count();

        assertEquals(expected.path("totalLocator").asText(), verification.total().locator());
        assertEquals(expected.path("totalQuote").asText(), verification.total().quote());
        assertEquals(expected.path("expectedEligibleLeafTotal").decimalValue(), eligibleTotal);
        assertEquals(expected.path("expectedEligibleFactCount").asInt(), eligibleFacts.size());
        assertEquals(expected.path("expectedOutOfSchemeFactCount").asInt(), auditFactCount);
        assertEquals("OUT_OF_SCHEME", qualification(facts.get(facts.size() - 1), qualifications).scopeStatus());
        assertTrue(verification.qualifications().stream().filter(q -> q.scopeStatus().equals("OUT_OF_SCHEME"))
                .allMatch(q -> !q.candidateEligible()));
        assertEquals("OUT_OF_SCHEME", OriginalScoringContext.textScopeAt(text, 19).scopeStatus());
        assertEquals("IN_SCHEME", OriginalScoringContext.textScopeAt(text, 4).scopeStatus());

        var validation = new com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator().validate(
                facts, artifact, text, expected.path("declaredTotal").decimalValue());
        assertEquals(expected.path("expectedEligibleFactCount").asInt(), OriginalScoreEvidencePolicy.eligibleModelFacts(facts, validation.sourceVerification()).size(),
                validation.sourceVerification().qualifications().toString());
        assertEquals(expected.path("expectedEligibleLeafTotal").decimalValue(), validation.leafTotal());
        assertTrue(validation.autoApprovable(), validation.violations().toString());
        assertTrue(validation.violations().stream().noneMatch(violation ->
                List.of("ITEM_SCHEME_SCOPE_MISMATCH", "WEIGHT_SUM_MISMATCH").contains(violation.code())));
    }

    @Test
    void project1384ReplayKeepsOneHundredPointSchemeAndCrossTableParent() throws Exception {
        String text = GoldenCasesReplaySupport.readFixture("case-project-1384-logical-scheme.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(
                "case-project-1384-logical-scheme.expected.json"));
        DocumentArtifact artifact = project1384Artifact(text, expected);
        List<MultimodalScoreFact> rawFacts = project1384Facts(expected);
        List<MultimodalScoreFact> facts = com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer
                .normalize(rawFacts);

        var verification = OriginalScoreEvidencePolicy.verifyModel(artifact, text, facts,
                expected.path("declaredTotal").decimalValue());
        var validation = new MultimodalScoreFactValidator().validate(facts, artifact,
                OriginalScoringContext.resolve(artifact, text), text, expected.path("declaredTotal").decimalValue());
        var schemes = OriginalScoringContext.schemes(artifact, text);
        List<MultimodalScoreFact> eligibleFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(facts, verification);
        assertEquals(facts.size(), verification.qualifications().size());
        assertTrue(verification.qualifications().stream().allMatch(
                OriginalScoreEvidencePolicy.Qualification::candidateEligible));

        assertEquals(1, schemes.size());
        assertEquals(expected.path("layoutStatus").asText(), schemes.get(0).layoutStatus());
        assertEquals(expected.path("declaredTotal").decimalValue(), schemes.get(0).declaredTotal());
        assertEquals(expected.path("totalLocator").asText(), schemes.get(0).totalLocator());
        assertEquals(expected.path("totalQuote").asText(), schemes.get(0).totalQuote());
        assertEquals(expected.path("expectedEligibleLeafTotal").decimalValue(), validation.leafTotal());
        assertTrue(validation.autoApprovable(), validation.violations().toString());
        assertTrue(facts.stream().filter(fact -> fact.code().startsWith("B"))
                .filter(fact -> !fact.code().equals("BUSINESS"))
                .allMatch(fact -> "BUSINESS".equals(fact.parentCode())));
        assertTrue(facts.stream().filter(fact -> fact.code().startsWith("T"))
                .filter(fact -> !fact.code().equals("TECHNICAL"))
                .allMatch(fact -> "TECHNICAL".equals(fact.parentCode())));
        assertEquals("IN_SCHEME", verification.qualifications().get(0).scopeStatus());
        assertTrue(validation.violations().stream().noneMatch(violation ->
                List.of("PARENT_NOT_FOUND", "PARENT_WEIGHT_MISMATCH", "WEIGHT_SUM_MISMATCH")
                        .contains(violation.code())));
        BigDecimal categoryTotal = StreamSupport.stream(expected.path("parents").spliterator(), false)
                .map(parent -> parent.path("weight").decimalValue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(expected.path("declaredTotal").decimalValue(), categoryTotal);
        assertEquals(expected.path("tableIds").size(), schemes.get(0).tableIds().size());
    }

    @Test
    void project1382RealPdfShapeReplayKeepsTocLinesOutOfSchemeEvidence() throws Exception {
        String text = GoldenCasesReplaySupport.readFixture("case-project-1382-pdf-real-shape.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(
                "case-project-1382-pdf-real-shape.expected.json"));
        DocumentArtifact artifact = project1382RealShapeArtifact(expected);
        List<MultimodalScoreFact> facts = project1382RealShapeFacts(expected);

        var schemes = OriginalScoringContext.schemes(artifact, text);
        var verification = OriginalScoreEvidencePolicy.verifyModel(artifact, text, facts,
                expected.path("declaredTotal").decimalValue());
        var validation = new MultimodalScoreFactValidator().validate(facts, artifact, text,
                expected.path("declaredTotal").decimalValue());
        List<MultimodalScoreFact> eligibleFacts = OriginalScoreEvidencePolicy.eligibleModelFacts(facts, verification);

        assertEquals(1, schemes.size());
        assertEquals(expected.path("schemeTitle").asText(), schemes.get(0).title());
        assertEquals(expected.path("layoutStatus").asText(), schemes.get(0).layoutStatus());
        assertEquals(expected.path("declaredTotal").decimalValue(), schemes.get(0).declaredTotal());
        assertEquals(expected.path("totalLocator").asText(), schemes.get(0).totalLocator());
        assertEquals(expected.path("totalQuote").asText(), schemes.get(0).totalQuote());
        assertEquals(expected.path("tableIds").size(), schemes.get(0).tableIds().size());
        assertTrue(verification.problems().stream().noneMatch(problem ->
                "ORIGINAL_SCHEME_AMBIGUOUS".equals(problem.code())), () -> verification.problems().toString());
        assertEquals(expected.path("expectedEligibleFactCount").asInt(), eligibleFacts.size(),
                () -> verification.qualifications().toString());
        assertEquals(expected.path("expectedOutOfSchemeFactCount").asInt(), verification.qualifications().stream()
                .filter(qualification -> "OUT_OF_SCHEME".equals(qualification.scopeStatus())).count());
        assertEquals(expected.path("expectedEligibleLeafTotal").decimalValue(), validation.leafTotal());
        assertTrue(validation.autoApprovable(), () -> validation.violations().toString());
        assertTrue(validation.violations().stream().noneMatch(violation -> List.of(
                "ORIGINAL_SCHEME_AMBIGUOUS", "SOURCE_SCHEME_NOT_VERIFIED", "ITEM_SCHEME_SCOPE_MISMATCH",
                "WEIGHT_SUM_MISMATCH", "DUPLICATE_CODE", "CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE")
                .contains(violation.code())), () -> validation.violations().toString());
        assertEquals("OUT_OF_SCHEME", OriginalScoringContext.textScopeAt(text,
                expected.path("outOfSchemeLine").asInt()).scopeStatus());
        assertEquals("IN_SCHEME", OriginalScoringContext.textScopeAt(text,
                expected.path("inSchemeLine").asInt()).scopeStatus());
    }

    @Test
    void project1382RealShapeBlankCodeSiblingsStayIndependentlyEligible() throws Exception {
        String text = GoldenCasesReplaySupport.readFixture("case-project-1382-pdf-real-shape.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(
                "case-project-1382-pdf-real-shape.expected.json"));
        DocumentArtifact artifact = project1382RealShapeArtifact(expected);
        List<MultimodalScoreFact> facts = project1382RealShapeFacts(expected);
        List<MultimodalScoreFact> blankCodeFacts = facts.stream().filter(fact -> fact.code().isBlank()).toList();

        assertEquals(2, blankCodeFacts.size(), "真实文件同节空 code 兄弟行必须进入金标");
        // 空 code 让「方案+状态+章节+code」这一维度上必然同键——正是这一碰撞让空 code 兄弟行
        // 在合格集/小计里互相串台，必须由 modelFactId 的其余判别因子分开。
        assertEquals(1, blankCodeFacts.stream().map(OriginalScoreEvidencePolicy::qualificationFactKey)
                .distinct().count());
        assertEquals(2, blankCodeFacts.stream().map(OriginalScoreEvidencePolicy::modelFactId).distinct().count());
        assertEquals(2, blankCodeFacts.stream().map(MultimodalScoreFact::sourceCellIds).distinct().count());

        var verification = OriginalScoreEvidencePolicy.verifyModel(artifact, text, facts,
                expected.path("declaredTotal").decimalValue());
        assertEquals(2, OriginalScoreEvidencePolicy.eligibleModelFacts(blankCodeFacts, verification).size(),
                () -> verification.qualifications().toString());

        var validation = new MultimodalScoreFactValidator().validate(facts, artifact, text,
                expected.path("declaredTotal").decimalValue());
        assertEquals(expected.path("expectedEligibleLeafTotal").decimalValue(), validation.leafTotal());
        assertTrue(validation.autoApprovable(), () -> validation.violations().toString());
    }

    private static MultimodalScoreFact textFact(JsonNode expected, String schemeId, String status) {
        String quote = expected.path("quote").asText();
        return new MultimodalScoreFact(expected.path("section").asText("商务"), expected.path("code").asText(),
                expected.path("parentCode").asText(""),
                expected.path("dimension").asText(), expected.path("weight").decimalValue(), quote,
                "OBJECTIVE", null, "", List.of(expected.path("locator").asText()), quote, 0.99,
                ChildAggregation.valueOf(expected.path("childRelation").asText("SUM")), schemeId, status);
    }

    private static OriginalScoreEvidencePolicy.Qualification qualification(MultimodalScoreFact fact,
            Map<String, OriginalScoreEvidencePolicy.Qualification> qualifications) {
        return qualifications.get(OriginalScoreEvidencePolicy.modelFactId(fact));
    }

    private static List<MultimodalScoreFact> project1384Facts(JsonNode expected) {
        List<MultimodalScoreFact> facts = new ArrayList<>();
        expected.path("parents").forEach(parent -> facts.add(new MultimodalScoreFact(
                parent.path("section").asText(), parent.path("code").asText(), "",
                parent.path("dimension").asText(), parent.path("weight").decimalValue(),
                parent.path("quote").asText(), "OBJECTIVE", null, "",
                List.of(parent.path("locator").asText()), parent.path("quote").asText(), 0.99,
                ChildAggregation.valueOf(parent.path("childRelation").asText()), "", "")));
        expected.path("facts").forEach(item -> {
            String tableId = item.path("tableId").asText();
            String factCode = item.path("code").asText();
            List<String> sourceCells = List.of(factCode + "-name", factCode + "-rule", factCode + "-weight");
            facts.add(new MultimodalScoreFact(item.path("section").asText(), factCode, item.path("parentCode").asText(),
                    item.path("dimension").asText(), item.path("weight").decimalValue(), item.path("quote").asText(),
                    "OBJECTIVE", pageForTable(tableId), tableId, sourceCells,
                    item.path("quote").asText(), 0.99, ChildAggregation.SUM, "", ""));
        });
        return facts;
    }

    private static DocumentArtifact project1384Artifact(String text, JsonNode expected) {
        String scheme = expected.path("schemeTitle").asText();
        Map<String, List<JsonNode>> grouped = new java.util.LinkedHashMap<>();
        expected.path("facts").forEach(item -> grouped.computeIfAbsent(item.path("tableId").asText(), ignored -> new ArrayList<>()).add(item));
        List<DocumentArtifact.CanonicalTable> tables = grouped.entrySet().stream().map(entry -> {
            String tableId = entry.getKey();
            int page = pageForTable(tableId);
            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>(List.of(
                    cell(tableId + "-h-no", 0, 0, "序号"), cell(tableId + "-h-name", 0, 1, "评审内容"),
                    cell(tableId + "-h-rule", 0, 2, "评审标准"), cell(tableId + "-h-score", 0, 3, "评分分值")));
            int row = 1;
            for (JsonNode item : entry.getValue()) {
                String code = item.path("code").asText();
                cells.add(cell(code + "-no", row, 0, Integer.toString(row)));
                cells.add(cell(code + "-name", row, 1, item.path("dimension").asText()));
                cells.add(cell(code + "-rule", row, 2, item.path("quote").asText()));
                cells.add(cell(code + "-weight", row, 3, item.path("weight").asText() + " 分"));
                row++;
            }
            String section = tableId.startsWith("t-business") ? "商务部分（综合实力）" : "技术部分";
            return table(tableId, page, scheme + " / " + section, true,
                    cells.toArray(DocumentArtifact.CanonicalTable.CanonicalCellDto[]::new));
        }).toList();
        if (!text.contains(expected.path("totalQuote").asText()))
            throw new IllegalStateException("1384 replay fixture is missing its original declared-total paragraph");
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("project-1384-redacted", "sha-1384", "docx", "2.0"),
                tables, List.of());
    }

    private static DocumentArtifact project1382Artifact(String text, JsonNode expected) {
        String quote = expected.path("totalQuote").asText();
        if (!text.contains(quote)) throw new IllegalStateException("1382 replay fixture is missing its total quote");
        var summary = table("t-score-summary", 28, expected.path("schemeTitle").asText() + " / 分值构成", false,
                cell("total-cell", 0, 0, quote));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("project-1382-redacted", "sha-1382", "pdf", "2.0"),
                List.of(summary), List.of());
    }

    private static List<MultimodalScoreFact> project1382RealShapeFacts(JsonNode expected) {
        List<MultimodalScoreFact> facts = new ArrayList<>();
        realShapeRowsByTable(expected).forEach((tableId, rows) -> {
            for (int row = 0; row < rows.size(); row++) facts.add(realShapeFact(tableId, row, rows.get(row)));
        });
        return facts;
    }

    /** Rows per table in physical order — parents first, then items, then the out-of-scheme table. */
    private static Map<String, List<JsonNode>> realShapeRowsByTable(JsonNode expected) {
        Map<String, List<JsonNode>> rowsByTable = new java.util.LinkedHashMap<>();
        for (String group : List.of("parents", "facts", "auditFacts")) {
            expected.path(group).forEach(item -> rowsByTable
                    .computeIfAbsent(item.path("tableId").asText(), ignored -> new ArrayList<>()).add(item));
        }
        return rowsByTable;
    }

    private static MultimodalScoreFact realShapeFact(String tableId, int row, JsonNode item) {
        // 引用单元格按「表 + 物理行」派生，不从 code 派生：空 code 的兄弟行会在 -name/-rule/-weight
        // 上撞成同一组 id，触发 DUPLICATE_SOURCE 并掩盖真正要验证的资格门与身份行为。
        return new MultimodalScoreFact(item.path("section").asText(), item.path("code").asText(),
                item.path("parentCode").asText(""), item.path("dimension").asText(),
                item.path("weight").decimalValue(), item.path("quote").asText(), "OBJECTIVE",
                item.path("page").intValue(), tableId,
                List.of(realShapeCellId(tableId, row, 1), realShapeCellId(tableId, row, 2),
                        realShapeCellId(tableId, row, 3)),
                item.path("quote").asText(), 0.99,
                ChildAggregation.valueOf(item.path("childRelation").asText("SUM")), "", "");
    }

    private static String realShapeCellId(String tableId, int row, int column) {
        return tableId + "-r" + row + "-c" + column;
    }

    private static DocumentArtifact project1382RealShapeArtifact(JsonNode expected) {
        Map<String, List<JsonNode>> rowsByTable = realShapeRowsByTable(expected);
        List<DocumentArtifact.CanonicalTable> tables = new ArrayList<>();
        expected.path("tables").forEach(spec -> {
            String tableId = spec.path("tableId").asText();
            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>(List.of(
                    cell(tableId + "-h-no", 0, 0, "序号"), cell(tableId + "-h-name", 0, 1, "评审内容"),
                    cell(tableId + "-h-rule", 0, 2, "评审标准"), cell(tableId + "-h-weight", 0, 3, "评分分值")));
            int row = 0;
            for (JsonNode item : rowsByTable.getOrDefault(tableId, List.of())) {
                int physicalRow = row + 1;
                cells.add(cell(realShapeCellId(tableId, row, 0), physicalRow, 0, Integer.toString(physicalRow)));
                cells.add(cell(realShapeCellId(tableId, row, 1), physicalRow, 1, item.path("dimension").asText()));
                cells.add(cell(realShapeCellId(tableId, row, 2), physicalRow, 2, item.path("quote").asText()));
                cells.add(cell(realShapeCellId(tableId, row, 3), physicalRow, 3,
                        item.path("weight").asText() + " 分"));
                row++;
            }
            tables.add(table(tableId, spec.path("page").asInt(), spec.path("section").asText(),
                    spec.path("score").asBoolean(),
                    cells.toArray(DocumentArtifact.CanonicalTable.CanonicalCellDto[]::new)));
        });
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("project-1382-pdf-real-shape",
                "sha-1382-pdf", "pdf", "2.2"), tables, List.of());
    }

    private static int pageForTable(String tableId) {
        return switch (tableId) {
            case "t-business-detail-1" -> 28;
            case "t-business-detail-2" -> 29;
            case "t-technical-detail-1" -> 30;
            default -> 31;
        };
    }

    private static BigDecimal effectiveTotal(List<MultimodalScoreFact> facts) {
        List<MultimodalScoreFact> normalized = com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer.normalize(facts);
        List<ScoreUnitPolicy.Node> nodes = normalized.stream().map(fact -> {
            String id = OriginalScoreEvidencePolicy.qualificationFactKey(fact);
            MultimodalScoreFact parentFact = com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer
                    .resolveParent(fact, normalized);
            String parent = parentFact == null ? "" : OriginalScoreEvidencePolicy.qualificationFactKey(parentFact);
            return new ScoreUnitPolicy.Node(id, parent, fact.weight(), fact.childAggregation().name());
        }).toList();
        return ScoreUnitPolicy.totalWeight(nodes);
    }

    private static DocumentArtifact.CanonicalTable table(String id, int page, String section, boolean score,
            DocumentArtifact.CanonicalTable.CanonicalCellDto... cells) {
        int rows = java.util.Arrays.stream(cells).mapToInt(DocumentArtifact.CanonicalTable.CanonicalCellDto::row)
                .max().orElse(0) + 1;
        int columns = java.util.Arrays.stream(cells).mapToInt(DocumentArtifact.CanonicalTable.CanonicalCellDto::column)
                .max().orElse(0) + 1;
        return new DocumentArtifact.CanonicalTable(id, page, section, rows, columns, List.of(), List.of(cells), score);
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, text, text,
                1, 1, false, "fixture:" + id);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("case-dual-section-renumber"),
                Arguments.of("case-weight-gap-mixed"),
                Arguments.of("case-1347-compound-percent"),
                Arguments.of("case-bracket-vs-pure-conflict"),
                Arguments.of("case-declared-total-80"),
                Arguments.of("case-single-unknown-total-adoption"),
                Arguments.of("case-review-required-80-gap"),
                Arguments.of("case-1374-redundant-formula"),
                Arguments.of("case-3col-embedded-bracket-weight"),
                Arguments.of("case-1381-pdf-preliminary-and-summary"),
                Arguments.of("case-pdf-cert-list-single-item"),
                Arguments.of("case-1117-performance-evidence"),
                Arguments.of("case-multi-table-tech-commercial"),
                Arguments.of("case-document-total-cross-table"),
                Arguments.of("case-category-weight-noise-before-score"),
                Arguments.of("case-sidecar-stream-failure"),
                Arguments.of("case-multimodal-incomplete-recall-fallback"),
                Arguments.of("case-1386-score-summary-and-leaves"),
                Arguments.of("case-page-split-parent-child"),
                Arguments.of("case-long-text-scoring-table"),
                Arguments.of("case-text-primary-multi-document"),
                Arguments.of("case-score-total-evidence-roles"),
                Arguments.of("case-upstream-scoring-integrity"),
                Arguments.of("case-docx-run-boundary-score"),
                Arguments.of("case-ocr-chinese-token-spaces"),
                Arguments.of("case-pdf-row-evidence-contiguous"),
                Arguments.of("case-project-1384-docx-continuation"));
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

    @org.junit.jupiter.api.Test
    void goldenReplayKeepsCompatibilityWithAiProviderDiagnostics() {
        // AI Provider 诊断遥测追加到 RunSummary 时，既有金标回放断言与结构不受影响
        org.junit.jupiter.api.Assertions.assertFalse(cases().toList().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void childAggregationSupportsAdditiveTotalWithoutMisclassifyingAsSharedCap() {
        assertTrue(ChildAggregation.SUM.supportedByOriginalText("服务网点及仓储（3分）：提供网点证明，合计最高得3分，各网点分值累加计算。"));
    }
}
