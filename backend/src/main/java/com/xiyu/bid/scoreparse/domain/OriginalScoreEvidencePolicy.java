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
        Map<String, String> parentByFact = new java.util.LinkedHashMap<>();
        for (MultimodalScoreFact fact : facts) {
            if (fact.parentCode().isBlank()) continue;
            MultimodalScoreFact parent = MultimodalFactParentCodeNormalizer.resolveParent(fact, facts);
            if (parent != null) parentByFact.put(OriginalScoreQualificationPolicy.logicalKey(fact.schemeId(),
                    fact.scopeStatus(), fact.sectionTitle(), fact.code()), OriginalScoreQualificationPolicy.logicalKey(
                    parent.schemeId(), parent.scopeStatus(), parent.sectionTitle(), parent.code()));
        }
        Set<String> parentKeys = new LinkedHashSet<>(parentByFact.values());
        List<ScoreUnitPolicy.Node> nodes = facts.stream().map(f -> {
            String id = OriginalScoreQualificationPolicy.logicalKey(f.schemeId(), f.scopeStatus(), f.sectionTitle(), f.code());
            return new ScoreUnitPolicy.Node(id, parentByFact.getOrDefault(id, ""), f.weight(), f.childAggregation().name());
        }).toList();
        Set<String> units = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<ScoreEvidenceClaim> claims = facts.stream().map(f -> new ScoreEvidenceClaim(modelFactId(f),
                modelFactId(f),
                f.tableId(), f.dimension(), f.sourceCellIds(), f.sourceCellIds(), f.weight(),
                parentKeys.contains(OriginalScoreQualificationPolicy.logicalKey(f.schemeId(), f.scopeStatus(), f.sectionTitle(), f.code())),
                units.contains(OriginalScoreQualificationPolicy.logicalKey(f.schemeId(), f.scopeStatus(), f.sectionTitle(), f.code())), f.quotedText(), tenderText,
                f.schemeId(), f.scopeStatus(), f.childAggregation())).toList();
        return verify(context, artifact, claims, modelTotal, true);
    }

    public static Verification verifyFacts(DocumentArtifact artifact, String tenderText, List<ScoreFact> facts) {
        return verifyFacts(OriginalScoringContext.resolve(artifact, tenderText), artifact, facts);
    }

    public static Verification verifyFacts(OriginalScoringContext context, DocumentArtifact artifact,
                                           List<ScoreFact> facts) {
        Set<String> parentIds = ScoreFact.parentIds(facts);
        Map<String, ScoreFact> factsById = facts.stream().collect(java.util.stream.Collectors.toMap(
                ScoreFact::factId, fact -> fact, (first, ignored) -> first));
        List<ScoreUnitPolicy.Node> nodes = facts.stream().map(f -> new ScoreUnitPolicy.Node(
                f.factId(), f.parentFactId(), f.weight(), f.childAggregation().name())).toList();
        Set<String> units = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<ScoreEvidenceClaim> claims = facts.stream().map(f -> new ScoreEvidenceClaim(f.factId(), f.factId(), f.sourceTableId(),
                f.dimension(), qualificationSourceCells(f, factsById), f.weightEvidence() == null ? f.sourceCellIds()
                : List.of(f.weightEvidence().cellId()), f.weight(), parentIds.contains(f.factId()),
                units.contains(f.factId()), f.dimension(), null, "", "", f.childAggregation())).toList();
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
        if (requireModelTotal && modelTotal == null) {
            problems.add(new Problem("", "declaredTotal", "MODEL_TOTAL_MISSING", total.value(), null, total.locator()));
        } else if (requireModelTotal && total.value() != null && modelTotal.compareTo(total.value()) != 0) {
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
            this(factId, schemeId, scopeStatus, candidateEligible, reason, locator, "", null,
                    "UNKNOWN", "", "", "UNKNOWN", List.of(), ChildAggregation.UNKNOWN);
        }
    }
    public record Verification(String documentId, String scheme, Total total, List<Problem> problems,
                               List<Qualification> qualifications) {
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
                || leafTotal == null || leafTotal.compareTo(verification.total().value()) != 0
                || facts == null || facts.isEmpty() || candidates == null || candidates.isEmpty()
                || !expectedCandidates.equals(candidates)) {
            return null;
        }
        return new PublicationProof(verification, facts, candidates);
    }
}
