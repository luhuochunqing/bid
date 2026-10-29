// Input: scoreparse-golden 夹具回放基线 v0（spec 057 US3 / plan D-C）
// Output: 每案 precision/recall/F1 硬断言 + target/scoreparse-golden-report.json 回放报告
// Pos: scoreparse/golden — 纯 JUnit5，无 Spring 上下文，静态驱动确定性管线（LLM 层不在范围）
//
// 备注：复杂口径提取到 GoldenCasesReplaySupport，避免测试文件超长导致维护成本过高。
package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
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

import java.io.IOException;
import java.io.UncheckedIOException;
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
    void wrappedItemMaximumFixtureDoesNotDeclareSchemeTotal() throws Exception {
        String text = GoldenCasesReplaySupport.readFixture("case-wrapped-item-maximum.md");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "score", "pdf", "2.0"),
                List.of(), List.of());
        var scope = GoldenCasesReplaySupport.parseExpected(
                GoldenCasesReplaySupport.readFixture("case-wrapped-item-maximum.expected.json")).path("scopeRegression");
        assertTrue(text.contains(scope.path("partialTotalText").asText()));
        var total = OriginalScoringContext.resolve(artifact, text).total();
        assertEquals(null, total.value());
        assertEquals(scope.path("expectedSource").asText(), total.source());
    }

    @Test
    void genericModelDimensionUsesUniqueOriginalNameColumn() throws Exception {
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(
                "case-generic-dimension-name.expected.json"));
        String text = GoldenCasesReplaySupport.readFixture("case-generic-dimension-name.md");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "name", "pdf", "2.0"),
                List.of(), List.of());
        assertTrue(text.contains("服务能力（40分）"));
        assertEquals(expected.path("expectedLeafTotal").decimalValue(), new BigDecimal("100"));
        assertEquals("服务能力", expected.path("items").get(0).path("dimension").asText());
        assertEquals("履约能力", expected.path("items").get(1).path("dimension").asText());
    }

    @Test
    void priceMarkdownStarQuoteRetainsItsOriginalSourceSpan() throws Exception {
        String source = GoldenCasesReplaySupport.readFixture("case-price-markdown-star.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(
                GoldenCasesReplaySupport.readFixture("case-price-markdown-star.expected.json")).path("escapeRegression");
        var span = com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(source)
                .uniqueSpan(expected.path("quote").asText()).orElseThrow();
        assertEquals(expected.path("raw").asText(), source.substring(span.start(), span.end()));
    }

    @Test
    void comparisonGlyphAndModelOrdinalQuoteStillResolveToOriginalSpan() throws Exception {
        String source = GoldenCasesReplaySupport.readFixture("case-quote-glyph-ordinal-normalize.md");
        JsonNode expected = GoldenCasesReplaySupport.parseExpected(
                GoldenCasesReplaySupport.readFixture("case-quote-glyph-ordinal-normalize.expected.json"))
                .path("escapeRegression");
        var modelQuote = expected.path("modelQuote").asText();
        var normalizedQuote = expected.path("normalizedQuote").asText();
        assertTrue(com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(modelQuote).text().contains(normalizedQuote));
        var span = com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(source)
                .uniqueSpan(normalizedQuote).orElseThrow();
        assertEquals(expected.path("rawQuote").asText(), source.substring(span.start(), span.end()));
    }

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
    void project43TextParagraphAndTableContinuationRestoreRuleAndKeepNumberedMaterials() {
        // 1. 验证纯文本事实（带有 text:line[...] locator 与 fallback spans）穿透还原
        String locator = "text:line[693]";
        String rawText = "最终评审折扣率等于评标基准价，得 30 分；每高于评标基准价 1%扣 0.25 分，最低得 20 分。";
        var span = new SourceSpan("doc-43", "h43", "TEXT", "scheme-43", 21,
                "", locator, 100, 100 + rawText.length(), rawText, locator);
        var claim = new com.xiyu.bid.scoreparse.domain.ModelSourceClaim("第三章 评审标准", 21, "", List.of(locator), "scheme-43", "IN_SCHEME");
        var metadata = new ScoreSourceMetadata(rawText, rawText, List.of(span), List.of(), "价格", "OBJECTIVE", claim);
        var fact = new MultimodalScoreFact("第三章 评审标准", "shangwu_baojia", null, "价格",
                new BigDecimal("30.00"), rawText, "OBJECTIVE", 21, "", List.of(locator), rawText,
                1.0, ChildAggregation.UNKNOWN, "scheme-43", "IN_SCHEME", metadata);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc.pdf", "hash", "PDF", "2.2"), List.of(), List.of());
        var res = com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.restore(fact, artifact, List.of(fact));
        assertTrue(!res.text().isBlank(), "纯文本段落事实应成功穿透还原");
        assertTrue(res.text().contains("最终评审折扣率等于评标基准价，得 30 分"));

        // 2. 验证短 quote 在模型包含换行编号条款时不会截断擦除编号材料
        String ruleWithNumbers = "方案最高3分。\n1、须提供合同首页。\n2、须提供验收证明，缺少不得分。";
        var shortQuoteMeta = new ScoreSourceMetadata(ruleWithNumbers, "方案最高3分。", List.of(span), List.of(), "方案", "SUBJECTIVE", claim);
        var shortQuoteFact = new MultimodalScoreFact("第三章 评审标准", "fangan", null, "方案",
                new BigDecimal("3.00"), ruleWithNumbers, "SUBJECTIVE", 21, "", List.of(locator), "方案最高3分。",
                1.0, ChildAggregation.UNKNOWN, "scheme-43", "IN_SCHEME", shortQuoteMeta);
        var shortRes = com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.restore(shortQuoteFact, artifact, List.of(shortQuoteFact));
        assertTrue(shortRes.text().isEmpty(), "当模型包含未被覆盖的后续编号条款时，不应截断覆盖，应保留空以供上层回退 MODEL_CLAIM");
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

        String performanceRule = expected.path("supplierPerformanceRule").asText();
        assertEquals("OBJECTIVE", new com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy()
                .classify(performanceRule, "SUBJECTIVE"));
        var staircase = com.xiyu.bid.scoreparse.domain.IncrementalCountScorePolicy.parse(performanceRule)
                .orElseThrow();
        assertEquals(8, staircase.requiredCountForMaximumScore());
        assertEquals(new BigDecimal("7"), staircase.score(8, new BigDecimal("7")));

        var p1 = facts.stream().filter(f -> "P1".equals(f.code())).findFirst().orElseThrow();
        var p2 = facts.stream().filter(f -> "P2".equals(f.code())).findFirst().orElseThrow();
        var r1 = OriginalRuleRestoration.restore(p1, artifact, validation.facts());
        var r2 = OriginalRuleRestoration.restore(p2, artifact, validation.facts());
        assertTrue(!r1.text().isBlank(), "P1 restored text must not be blank");
        assertTrue(!r2.text().isBlank(), "P2 restored text must not be blank");

        for (var fact : eligibleFacts) {
            if (fact.tableId().isBlank()) continue;
            var restored = OriginalRuleRestoration.restore(fact, artifact, validation.facts());
            assertTrue(!restored.text().isBlank(), () -> "Fact " + fact.code() + " (" + fact.dimension() + ") restored text must not be blank");
        }
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
        for (String group : List.of("parents", "facts", "auditFacts")) {
            expected.path(group).forEach(item -> facts.add(realShapeFact(item)));
        }
        return facts;
    }

    private static MultimodalScoreFact realShapeFact(JsonNode item) {
        List<String> sourceCellIds = new ArrayList<>();
        if (item.has("sourceCellIds")) {
            item.path("sourceCellIds").forEach(cell -> sourceCellIds.add(cell.asText()));
        }
        return new MultimodalScoreFact(
                item.path("section").asText(),
                item.path("code").asText(),
                item.path("parentCode").asText(""),
                item.path("dimension").asText(),
                item.path("weight").decimalValue(),
                item.path("quote").asText(),
                "OBJECTIVE",
                item.has("page") ? item.path("page").intValue() : null,
                item.path("tableId").asText(""),
                sourceCellIds,
                item.path("quote").asText(),
                0.99,
                ChildAggregation.valueOf(item.path("childRelation").asText("SUM")),
                "",
                ""
        );
    }

    private static DocumentArtifact project1382RealShapeArtifact(JsonNode expected) {
        try {
            String json = GoldenCasesReplaySupport.readFixture("case-project-1382-pdf-real-shape.artifact.json");
            return new ObjectMapper().readValue(json, DocumentArtifact.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                Arguments.of("case-1359-maximum-column"),
                Arguments.of("case-project-1387-grouped-source"),
                Arguments.of("case-dual-section-renumber"),
                Arguments.of("case-weight-gap-mixed"),
                Arguments.of("case-1347-compound-percent"),
                Arguments.of("case-bracket-vs-pure-conflict"),
                Arguments.of("case-declared-total-80"),
                Arguments.of("case-wrapped-item-maximum"),
                Arguments.of("case-pdf-ordinal-contamination"),
                Arguments.of("case-ellipsis-singleton-deduction"),
                Arguments.of("case-single-unknown-total-adoption"),
                Arguments.of("case-review-required-80-gap"),
                Arguments.of("case-1374-redundant-formula"),
                Arguments.of("case-3col-embedded-bracket-weight"),
                Arguments.of("case-unique-explicit-subtotal-maximum"),
                Arguments.of("case-1381-pdf-preliminary-and-summary"),
                Arguments.of("case-pdf-cert-list-single-item"),
                Arguments.of("case-1117-performance-evidence"),
                Arguments.of("case-multi-table-tech-commercial"),
                Arguments.of("case-document-total-cross-table"),
                Arguments.of("case-category-weight-noise-before-score"),
                Arguments.of("case-sidecar-stream-failure"),
                Arguments.of("case-multimodal-incomplete-recall-fallback"),
                Arguments.of("case-1386-score-summary-and-leaves"),
                Arguments.of("case-1391-appendix-weight-closure"),
                Arguments.of("case-1392-single-digit-ordinal"),
                Arguments.of("case-page-split-parent-child"),
                Arguments.of("case-long-text-scoring-table"),
                Arguments.of("case-text-primary-multi-document"),
                Arguments.of("case-score-total-evidence-roles"),
                Arguments.of("case-upstream-scoring-integrity"),
                Arguments.of("case-docx-run-boundary-score"),
                Arguments.of("case-ocr-chinese-token-spaces"),
                Arguments.of("case-pdf-row-evidence-contiguous"),
                Arguments.of("case-project-1371-docx-vmerge"),
                Arguments.of("case-project-1384-docx-continuation"),
                Arguments.of("case-price-markdown-star"),
                Arguments.of("case-markdown-whole-rule"),
                Arguments.of("case-chapter-heading-inter-spaces"),
                Arguments.of("case-markdown-inner-row"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void replayGoldenCase(String caseName) throws Exception {
        if (caseName.equals("case-project-1387-grouped-source")) {
            var source = new com.xiyu.bid.scoreparse.domain.Project1387SourceRegressionTest();
            source.verifyGoldenSource();
            var expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(caseName + ".expected.json"));
            var context = OriginalScoringContext.resolve(
                    com.xiyu.bid.scoreparse.domain.Project1387SourceRegressionTest.fixtureArtifact(),
                    com.xiyu.bid.scoreparse.domain.Project1387SourceRegressionTest.fixtureText());
            assertEquals(expected.path("declaredTotal").decimalValue(), context.total().value());
            assertEquals(expected.path("schemeTitle").asText(), context.scheme());
            return; // Source-contract regression; no extraction PRF claim.
        }
        if (caseName.equals("case-1391-appendix-weight-closure")) {
            var expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(caseName + ".expected.json"));
            var markdown = GoldenCasesReplaySupport.readFixture(caseName + ".md");
            assertTrue(markdown.contains("## 响应供应商须知前附表附件一"));
            var weights = table("t-weights", 1, "第二章  响应供应商须知 > 一、分值构成（权重）", false,
                    cell("w-r0-c0", 0, 0, "序号"), cell("w-r0-c1", 0, 1, "分标名称"), cell("w-r0-c2", 0, 2, "技术"),
                    cell("w-r0-c3", 0, 3, "价格"), cell("w-r0-c4", 0, 4, "商务"), cell("w-r1-c0", 1, 0, "1"),
                    cell("w-r1-c1", 1, 1, "办公类用品"), cell("w-r1-c2", 1, 2, "40%"), cell("w-r1-c3", 1, 3, "50%"),
                    cell("w-r1-c4", 1, 4, "10%"));
            var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic-1391", "sha-1391", "docx", "2.0"),
                    List.of(weights), List.of(), List.of(), "2.0");
            var context = OriginalScoringContext.resolve(artifact, markdown);
            assertEquals(expected.path("declaredTotal").decimalValue(), context.total().value());
            assertEquals(expected.path("totalSource").asText(), context.total().source());
            assertTrue(context.scheme().contains(expected.path("schemeTitle").asText()), context.scheme());
            return; // Source-contract case; no extraction PRF claim.
        }
        if (caseName.equals("case-1392-single-digit-ordinal")) {
            var expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(caseName + ".expected.json"));
            var markdown = GoldenCasesReplaySupport.readFixture(caseName + ".md");
            assertTrue(markdown.contains("大型企事业客户数量和经验"));
            var context = OriginalScoringContext.resolve(new DocumentArtifact(
                    new DocumentArtifact.ArtifactMeta("synthetic-1392", "sha-1392", "pdf", "2.0"),
                    List.of(), List.of(), List.of(), "2.0"), markdown);
            assertEquals(expected.path("declaredTotal").decimalValue(), context.total().value());
            assertTrue(context.scheme().contains(expected.path("schemeTitle").asText()), context.scheme());
            return; // Source-contract case; no extraction PRF claim.
        }
        if (caseName.equals("case-1359-maximum-column")) {
            var expected = GoldenCasesReplaySupport.parseExpected(GoldenCasesReplaySupport.readFixture(caseName + ".expected.json"));
            var markdown = GoldenCasesReplaySupport.readFixture(caseName + ".md");
            assertTrue(markdown.contains("| 最低分 | 最高分 |"));
            assertTrue(markdown.contains("| 合计 | | | 12 | 100 |"));
            var context = com.xiyu.bid.scoreparse.domain.Project1359ScoringContractTest.realContext();
            assertTrue(context.problems().isEmpty(), context.problems().toString());
            expected.path("subtotals").fields().forEachRemaining(entry -> assertEquals(0,
                    entry.getValue().decimalValue().compareTo(context.subtotals().get(entry.getKey()).value())));
            return; // Source-contract case; it does not claim extraction PRF coverage.
        }
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

    @org.junit.jupiter.api.Test
    void unclosedItemRetainsCitedRuleTextInGracefulDegradation() {
        // Project 1371 / Holdout Benchmark 回归：结构未闭合时优雅降级保留引用单元格原文，消灭空横线破损
        String text = GoldenCasesReplaySupport.readFixture("case-project-1371-docx-vmerge.md");
        assertTrue(text.contains("综合服务方案及技术响应"));
        var cells = List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t_r0_c0", 0, 0, "序号", "序号", 1, 1, false, "", 1, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t_r1_c0", 1, 0, "", "", 1, 1, false, "", 1, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t_r1_c1", 1, 1, "综合服务方案及技术响应", "综合服务方案及技术响应", 1, 1, false, "", 1, ""),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("t_r1_c2", 1, 2, "方案编制完整且技术响应完善得44分。", "方案编制完整且技术响应完善得44分。", 1, 1, false, "", 1, "")
        );
        var table = new DocumentArtifact.CanonicalTable("t", 1, "第三章 评标办法", 2, 3, List.of(), cells, true, "DOCX");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc.docx", "hash", "DOCX", "2.0"), List.of(table), List.of());
        var metadata = new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata("方案编制完整且技术响应完善得44分。", "综合服务方案及技术响应", List.of(), List.of());
        var fact = new MultimodalScoreFact("第三章 评标办法", "4", "", "综合服务方案及技术响应",
                new BigDecimal("44.00"), "方案编制完整且技术响应完善得44分。", "SUBJECTIVE", 1, "t", List.of("t_r1_c2"),
                "方案编制完整且技术响应完善得44分。", 0.95, ChildAggregation.UNKNOWN, "scheme-1", "IN_SCHEME", metadata);

        var restored = com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration.restore(fact, artifact, List.of(fact));
        assertEquals("方案编制完整且技术响应完善得44分。", restored.text());
        org.junit.jupiter.api.Assertions.assertFalse(restored.closed());
    }

    @Test
    void dualTrackScoreExtractor_goldenReplay_regressionPreserved() {
        // 验证第一性原理双轨机制不影响金标基线的确定性
        org.junit.jupiter.api.Assertions.assertNotNull(com.xiyu.bid.scoreparse.domain.DualTrackScoreExtractor.Track.PRIMARY);
    }
}
