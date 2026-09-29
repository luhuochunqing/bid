package com.xiyu.bid.scoreparse.application;

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
import java.nio.file.*;
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
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual DOCX bytes -> actual Sidecar -> bundle -> binder -> source qualification -> snapshot. */
class CrossFormatDocumentSourceReplayTest {
    private static final String RULE="方案编制合理，组织保障措施完整，最高3分。";
    static DocumentArtifact convert(String variant) throws Exception {
        Path sidecar=Path.of("../document-converter-sidecar").toAbsolutePath();
        Path file=Path.of("src/test/resources/scoreparse/docx-continuity/"+variant+"-score-language.docx").toAbsolutePath();
        String python = usableSidecarPython(sidecar);
        Path output=Files.createTempFile("docx-source-replay-", ".json");
        Process process=null;
        try {
            process=new ProcessBuilder(python,"-B","-c",
                    "import json,sys; from table_extractor import extract_tables_artifact; print(json.dumps(extract_tables_artifact(sys.argv[1]),ensure_ascii=False))",file.toString())
                    .directory(sidecar.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(output.toFile()).start();
            assertThat(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)).as("Sidecar conversion deadline").isTrue();
            assertThat(process.exitValue()).as("actual Sidecar DOCX conversion").isZero();
            return new ObjectMapper().readValue(Files.readString(output),DocumentArtifact.class);
        } finally {
            if(process!=null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }
    /**
     * 对齐 scripts/check-scoreparse-root-cause.sh：候选 SIDECAR_PYTHON → .venv/bin/python → python3，
     * 逐个真实验证 import docx,lxml，选中第一个可用的。存在但缺依赖的 venv 不再产生成片假红
     * （2026-09-29 教训：Task 291 排查被 8F+19E 环境污染红误导），全部不可用时给出可操作信息。
     */
    private static String usableSidecarPython(Path sidecar) {
        String env = System.getenv("SIDECAR_PYTHON");
        List<String> candidates = new ArrayList<>();
        if (env != null && !env.isBlank()) candidates.add(env);
        candidates.add(sidecar.resolve(".venv/bin/python").toString());
        candidates.add("python3");
        for (String candidate : candidates) {
            if (sidecarPythonUsable(candidate, sidecar)) return candidate;
        }
        throw new AssertionError("Sidecar 真实 DOCX 转换回放需要可用的 Python（import docx, lxml）。"
                + "候选均不可用: " + candidates + "。修复：python3 -m venv document-converter-sidecar/.venv "
                + "&& document-converter-sidecar/.venv/bin/pip install -r document-converter-sidecar/requirements.txt，"
                + "或 export SIDECAR_PYTHON=<可用的 python>");
    }
    private static boolean sidecarPythonUsable(String python, Path sidecar) {
        try {
            Process probe = new ProcessBuilder(python, "-B", "-c", "import docx, lxml")
                    .directory(sidecar.toFile()).redirectErrorStream(true).start();
            probe.getInputStream().readAllBytes();
            return probe.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }
    private static DocumentArtifact pdf() {
        var cell=new DocumentArtifact.CanonicalTable.CanonicalCellDto("p-r1-c1",1,1,"另项最高2分","另项最高2分",1,1,false,"pdf:page=1",1);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("sha256:other-pdf","other-pdf","PDF","2.2"),
                List.of(new DocumentArtifact.CanonicalTable("p",1,"另一个评分办法",2,2,List.of(),List.of(cell),true)),List.of());
    }
    private static MultimodalScoreFact claim(String quote, String rule) {
        return new MultimodalScoreFact("第三章 评审标准","1","","实施方案",new BigDecimal("3"),rule,"SUBJECTIVE",null,"",List.of(),quote,.9)
                .withScope(OriginalScoringContext.schemeId("第三章 评审标准"), "IN_SCHEME");
    }
    private static ScoreExtractionSnapshot replay(DocumentArtifact artifact) {
        return replay(artifact, RULE);
    }
    private static ScoreExtractionSnapshot replay(DocumentArtifact artifact, String quote) {
        return replay(artifact, quote, RULE);
    }
    private static ScoreExtractionSnapshot replay(DocumentArtifact artifact, String quote, String rule) {
        var bound=TextPrimaryEvidenceBinder.bind(List.of(claim(quote,rule)),artifact,"");
        var validation=new MultimodalScoreFactValidator().validate(bound,artifact,null);
        var fs=bound.stream().map(f -> TextPrimaryScoreFactMapper.map(f,artifact,validation)).toList();
        return ScoreExtractionSnapshot.from("reordered-task",fs,validation.sourceVerification().qualifications(),List.of());
    }
    @Test void adjacentDocxNullPagesQualifyInEitherMixedOrderAndKeepStableItemIdentity() throws Exception {
        var docx=convert("adjacent");
        for(String quote:List.of(RULE,RULE.replaceFirst("，","；"),RULE.replaceFirst("，","、"))) {
        var first=replay(SourceArtifactBundle.combine(List.of(pdf(),docx)),quote);
        var second=replay(SourceArtifactBundle.combine(List.of(docx,pdf())),quote);
        for(var snapshot:List.of(first,second)) {
            var item=snapshot.items().getFirst();
            assertThat(item.qualification().candidateEligible()).as("%s",item).isTrue();
            assertThat(item.ruleText().replace("\n", "")).isEqualTo(RULE);
            assertThat(item.fact().sourceMetadata().sourceSpans()).hasSize(2).allSatisfy(span -> {
                assertThat(span.fileType()).isEqualTo("DOCX");assertThat(span.pageNumber()).isNull();
                assertThat(span.locator()).startsWith("/w:document");
            });
        }
        assertThat(first.items().getFirst().itemId()).isEqualTo(second.items().getFirst().itemId());
        assertThat(first.items().getFirst().fact().sourceCellIds()).isNotEqualTo(second.items().getFirst().fact().sourceCellIds());
        }
    }
    @Test void missingDocxXmlCannotProveContinuation() throws Exception {
        var json=new ObjectMapper();
        var tree=json.valueToTree(convert("adjacent"));
        tree.path("tables").forEach(t -> t.path("cells").forEach(c -> ((com.fasterxml.jackson.databind.node.ObjectNode)c).put("sourceXmlPath", "")));
        var item=replay(SourceArtifactBundle.combine(List.of(json.treeToValue(tree,DocumentArtifact.class),pdf()))).items().getFirst();
        assertThat(item.qualification().candidateEligible()).isFalse();
        assertThat(item.rawMaximum()).isEqualByComparingTo("3");
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> brokenContinuations() {
        return List.of("paragraph","chapter","new-number","different-columns","repeated-header","same-table").stream().flatMap(variant ->
                List.of(RULE,RULE.replaceFirst("，","；"),RULE.replaceFirst("，","、")).stream().flatMap(quote ->
                        List.of(false,true).stream().map(reverse -> org.junit.jupiter.params.provider.Arguments.of(variant,quote,reverse))));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("brokenContinuations")
    void brokenContinuationCannotQualifyOrTruncateWithChangedModelPunctuation(String variant,String quote,boolean reverse) throws Exception {
        var docx=convert(variant);
        var item=replay(SourceArtifactBundle.combine(reverse?List.of(docx,pdf()):List.of(pdf(),docx)),quote).items().getFirst();
        assertThat(item.qualification().candidateEligible()).as("%s: %s",variant,item).isFalse();
        assertThat(item.fact().sourceCellIds()).isEmpty();
        assertThat(item.ruleText().replace("\n", "")).isEqualTo(RULE);
        assertThat(item.rawMaximum()).isEqualByComparingTo("3");
        assertThat(item.computable()).isTrue();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void addingHeaderKeywordsOnlyToContinuationProseCannotChangeStructuralRolesOrQualification(boolean reverse) throws Exception {
        var original = convert("repeated-header");
        var prose = convert("body-keywords");
        var json = new ObjectMapper();
        var expected = json.valueToTree(original.tables());
        var actual = json.valueToTree(prose.tables());
        // The only source change is text in the rule-body cell; physical and logical roles remain identical.
        expected.path(1).path("cells").forEach(cell -> {
            if (cell.path("row").asInt() == 1 && cell.path("column").asInt() == 2) {
                var body = (com.fasterxml.jackson.databind.node.ObjectNode)cell;
                String changedText = "组织保障措施完整，各评分项目应按编号提供资料，最高3分。";
                body.put("rawText", changedText);
                body.put("normalizedText", changedText);
            }
        });
        assertThat(actual).isEqualTo(expected);
        for (var docx : List.of(original, prose)) {
            String rule = docx == original ? RULE : RULE.replace("最高3分。", "各评分项目应按编号提供资料，最高3分。");
            var item = replay(SourceArtifactBundle.combine(reverse ? List.of(docx,pdf()) : List.of(pdf(),docx)),
                    rule.replaceFirst("，", "；"), rule).items().getFirst();
            assertThat(item.qualification().candidateEligible()).as("%s", item).isFalse();
            assertThat(item.fact().sourceCellIds()).isEmpty();
            assertThat(item.ruleText()).isEqualTo(rule);
            assertThat(item.rawMaximum()).isEqualByComparingTo("3");
            assertThat(item.computable()).isTrue();
        }
    }
    @Test void completeLastRowBeforeIndependentNewChapterTableStillRebuildsFullRule() throws Exception {
        for (String variant : List.of("independent-table", "independent-header", "independent-row")) {
        var docx=convert(variant);
        for(var order:List.of(List.of(pdf(),docx),List.of(docx,pdf()))) {
            var item=replay(SourceArtifactBundle.combine(order),RULE.replaceFirst("，","；")).items().getFirst();
            assertThat(item.qualification().candidateEligible()).as("%s",item).isTrue();
            assertThat(item.ruleText().replace("\n", "")).isEqualTo(RULE);
            assertThat(item.computable()).isTrue();
        }
        }
    }
}
