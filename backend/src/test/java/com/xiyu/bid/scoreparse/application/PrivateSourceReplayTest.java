package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.UnknownDeclaredTotalReviewPolicy;
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
import com.xiyu.bid.scoreparse.domain.ImageAssignmentPlan;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional full private-source replay; mandatory portable extracted excerpts live in other tests. */
class PrivateSourceReplayTest {
    @Test void replayActualPersistedQuotesAndCurrentPdfArtifact() throws Exception {
        Path directory = Path.of("../.runtime/root-cause/1382-v2");
        assumeTrue(Files.exists(directory.resolve("logical-artifact.json")), "private source is local only");
        ObjectMapper json = new ObjectMapper();
        var artifact = SourceArtifactBundle.combine(List.of(json.readValue(
                Files.readString(directory.resolve("logical-artifact.json")), DocumentArtifact.class)));
        List<JsonNode> nodes = Files.readAllLines(directory.resolve("task274-facts.jsonl")).stream()
                .map(line -> { try { return json.readTree(line); } catch (Exception e) { throw new IllegalStateException(e); } }).toList();
        List<MultimodalScoreFact> facts = new ArrayList<>();
        for (var node : nodes.subList(0, 16)) {
            String parentId = node.path("parentFactId").asText();
            String parent = nodes.stream().filter(n -> n.path("factId").asText().equals(parentId))
                    .map(n -> n.path("code").asText()).findFirst().orElse("");
            List<String> ids = new ArrayList<>(); node.path("sourceCellIds").forEach(n -> ids.add(n.asText()));
            facts.add(new MultimodalScoreFact("第三章 评审办法", node.path("code").asText(), parent,
                    node.path("dimension").asText(), node.path("weight").decimalValue(), node.path("sourceText").asText(),
                    "SUBJECTIVE", null, node.path("sourceTableId").asText(), ids,
                    node.path("weightEvidence").path("quotedText").asText(), .9, ChildAggregation.SUM));
        }
        var bound = TextPrimaryEvidenceBinder.bind(facts, artifact, Files.readString(directory.resolve("source-local.md")));
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, new BigDecimal("100"));
        for (var fact : bound) {
            var id = OriginalScoreEvidencePolicy.modelFactId(fact);
            System.out.println("REPLAY " + fact.code() + " | " + fact.tableId() + " | " + fact.sourceCellIds()
                    + " | " + validation.violations().stream().filter(v -> v.factId().equals(id)).map(v -> v.code()).toList());
        }
        assertThat(bound).hasSize(16);
        var leaves = bound.stream().filter(f -> !f.parentCode().isBlank()).toList();
        assertThat(leaves).hasSize(13);
        assertThat(leaves.stream().map(MultimodalScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
        for (var expected : java.util.Map.of("商务部分", "15", "技术部分", "45", "报价", "40").entrySet()) {
            assertThat(leaves.stream().filter(f -> f.parentCode().equals(expected.getKey())).map(MultimodalScoreFact::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(expected.getValue());
        }

        assertThat(bound.stream().filter(f -> f.code().contains("物流仓储")).findFirst().orElseThrow().sourceCellIds()).isEmpty();
        var logistics = facts.stream().filter(f -> f.code().contains("物流仓储")).findFirst().orElseThrow();
        var anchors = SourceAnchorCatalog.build(artifact);
        var anchor = anchors.stream().filter(a -> EvidenceText.normalize(a.rawText()).text().contains(logistics.code()))
                .findFirst().orElseThrow();
        var anchoredInput = new MultimodalScoreFact(logistics.sectionTitle(), logistics.code(), logistics.parentCode(),
                logistics.code(), logistics.weight(), logistics.ruleText(), logistics.scoreType(), logistics.pageNumber(),
                "", List.of(anchor.id()), logistics.quotedText(), logistics.confidence(), logistics.childAggregation());
        var restored = SourceAnchorCatalog.restore(anchoredInput, anchors);
        assertThat(restored.sourceMetadata().sourceSpans().stream().map(SourceSpan::pageNumber).distinct()).hasSize(2);
        assertThat(restored.quotedText()).contains("配送响应速度能力");
        assertThat(restored.sourceMetadata().modelQuotedText()).isEqualTo(logistics.quotedText());
        assertThat(new MultimodalScoreFactValidator().validate(List.of(restored), artifact, new BigDecimal("100"))
                .violations()).noneMatch(v -> v.code().equals("QUOTE_NOT_VERBATIM") || v.code().equals("SOURCE_LOCATOR_INVALID"));

    }
}
