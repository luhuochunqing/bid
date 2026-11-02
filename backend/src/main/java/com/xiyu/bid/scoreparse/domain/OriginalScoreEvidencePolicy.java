// Input: immutable document artifact, tender text, and candidate score facts
// Output: scoped original-total and item-maximum verification with publication proof
// Pos: scoreparse/domain — pure source-evidence policy shared by model and deterministic paths
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure checks against the original artifact, never against another model claim. */
public final class OriginalScoreEvidencePolicy {

    private OriginalScoreEvidencePolicy() {}

    public static Verification verifyModel(DocumentArtifact artifact, String tenderText,
                                           List<MultimodalScoreFact> facts, BigDecimal modelTotal) {
        return verifyModel(OriginalScoringContext.resolve(artifact, tenderText), artifact, tenderText, facts, modelTotal);
    }

    public static Verification verifyModel(OriginalScoringContext context, DocumentArtifact artifact,
                                           List<MultimodalScoreFact> facts, BigDecimal modelTotal) {
        return verifyModel(context, artifact, null, facts, modelTotal);
    }

    public static Verification verifyModel(OriginalScoringContext context, DocumentArtifact artifact,
                                           String tenderText, List<MultimodalScoreFact> facts, BigDecimal modelTotal) {
        // Facts are identified by `modelFactId`, not by their section+code key: two facts may share a
        // blank code, and keying the parent graph by that key makes one child's parent link overwrite
        // the other's. The collided node then reports as a leaf, so a SUM parent's own weight is
        // summed alongside the children that should have replaced it and the table subtotal no longer
        // closes.
        Map<String, String> parentByFact = new java.util.LinkedHashMap<>();
        for (MultimodalScoreFact fact : facts) {
            if (fact.parentCode().isBlank()) continue;
            MultimodalScoreFact parent = MultimodalFactParentCodeNormalizer.resolveParent(fact, facts);
            if (parent != null) parentByFact.put(modelFactId(fact), modelFactId(parent));
        }
        Set<String> parentKeys = new LinkedHashSet<>(parentByFact.values());
        List<ScoreUnitPolicy.Node> nodes = facts.stream().map(f -> new ScoreUnitPolicy.Node(
                modelFactId(f), parentByFact.getOrDefault(modelFactId(f), ""), f.weight(),
                f.childAggregation().name())).toList();
        Set<String> units = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<ScoreEvidenceClaim> claims = facts.stream().map(f -> {
            String id = modelFactId(f);
            return new ScoreEvidenceClaim(id, id,
                    f.tableId(), f.dimension(), f.sourceCellIds(), f.sourceCellIds(), f.weight(),
                    parentKeys.contains(id), units.contains(id), f.quotedText(), tenderText,
                    f.schemeId(), f.scopeStatus(), f.childAggregation(), OriginalRuleRestoration.restoreForWeight(f, artifact, facts));
        }).toList();
        return rejectAmbiguousIdentity(verify(context, artifact, claims, modelTotal, true), facts);
    }

    /**
     * Blocks auto-approval when two facts share one {@code modelFactId}.
     *
     * <p>The id covers the fact's scoring identity (scheme, scope, section, code, dimension,
     * weight, quote, parent). Two facts agreeing on all of it but sourced from different cells are
     * indistinguishable here, and every structure keyed by the id — the parent graph above all —
     * would silently merge them: one side's parent link wins, the merged node reports as a leaf,
     * and a SUM parent's own weight gets summed next to the children that should have replaced it.
     * Merging is the wrong resolution and arbitrarily picking a winner is worse, so the collision
     * becomes a blocking problem: the document goes to manual review instead.
     *
     * <p>Extending the id with the source locator would narrow the window further, but the locator
     * is model-produced and re-parse-dependent, so identity built on it would churn between runs.
     * Refusing is total — it holds for every collision cause, not just the ones we can enumerate.
     */
    private static Verification rejectAmbiguousIdentity(Verification verification, List<MultimodalScoreFact> facts) {
        if (facts == null || facts.isEmpty()) return verification;
        Map<String, Integer> identityCounts = new java.util.LinkedHashMap<>();
        for (MultimodalScoreFact fact : facts) identityCounts.merge(modelFactId(fact), 1, Integer::sum);
        List<Map.Entry<String, Integer>> collisions = identityCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1).toList();
        if (collisions.isEmpty()) return verification;
        List<Problem> problems = new ArrayList<>(verification.problems());
        for (Map.Entry<String, Integer> collision : collisions) {
            problems.add(new Problem(collision.getKey(), "factId", "ORIGINAL_FACT_IDENTITY_AMBIGUOUS",
                    "a fact identity unique within the document", collision.getValue(),
                    verification.total().locator()));
        }
        return new Verification(verification.documentId(), verification.scheme(), verification.total(),
                List.copyOf(problems), verification.qualifications());
    }

    public static Verification verifyFacts(DocumentArtifact artifact, String tenderText, List<ScoreFact> facts) {
        return verifyFacts(OriginalScoringContext.resolve(artifact, tenderText), artifact, facts);
    }

    public static Verification verifyFacts(OriginalScoringContext context, DocumentArtifact artifact,
                                           List<ScoreFact> facts) {
        Set<String> parentIds = ScoreFact.parentIds(facts);
        var ruleUnits = OriginalRuleRestoration.restoreFacts(artifact, facts);
        Map<String, ScoreFact> factsById = facts.stream().collect(java.util.stream.Collectors.toMap(
                ScoreFact::factId, fact -> fact, (first, ignored) -> first));
        List<ScoreUnitPolicy.Node> nodes = facts.stream().map(f -> new ScoreUnitPolicy.Node(
                f.factId(), f.parentFactId(), f.weight(), f.childAggregation().name())).toList();
        Set<String> units = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<ScoreEvidenceClaim> claims = facts.stream().map(f -> new ScoreEvidenceClaim(f.factId(), f.factId(), f.sourceTableId(),
                f.dimension(), qualificationSourceCells(f, factsById), f.weightEvidence() == null ? f.sourceCellIds()
                : List.of(f.weightEvidence().cellId()), f.weight(), parentIds.contains(f.factId()),
                units.contains(f.factId()), f.dimension(), null, "", "", f.childAggregation(), ruleUnits.get(f))).toList();
        return verify(context, artifact, claims, null, false);
    }

    private static List<String> qualificationSourceCells(ScoreFact fact, Map<String, ScoreFact> factsById) {
        Set<String> cellIds = new LinkedHashSet<>(fact.sourceCellIds());
        Set<String> visited = new LinkedHashSet<>();
        ScoreFact parent = factsById.get(fact.parentFactId());
        while (parent != null && visited.add(parent.factId())
                && fact.sourceTableId().equals(parent.sourceTableId())) {
            cellIds.addAll(parent.sourceCellIds());
            parent = factsById.get(parent.parentFactId());
        }
        return List.copyOf(cellIds);
    }

    private static Verification verify(OriginalScoringContext context, DocumentArtifact artifact, List<ScoreEvidenceClaim> claims,
                                       BigDecimal modelTotal, boolean requireModelTotal) {
        Total total = context.total();
        List<Problem> problems = new ArrayList<>(total.problems());
        problems.addAll(context.problems());
        if (claims.isEmpty()) problems.add(new Problem("", "facts", "ORIGINAL_FACTS_MISSING",
                "non-empty scoring facts", 0, total.locator()));
        // 抽取提示要求模型拿不准时把 declaredTotal 留空。原文总分已经核验时，留空不再否决；
        // 只有模型给出了另一个总分才记冲突。
        if (requireModelTotal && modelTotal == null && total.value() == null) {
            problems.add(new Problem("", "declaredTotal", "MODEL_TOTAL_MISSING", total.value(), null, total.locator()));
        } else if (requireModelTotal && modelTotal != null && total.value() != null
                && modelTotal.compareTo(total.value()) != 0) {
            problems.add(new Problem("", "declaredTotal", "MODEL_TOTAL_SOURCE_MISMATCH",
                    total.value(), modelTotal, total.locator()));
        }
        List<Qualification> qualifications = new ArrayList<>();
        Set<String> eligibleClaimIds = new LinkedHashSet<>();
        List<OriginalScoringContext.SchemeEvidence> schemes = OriginalScoringContext.schemes(artifact,
                claims.stream().map(ScoreEvidenceClaim::tenderText).filter(java.util.Objects::nonNull).findFirst().orElse(null));
        for (ScoreEvidenceClaim claim : claims) {
            Qualification qualification = OriginalScoreQualificationPolicy.qualify(context, artifact, claim, schemes);
            qualifications.add(qualification);
            // 权重为 0 的行是「见评审办法前附表」这类指引或大纲，不是评分单位。
            // 资格仍要记下，方案外的 0 分项才能继续被校验器排除。
            if (claim.weight() != null && claim.weight().signum() <= 0) continue;
            if (qualification.candidateEligible()) {
                eligibleClaimIds.add(claim.id());
                OriginalScoreWeightEvidencePolicy.verifyWeight(artifact, claim, problems);
            } else if (!"OUT_OF_SCHEME".equals(qualification.scopeStatus())) {
                problems.add(new Problem(claim.id(), "tableId",
                        qualification.reason() == null ? "SOURCE_SCHEME_NOT_VERIFIED" : qualification.reason(),
                        qualification.schemeTitle().isBlank() ? context.scheme() : qualification.schemeTitle(),
                        qualification.scopeStatus(), qualification.locator().isBlank() ? claim.tableId() : qualification.locator()));
            }
        }
        if (!claims.isEmpty() && eligibleClaimIds.isEmpty()
                && qualifications.stream().allMatch(q -> "OUT_OF_SCHEME".equals(q.scopeStatus()))) {
            problems.add(new Problem("", "facts", "ORIGINAL_ELIGIBLE_FACTS_MISSING",
                    "at least one source-verified fact in the scoring scheme", claims.size(), context.scheme()));
        }
        Set<String> coveredClaimIds = qualifications.stream().filter(Qualification::candidateEligible)
                .map(Qualification::factId).collect(java.util.stream.Collectors.toSet());
        problems.addAll(SourceScoreRowCoveragePolicy.uncovered(artifact, context, claims, coveredClaimIds));
        context.subtotals().forEach((tableId, subtotal) -> {
            BigDecimal leafSum = claims.stream().filter(claim -> eligibleClaimIds.contains(claim.id())
                            && tableId.equals(claim.tableId())
                            && claim.contributesToTotal())
                    .map(ScoreEvidenceClaim::weight).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (leafSum.compareTo(subtotal.value()) != 0) {
                problems.add(new Problem(tableId, "subtotal", "TABLE_SUBTOTAL_MISMATCH",
                        subtotal.value(), leafSum, subtotal.locator()));
            }
        });
        return new Verification(context.documentId(), context.scheme(), total, List.copyOf(problems),
                List.copyOf(qualifications));
    }
    public static String qualificationFactKey(MultimodalScoreFact fact) {
        return OriginalScoreQualificationPolicy.qualificationFactKey(fact);
    }
    public static String modelFactId(MultimodalScoreFact fact) {
        return OriginalScoreQualificationPolicy.modelFactId(fact);
    }
    public static List<MultimodalScoreFact> eligibleModelFacts(List<MultimodalScoreFact> facts,
                                                                Verification verification) {
        return OriginalScoreQualificationPolicy.eligibleModelFacts(facts, verification);
    }

    public record Problem(String factId, String field, String code, Object expected, Object actual, String locator) {}
    public record Total(BigDecimal value, String source, String scope, String locator, List<Problem> problems,
                        String quote) {
        public Total(BigDecimal value, String source, String scope, String locator, List<Problem> problems) {
            this(value, source, scope, locator, problems, "");
        }
    }
    public record Qualification(String factId, String schemeId, String scopeStatus, boolean candidateEligible,
                                String reason, String locator, String schemeTitle, BigDecimal declaredTotal,
                                String totalEvidenceStatus, String totalQuote, String totalLocator, String layoutStatus,
                                List<String> tableIds, ChildAggregation childAggregation) {
        public Qualification {
            schemeTitle = schemeTitle == null ? "" : schemeTitle;
            totalEvidenceStatus = totalEvidenceStatus == null ? "UNKNOWN" : totalEvidenceStatus;
            totalQuote = totalQuote == null ? "" : totalQuote;
            totalLocator = totalLocator == null ? "" : totalLocator;
            layoutStatus = layoutStatus == null ? "UNKNOWN" : layoutStatus;
            tableIds = tableIds == null ? List.of() : List.copyOf(tableIds);
            childAggregation = childAggregation == null ? ChildAggregation.UNKNOWN : childAggregation;
        }

        public Qualification(String factId, String schemeId, String scopeStatus, boolean candidateEligible,
                             String reason, String locator) {
            this(factId, schemeId, scopeStatus, candidateEligible, reason, locator, "", null, "UNKNOWN", "", "", "UNKNOWN", List.of(), ChildAggregation.UNKNOWN);
        }
    }
    public record Verification(String documentId, String scheme, Total total, List<Problem> problems, List<Qualification> qualifications) {
        public Verification(String documentId, String scheme, Total total, List<Problem> problems) {
            this(documentId, scheme, total, problems, List.of());
        }
        public boolean publishable() { return problems.isEmpty(); }
    }

    /** Created only after the original-source checks and leaf sum both close. */
    public static final class PublicationProof {
        private final Verification verification;
        private final List<ScoreFact> facts;
        private final List<ScoreCandidate> candidates;
        private PublicationProof(Verification verification, List<ScoreFact> facts, List<ScoreCandidate> candidates) {
            this.verification = verification;
            this.facts = List.copyOf(facts);
            this.candidates = List.copyOf(candidates);
        }
        public Verification verification() { return verification; }
        public boolean matches(List<ScoreFact> currentFacts, List<ScoreCandidate> currentCandidates) {
            return facts.equals(currentFacts) && candidates.equals(currentCandidates);
        }
    }

    /** 模型常把「商务/技术/报价」写进 dimension，项名和分值在原文标题里。 */
    static boolean sectionCategory(String dimension) {
        if (dimension == null || dimension.isBlank()) return false;
        return dimension.replaceAll("\\s+", "").matches("(?:商务|技术|报价|价格|其他|财务|运营|业绩|资质|服务|实施|方案)(?:部分|评分标准|评分|标准|因素|状况|经验|要求|方案)?");
    }

    static boolean matchesDimensionAlias(String source, String dimension) {
        if (dimension == null || dimension.isBlank() || source == null || source.isBlank()) return false;
        String dim = dimension.replaceAll("\\s+", "");
        String normSource = EvidenceText.normalize(source).text();
        return (dim.contains("运营") && normSource.contains("电子商城运营时间"))
                || (dim.contains("财务") && normSource.contains("授信额度"))
                || ((dim.contains("实施") || dim.contains("方案")) && normSource.contains("服务方案")) || (dim.contains("历史交易数据") && normSource.contains("历史交易数据"));
    }

    public static String publishedDimension(String dimension, String sourceText, BigDecimal weight) {
        if (!sectionCategory(dimension) || weight == null || sourceText == null || sourceText.isBlank()) {
            return dimension == null ? "" : dimension;
        }
        String number = weight.stripTrailingZeros().toPlainString();
        String compact = sourceText.replaceAll("\\s+", "");
        int at = markerAt(compact, "（" + number + "分）");
        if (at < 0) at = markerAt(compact, "(" + number + "分)");
        if (at <= 0) return dimension;
        int start = at;
        while (start > 0 && nameChar(compact.charAt(start - 1))) start--;
        String name = compact.substring(start, at).replaceFirst(
                "^(?:商务|技术|报价|价格|其他)?(?:评分标准|评分|标准|部分)?", "");
        return name.length() >= 2 && name.length() <= 24 ? name : dimension;
    }

    private static int markerAt(String compact, String marker) {
        return compact.indexOf(marker);
    }

    private static boolean nameChar(char value) {
        return Character.isLetterOrDigit(value) || (value >= 0x4e00 && value <= 0x9fff);
    }

    public static PublicationProof publicationProof(Verification verification, BigDecimal leafTotal,
                                                     List<ScoreFact> facts, List<ScoreCandidate> candidates) {
        Set<String> eligibleIds = verification == null ? Set.of() : verification.qualifications().stream()
                .filter(Qualification::candidateEligible).map(Qualification::factId)
                .collect(java.util.stream.Collectors.toSet());
        List<ScoreFact> eligibleFacts = facts == null ? List.of()
                : facts.stream().filter(fact -> eligibleIds.contains(fact.factId())).toList();
        List<ScoreUnitPolicy.Node> nodes = eligibleFacts.stream().map(fact -> new ScoreUnitPolicy.Node(
                fact.factId(), fact.parentFactId(), fact.weight(), fact.childAggregation().name())).toList();
        Set<String> unitIds = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<ScoreCandidate> expectedCandidates = eligibleFacts.stream()
                .filter(fact -> unitIds.contains(fact.factId())).map(ScoreFact::toCandidate)
                .flatMap(java.util.Optional::stream).toList();
        if (verification == null || !verification.publishable() || verification.total().value() == null
                || leafTotal == null || ScoreUnitPolicy.comparableTotal(nodes, verification.total().source())
                        .compareTo(verification.total().value()) != 0
                || facts == null || facts.isEmpty() || candidates == null || candidates.isEmpty()
                || !expectedCandidates.equals(candidates)) {
            return null;
        }
        return new PublicationProof(verification, facts, candidates);
    }
}
