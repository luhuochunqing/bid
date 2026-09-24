// Input: immutable document artifact, tender text, and candidate score facts
// Output: scoped original-total and item-maximum verification with publication proof
// Pos: scoreparse/domain — pure source-evidence policy shared by model and deterministic paths
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure checks against the original artifact, never against another model claim. */
public final class OriginalScoreEvidencePolicy {
    private static final Pattern SCORE_CELL = Pattern.compile("^[~≈]?\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    private static final Pattern MAX_SCORE = Pattern.compile(
            "(?:最高(?:可)?(?:得分?|分值|分)?|满分|上限|不超过)\\s*[：:为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private static final Pattern TITLE_SCORE = Pattern.compile("[（(]\\s*(\\d+(?:\\.\\d+)?)\\s*分\\s*[）)]");
    private static final Pattern AWARD_SCORE = Pattern.compile("(?:得|奖励|加)\\s*(\\d+(?:\\.\\d+)?)\\s*分");

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
        Set<String> parentKeys = new LinkedHashSet<>();
        facts.stream().filter(f -> !f.parentCode().isBlank())
                .forEach(f -> parentKeys.add(f.sectionTitle() + "/" + f.parentCode()));
        List<ScoreUnitPolicy.Node> nodes = facts.stream().map(f -> new ScoreUnitPolicy.Node(
                f.sectionTitle() + "/" + f.code(), f.parentCode().isBlank() ? ""
                : f.sectionTitle() + "/" + f.parentCode(), f.weight(), f.childAggregation().name())).toList();
        Set<String> units = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<Claim> claims = facts.stream().map(f -> new Claim(f.sectionTitle() + "/" + f.code(),
                f.tableId(), f.dimension(), f.sourceCellIds(), f.weight(),
                parentKeys.contains(f.sectionTitle() + "/" + f.code()),
                units.contains(f.sectionTitle() + "/" + f.code()), f.quotedText(), tenderText)).toList();
        return verify(context, artifact, claims, modelTotal, true);
    }

    public static Verification verifyFacts(DocumentArtifact artifact, String tenderText, List<ScoreFact> facts) {
        return verifyFacts(OriginalScoringContext.resolve(artifact, tenderText), artifact, facts);
    }

    public static Verification verifyFacts(OriginalScoringContext context, DocumentArtifact artifact,
                                           List<ScoreFact> facts) {
        Set<String> parentIds = ScoreFact.parentIds(facts);
        List<ScoreUnitPolicy.Node> nodes = facts.stream().map(f -> new ScoreUnitPolicy.Node(
                f.factId(), f.parentFactId(), f.weight(), f.childAggregation().name())).toList();
        Set<String> units = ScoreUnitPolicy.effectiveUnitIds(nodes);
        List<Claim> claims = facts.stream().map(f -> new Claim(f.factId(), f.sourceTableId(),
                f.dimension(), f.weightEvidence() == null ? f.sourceCellIds()
                : List.of(f.weightEvidence().cellId()), f.weight(), parentIds.contains(f.factId()),
                units.contains(f.factId()), f.dimension(), null)).toList();
        return verify(context, artifact, claims, null, false);
    }

    private static Verification verify(OriginalScoringContext context, DocumentArtifact artifact, List<Claim> claims,
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
        for (Claim claim : claims) {
            boolean textBacked = claim.textLocator();
            boolean inScheme = textBacked || (artifact != null && artifact.tables().stream()
                    .anyMatch(table -> table.tableId().equals(claim.tableId()) && context.owns(table)));
            if (!inScheme) problems.add(new Problem(claim.id(), "tableId", "ITEM_SCHEME_SCOPE_MISMATCH",
                    context.scheme(), claim.tableId(), claim.tableId()));
            verifyWeight(artifact, claim, problems);
        }
        context.subtotals().forEach((tableId, subtotal) -> {
            BigDecimal leafSum = claims.stream().filter(claim -> tableId.equals(claim.tableId())
                            && claim.contributesToTotal())
                    .map(Claim::weight).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (leafSum.compareTo(subtotal.value()) != 0) {
                problems.add(new Problem(tableId, "subtotal", "TABLE_SUBTOTAL_MISMATCH",
                        subtotal.value(), leafSum, subtotal.locator()));
            }
        });
        return new Verification(context.documentId(), context.scheme(), total, List.copyOf(problems));
    }

    private static void verifyWeight(DocumentArtifact artifact, Claim claim, List<Problem> problems) {
        if (claim.weight() == null && !claim.parent()) return;
        if (claim.textLocator()) {
            if (claim.weight() == null) return;
            String text = claim.tenderText() == null ? "" : claim.tenderText();
            String quote = claim.quotedText() == null ? "" : claim.quotedText();
            String evidence = text.contains(quote) ? quote : text;
            Pattern weight = Pattern.compile("(?<!\\d)" + Pattern.quote(claim.weight().stripTrailingZeros().toPlainString()) + "\\s*分");
            if (weight.matcher(evidence).find()) return;
            problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                    "original item max score", claim.weight(), claim.sourceCellIds().get(0)));
            return;
        }
        if (artifact == null || claim.sourceCellIds().isEmpty()) {
            problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                    "original item max score", claim.weight(), ""));
            return;
        }
        for (var table : artifact.tables()) {
            if (!table.tableId().equals(claim.tableId())) continue;
            var anchor = table.cells().stream().filter(c -> claim.sourceCellIds().contains(c.cellId()))
                    .findFirst().orElse(null);
            if (anchor == null || anchor.row() == null) {
                problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        "original item max score", claim.weight(), claim.tableId()));
                return;
            }
            var row = table.cells().stream().filter(c -> c.row() != null && !Boolean.TRUE.equals(c.isMergedContinue())
                    && c.row() <= anchor.row() && c.row() + Math.max(1, c.rowspan() == null ? 1 : c.rowspan()) > anchor.row())
                    .toList();
            String locator = table.tableId() + ":" + anchor.cellId();
            String rowText = row.stream().map(c -> c.rawText() == null ? "" : c.rawText())
                    .reduce((a, b) -> a + " " + b).orElse("");
            boolean itemMatched = (!claim.dimension().isBlank() && rowText.contains(claim.dimension()))
                    || (!claim.quotedText().isBlank() && rowText.contains(claim.quotedText())
                    && (claim.dimension().isBlank() || claim.quotedText().contains(claim.dimension())));
            if (!itemMatched) {
                problems.add(new Problem(claim.id(), "dimension", "ITEM_SOURCE_SCOPE_MISMATCH",
                        claim.dimension(), "not in cited item row", locator));
            }
            List<BigDecimal> explicit = new ArrayList<>();
            List<BigDecimal> capped = new ArrayList<>();
            List<BigDecimal> title = new ArrayList<>();
            List<BigDecimal> awarded = new ArrayList<>();
            for (var cell : row) {
                String raw = cell.rawText() == null ? "" : cell.rawText();
                Matcher numeric = SCORE_CELL.matcher(raw);
                boolean scoreColumn = cell.column() != null && table.cells().stream()
                        .anyMatch(header -> header.row() != null && header.row() == 0
                                && cell.column().equals(header.column()) && header.rawText() != null
                                && header.rawText().matches("(?s).*(?:最高分|分值|权重|得分).*"));
                if (cell.column() != null && cell.column() > 0 && numeric.matches()
                        && (raw.contains("分") || scoreColumn)) {
                    explicit.add(new BigDecimal(numeric.group(1)));
                }
                collect(MAX_SCORE, raw, capped);
                collect(TITLE_SCORE, raw, title);
                if (!raw.matches("(?s).*(?:每份|每个|每项|每有|每增加).*")) {
                    collect(AWARD_SCORE, raw, awarded);
                }
            }
            List<BigDecimal> authoritative = claim.parent() && !title.isEmpty() ? title
                    : !explicit.isEmpty() ? explicit : !capped.isEmpty() ? capped
                    : !title.isEmpty() ? title : awarded;
            if (!claim.parent() && !explicit.isEmpty() && !capped.isEmpty()) authoritative = concat(explicit, capped);
            if (authoritative.isEmpty() || distinct(authoritative).size() != 1) {
                if (!claim.parent() && distinct(title).size() == 1 && claim.weight() != null
                        && distinct(title).iterator().next().compareTo(claim.weight()) == 0) {
                    return;
                }
                problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                        authoritative, claim.weight(), locator));
            } else if (claim.weight() == null || claim.weight().compareTo(authoritative.get(0)) != 0) {
                problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_MISMATCH",
                        authoritative.get(0), claim.weight(), locator));
            }
            return;
        }
        problems.add(new Problem(claim.id(), "weight", "ITEM_WEIGHT_SOURCE_UNKNOWN",
                "original item max score", claim.weight(), claim.tableId()));
    }

    private static List<BigDecimal> concat(List<BigDecimal> first, List<BigDecimal> second) {
        List<BigDecimal> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    private static Set<BigDecimal> distinct(List<BigDecimal> values) {
        Set<BigDecimal> distinct = new LinkedHashSet<>();
        values.forEach(value -> distinct.add(value.stripTrailingZeros()));
        return distinct;
    }

    private static void collect(Pattern pattern, String raw, List<BigDecimal> values) {
        Matcher matcher = pattern.matcher(raw);
        while (matcher.find()) values.add(new BigDecimal(matcher.group(1)));
    }

    private record Claim(String id, String tableId, String dimension, List<String> sourceCellIds,
                         BigDecimal weight, boolean parent, boolean contributesToTotal,
                         String quotedText, String tenderText) {
        private boolean textLocator() {
            return (tableId == null || tableId.isBlank())
                    && sourceCellIds != null && sourceCellIds.size() == 1
                    && sourceCellIds.get(0).startsWith("text:line[");
        }
    }
    public record Problem(String factId, String field, String code, Object expected, Object actual, String locator) {}
    public record Total(BigDecimal value, String source, String scope, String locator, List<Problem> problems) {}
    public record Verification(String documentId, String scheme, Total total, List<Problem> problems) {
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
        if (verification == null || !verification.publishable() || verification.total().value() == null
                || leafTotal == null || leafTotal.compareTo(verification.total().value()) != 0
                || facts == null || facts.isEmpty() || candidates == null || candidates.isEmpty()
                || !facts.stream().filter(f -> ScoreUnitPolicy.effectiveUnitIds(facts.stream().map(fact ->
                        new ScoreUnitPolicy.Node(fact.factId(), fact.parentFactId(), fact.weight(),
                                fact.childAggregation().name())).toList()).contains(f.factId())).map(ScoreFact::toCandidate)
                        .flatMap(java.util.Optional::stream).toList().equals(candidates)) {
            return null;
        }
        return new PublicationProof(verification, facts, candidates);
    }
}
