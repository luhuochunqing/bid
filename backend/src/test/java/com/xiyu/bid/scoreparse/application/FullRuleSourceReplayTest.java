package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.ScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreAssessmentGuard;
import com.xiyu.bid.scoreparse.domain.ScoreItemChapterMatch;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreTableSectionPolicy;
import com.xiyu.bid.scoreparse.domain.StructuredTableCandidateExtractor;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.domain.ModelSourceClaim;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.ScoreVersionFingerprint;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.EvidenceText;
import com.xiyu.bid.scoreparse.domain.ScoreRangeGuard;
import com.xiyu.bid.scoreparse.domain.ScoreTableBlockLocator;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.AutoParseGate;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.TableSplitResult;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.domain.AiProviderDiagnostics;
import com.xiyu.bid.scoreparse.domain.ScoreItemOrderPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer;
import com.xiyu.bid.scoreparse.domain.OriginalScoreWeightEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringSchemePolicy;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.UnknownDeclaredTotalReviewPolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoreQualificationPolicy;
import com.xiyu.bid.scoreparse.domain.ItemCountCheck;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText;
import com.xiyu.bid.scoreparse.domain.ExtractedScoreItem;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.domain.ScoreTableSplitPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.SourceRecheckPlan;
import com.xiyu.bid.scoreparse.domain.ScoreMergeResultGuard;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableRowParser;
import com.xiyu.bid.scoreparse.domain.ScoreOriginalTextAligner;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.ScoreEvidenceClaim;
import com.xiyu.bid.scoreparse.domain.WeightOverloadTrimPolicy;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.SourceSpanResolver;
import com.xiyu.bid.scoreparse.domain.ScoreParseReviewSnapshot;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.ProjectEvidenceRequirementPolicy;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.DeductionItemPolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringTextScopePolicy;
import com.xiyu.bid.scoreparse.domain.ImageAssignmentPlan;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.assertThat;

/** FBR-I1: real DOCX -> Sidecar -> every binding route -> mapper -> immutable candidate. */
class FullRuleSourceReplayTest {
    static final String RULE = "方案编制合理，组织保障措施完整，最高3分。";
    static Stream<Arguments> routes() {
        return List.of("adjacent", "paragraph", "repeated-header", "same-table", "body-keywords",
                "independent-table", "independent-header", "independent-row").stream().flatMap(variant ->
                List.of("exact", "name-score", "prebound", "anchor").stream().map(route -> Arguments.of(variant, route)));
    }
    static ScoreFact mapped(String variant, String route) throws Exception {
        var artifact = CrossFormatDocumentSourceReplayTest.convert(variant);
        var claim = claim(route.equals("name-score") ? RULE.replaceFirst("，", "；") : "方案编制合理，");
        if (route.equals("prebound")) {
            var first = artifact.tables().getFirst().cells().stream().filter(c -> c.rawText().contains("方案编制合理")).findFirst().orElseThrow();
            var ids = artifact.tables().stream().flatMap(t -> t.cells().stream())
                    .filter(c -> first.logicalRowId().equals(c.logicalRowId()) && first.column().equals(c.column()))
                    .map(DocumentArtifact.CanonicalTable.CanonicalCellDto::cellId).toList();
            claim = copy(claim, artifact.tables().getFirst().tableId(), ids,
                    new ScoreSourceMetadata(RULE, claim.quotedText(), SourceSpanResolver.resolve(artifact, ids), List.of(),
                            claim.dimension(), claim.scoreType(), ModelSourceClaim.from(claim)));
        }
        if (route.equals("anchor")) {
            var anchor = SourceAnchorCatalog.build(artifact).stream().filter(a -> a.rawText().contains("方案编制合理")).findFirst().orElseThrow();
            claim = SourceAnchorCatalog.restore(copy(claim, "", List.of(anchor.id()), ScoreSourceMetadata.EMPTY), List.of(anchor));
        }
        var bound = route.equals("prebound") ? List.of(claim) : TextPrimaryEvidenceBinder.bind(List.of(claim), artifact, "");
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, null);
        return TextPrimaryScoreFactMapper.map(bound.getFirst(), artifact, validation);
    }
    static List<OriginalScoreEvidencePolicy.Qualification> knownTotal(ScoreFact fact) {
        return List.of(new OriginalScoreEvidencePolicy.Qualification(fact.factId(), "s", "IN_SCHEME", false,
                "SOURCE_UNVERIFIED", "", "第三章 评审标准", new BigDecimal("3"), "KNOWN", "总分3分", "t", "SINGLE_TABLE", List.of("t"), ChildAggregation.SUM));
    }
    static ScoreExtractionSnapshot snapshot(ScoreFact fact) {
        return ScoreExtractionSnapshot.from("full-rule", List.of(fact), knownTotal(fact), List.of());
    }
    @ParameterizedTest @MethodSource("routes")
    void ruleCompletenessComesFromSourceUnitAcrossEveryEntry(String variant, String route) throws Exception {
        var fact = mapped(variant, route);
        var item = snapshot(fact).items().getFirst();
        boolean closed = variant.equals("adjacent") || variant.startsWith("independent-");
        assertThat(item.ruleText().replace("\n", "")).as("%s/%s full rule", variant, route).isEqualTo(RULE);
        assertThat(item.computable()).isTrue();
        assertThat(fact.sourceMetadata().modelRuleText()).isEqualTo(RULE);
        if (closed) {
            assertThat(item.fieldSources().get("ruleText")).isEqualTo("SOURCE");
            assertThat(item.diagnostics()).doesNotContain("FULL_RULE_UNIT_UNRESOLVED");
            assertThat(fact.sourceMetadata().sourceSpans()).allSatisfy(span -> assertThat(span.locator()).startsWith("/w:document"));
            if (variant.equals("adjacent")) assertThat(fact.sourceMetadata().sourceSpans().stream().map(SourceSpan::rawText))
                    .anyMatch(raw -> raw.contains("组织保障措施完整"));
        } else {
            assertThat(item.fieldSources().get("ruleText")).isEqualTo("MODEL_CLAIM");
            assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
            assertThat(snapshot(fact).schemes().getFirst().extractedTotalComplete()).isFalse();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"exact", "anchor"})
    void missingXmlNeverCertifiesLogicalRuleEvenWhenQuoteMatches(String route) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = json.valueToTree(CrossFormatDocumentSourceReplayTest.convert("adjacent"));
        tree.path("tables").forEach(t -> t.path("cells").forEach(c -> ((com.fasterxml.jackson.databind.node.ObjectNode)c).put("sourceXmlPath", "")));
        var artifact = json.treeToValue(tree, DocumentArtifact.class);
        var model = claim("方案编制合理，");
        if (route.equals("anchor")) {
            var anchor = SourceAnchorCatalog.build(artifact).stream().filter(a -> a.rawText().contains("方案编制合理")).findFirst().orElseThrow();
            model = SourceAnchorCatalog.restore(copy(model, "", List.of(anchor.id()), ScoreSourceMetadata.EMPTY), List.of(anchor));
        }
        var bound = TextPrimaryEvidenceBinder.bind(List.of(model), artifact, "");
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, null);
        var item = snapshot(TextPrimaryScoreFactMapper.map(bound.getFirst(), artifact, validation)).items().getFirst();
        assertThat(item.ruleText()).isEqualTo(RULE);
        assertThat(item.fieldSources().get("ruleText")).isEqualTo("MODEL_CLAIM");
        assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
    }
    @Test void textQuoteDoesNotEraseFollowingNumberedMaterials() {
        String rule = "方案最高3分。\n1、须提供合同首页。\n2、须提供验收证明，缺少不得分。";
        var model = new MultimodalScoreFact("第三章 评审标准", "1", "", "实施方案", new BigDecimal("3"), rule,
                "SUBJECTIVE", null, "", List.of(), "方案最高3分。", .9).withScope(OriginalScoringContext.schemeId("第三章 评审标准"), "IN_SCHEME");
        var bound = TextPrimaryEvidenceBinder.bind(List.of(model), null, "## 第三章 评审标准\n" + rule);
        var validation = new MultimodalScoreFactValidator().validate(bound, null, null);
        var item = snapshot(TextPrimaryScoreFactMapper.map(bound.getFirst(), null, validation)).items().getFirst();
        assertThat(item.ruleText()).isEqualTo(rule);
        assertThat(item.fieldSources().get("ruleText")).isEqualTo("MODEL_CLAIM");
        assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        assertThat(item.computable()).isTrue();
        assertThat(item.fact().sourceMetadata().sourceSpans().getFirst().rawText()).isEqualTo("方案最高3分。");
    }
    @ParameterizedTest @ValueSource(strings = {"first", "continuation", "missing-page", "gap"})
    void pdfShortQuoteRestoresWholePhysicalContinuationFromEitherAnchor(String mode) {
        String first = "方案最高3分。\n1、须提供合同首页。";
        String second = "2、须提供验收证明，缺少不得分。";
        List<DocumentArtifact.CanonicalTable> tables = new ArrayList<>();
        for (int n = 0; n < 2; n++) {
            Integer page = mode.equals("missing-page") ? null : 2 + n * (mode.equals("gap") ? 2 : 1);
            String raw = n == 0 ? first : second;
            var cell = new DocumentArtifact.CanonicalTable.CanonicalCellDto("p" + n, 0, 0, raw, raw, 1, 1, false, "pdf:page=" + page, page, "unit");
            tables.add(new DocumentArtifact.CanonicalTable("t" + n, page, "第三章 评审标准", 1, 1, List.of(), List.of(cell), true, "PDF"));
        }
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("pdf", "hash", "PDF", "2.2"), tables, List.of());
        String quote = mode.equals("first") ? "方案最高3分。" : "2、须提供验收证明";
        var model = new MultimodalScoreFact("第三章 评审标准", "1", "", "实施方案", new BigDecimal("3"), first + "\n" + second,
                "SUBJECTIVE", null, "", List.of(), quote, .9).withScope(OriginalScoringContext.schemeId("第三章 评审标准"), "IN_SCHEME");
        var bound = TextPrimaryEvidenceBinder.bind(List.of(model), artifact, "");
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, null);
        var item = snapshot(TextPrimaryScoreFactMapper.map(bound.getFirst(), artifact, validation)).items().getFirst();
        assertThat(item.ruleText()).isEqualTo(first + "\n" + second);
        assertThat(item.computable()).isTrue();
        boolean proven = mode.equals("first") || mode.equals("continuation");
        assertThat(item.fieldSources().get("ruleText")).isEqualTo(proven ? "SOURCE" : "MODEL_CLAIM");
        if (proven) assertThat(item.fact().sourceMetadata().sourceSpans()).hasSize(2);
        else assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
    }
    @Test void unrelatedPreboundCellsAreNotBlanketConcatenatedIntoOneRule() {
        var a = new DocumentArtifact.CanonicalTable.CanonicalCellDto("a", 0, 0, "方案编制合理，", "方案编制合理，", 1, 1, false, "/w:document/a", null);
        var b = new DocumentArtifact.CanonicalTable.CanonicalCellDto("b", 1, 0, "其他项目：资格证书最高8分。", "其他项目：资格证书最高8分。", 1, 1, false, "/w:document/b", null);
        var table = new DocumentArtifact.CanonicalTable("t", null, "第三章 评审标准", 2, 1, List.of(), List.of(a, b), true, "DOCX");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "hash", "DOCX", "2.2"), List.of(table), List.of());
        var model = copy(claim("方案编制合理，"), "t", List.of("a", "b"), ScoreSourceMetadata.EMPTY);
        var validation = new MultimodalScoreFactValidator().validate(List.of(model), artifact, null);
        var item = snapshot(TextPrimaryScoreFactMapper.map(model, artifact, validation)).items().getFirst();
        assertThat(item.ruleText()).isEqualTo(RULE);
        assertThat(item.fieldSources().get("ruleText")).isEqualTo("MODEL_CLAIM");
        assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        assertThat(item.fact().sourceMetadata().sourceSpans()).hasSize(2);
    }
    @ParameterizedTest @CsvSource({"DOCX,exact", "DOCX,anchor", "PDF,exact", "PDF,anchor"})
    void missingLogicalMetadataCannotPromoteShortQuoteToCompleteSource(String type, String route) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = json.valueToTree(CrossFormatDocumentSourceReplayTest.convert("adjacent"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("artifact")).put("fileType", type);
        int page = 1;
        for (var table : tree.path("tables")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode)table).put("sourcePage", page++);
            table.path("cells").forEach(c -> ((com.fasterxml.jackson.databind.node.ObjectNode)c).put("logicalRowId", ""));
        }
        var artifact = json.treeToValue(tree, DocumentArtifact.class);
        var model = claim("方案编制合理，");
        if (route.equals("anchor")) {
            var anchor = SourceAnchorCatalog.build(artifact).stream().filter(a -> a.rawText().contains("方案编制合理")).findFirst().orElseThrow();
            model = SourceAnchorCatalog.restore(copy(model, "", List.of(anchor.id()), ScoreSourceMetadata.EMPTY), List.of(anchor));
        }
        var bound = TextPrimaryEvidenceBinder.bind(List.of(model), artifact, "");
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, null);
        var item = snapshot(TextPrimaryScoreFactMapper.map(bound.getFirst(), artifact, validation)).items().getFirst();
        assertThat(item.ruleText()).isEqualTo(RULE);
        assertThat(item.fieldSources().get("ruleText")).isEqualTo("MODEL_CLAIM");
        assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        assertThat(item.computable()).isTrue();
    }
    static MultimodalScoreFact claim(String quote) {
        return new MultimodalScoreFact("第三章 评审标准", "1", "", "实施方案", new BigDecimal("3"), RULE,
                "SUBJECTIVE", null, "", List.of(), quote, .9).withScope(OriginalScoringContext.schemeId("第三章 评审标准"), "IN_SCHEME");
    }
    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false"})
    void realPdfDisjointCellsNeedSiblingBoundariesBeforeExpandingLogicalRow(boolean identified, boolean reverse) throws Exception {
        var directory = java.nio.file.Path.of("../.runtime/root-cause/1382-v2");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(directory.resolve("logical-artifact.json")),
                "private source is local only");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var artifact = SourceArtifactBundle.combine(List.of(json.readValue(
                java.nio.file.Files.readString(directory.resolve("logical-artifact.json")), DocumentArtifact.class)));
        List<MultimodalScoreFact> models = new ArrayList<>();
        for (var line : java.nio.file.Files.readAllLines(directory.resolve("task274-facts.jsonl"))) {
            var node = json.readTree(line);
            if (!List.of("一级集采商品", "非一级集采商品").contains(node.path("code").asText())) continue;
            String complete = node.path("weightEvidence").path("quotedText").asText();
            String code = identified ? node.path("code").asText() : "未标识子项" + models.size();
            models.add(new MultimodalScoreFact("第三章 评审办法", code, "报价", "价格",
                    node.path("weight").decimalValue(), complete, "SUBJECTIVE", null, "", List.of(),
                    complete.lines().findFirst().orElseThrow(), .9));
        }
        assertThat(models).hasSize(2);
        if (reverse) Collections.reverse(models);
        var bound = TextPrimaryEvidenceBinder.bind(models, artifact, "");
        assertThat(bound.getFirst().sourceCellIds()).hasSize(1)
                .doesNotContainAnyElementsOf(bound.getLast().sourceCellIds());
        var firstUnit = LogicalRuleSourceUnit.resolve(artifact, bound.getFirst());
        var secondUnit = LogicalRuleSourceUnit.resolve(artifact, bound.getLast());
        assertThat(firstUnit.closed()).isTrue();
        assertThat(secondUnit.spans()).isEqualTo(firstUnit.spans());
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, new BigDecimal("40"));
        var facts = bound.stream().map(f -> TextPrimaryScoreFactMapper.map(f, artifact, validation)).toList();
        var qualifications = facts.stream().map(f -> new OriginalScoreEvidencePolicy.Qualification(f.factId(), "s", "IN_SCHEME", false,
                "SOURCE_UNVERIFIED", "", "第三章 评审办法", new BigDecimal("40"), "KNOWN", "总分40分", "t", "SINGLE_TABLE", List.of("t"), ChildAggregation.SUM)).toList();
        var snapshot = ScoreExtractionSnapshot.from("disjoint-real-pdf", facts, qualifications, List.of());
        for (var item : snapshot.items()) {
            var model = models.stream().filter(m -> m.code().equals(item.fact().code())).findFirst().orElseThrow();
            assertThat(item.ruleText()).isEqualTo(model.ruleText());
            assertThat(item.computable()).isTrue();
            assertThat(item.fieldSources().get("ruleText")).isEqualTo(identified ? "SOURCE" : "MODEL_CLAIM");
            if (identified) assertThat(item.diagnostics()).doesNotContain("FULL_RULE_UNIT_UNRESOLVED");
            else assertThat(item.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        }
        if (!identified) assertThat(snapshot.schemes().getFirst().extractedTotalComplete()).isFalse();
    }
    private static MultimodalScoreFact copy(MultimodalScoreFact f, String table, List<String> ids, ScoreSourceMetadata metadata) {
        return new MultimodalScoreFact(f.sectionTitle(), f.code(), f.parentCode(), f.dimension(), f.weight(), f.ruleText(),
                f.scoreType(), f.pageNumber(), table, ids, f.quotedText(), f.confidence(), f.childAggregation(), f.schemeId(), f.scopeStatus(), metadata);
    }
}
