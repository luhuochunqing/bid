package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.EvidenceText;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.SourceSpanResolver;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Recovers only independently proven, wholly absent rows for manual review. */
final class SourceReviewRowRecovery {
    record Result(List<ScoreFact> facts, List<OriginalScoreEvidencePolicy.Qualification> qualifications,
                  Set<String> recoveredRows) {
        Result { facts = List.copyOf(facts); qualifications = List.copyOf(qualifications); recoveredRows = Set.copyOf(recoveredRows); }
    }
    private SourceReviewRowRecovery() { }

    static MultimodalScoreFactValidator.ValidationReport reviewValidation(
            MultimodalScoreFactValidator.ValidationReport validation, Result recovery, DocumentArtifact artifact) {
        if (recovery.facts().isEmpty()) return validation;
        var violations = validation.violations().stream().filter(v -> !"WEIGHT_SUM_MISMATCH".equals(v.code()))
                .filter(v -> !"SOURCE_SCORE_ROW_UNCOVERED".equals(v.code()) || !recovery.recoveredRows().contains(v.evidence()))
                .collect(Collectors.toCollection(ArrayList::new));
        var provisional = new MultimodalScoreFactValidator.ValidationReport(validation.facts(), validation.declaredTotal(),
                validation.originalTotal(), validation.leafTotal(), false, violations, validation.sourceVerification());
        var eligible = eligibleFactIds(reviewQualifications(validation, recovery));
        var nodes = reviewFacts(provisional, artifact, recovery).stream().filter(f -> eligible.contains(f.factId()))
                .map(f -> new com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy.Node(f.factId(), f.parentFactId(), f.weight(),
                        f.childAggregation().name())).toList();
        var leafTotal = com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy.totalWeight(nodes);
        var comparable = com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy.comparableTotal(nodes, validation.sourceVerification().total().source());
        if (validation.originalTotal() != null && comparable.compareTo(validation.originalTotal()) != 0)
            violations.add(new MultimodalScoreFactValidator.Violation("", "weight", "WEIGHT_SUM_MISMATCH",
                    "叶子项总分必须与已核验原文总分闭合", validation.originalTotal(), comparable,
                    validation.sourceVerification().total().locator()));
        return new MultimodalScoreFactValidator.ValidationReport(validation.facts(), validation.declaredTotal(),
                validation.originalTotal(), leafTotal, false, violations, validation.sourceVerification());
    }

    static List<ScoreFact> reviewFacts(MultimodalScoreFactValidator.ValidationReport validation,
                                       DocumentArtifact artifact, Result recovery) {
        return java.util.stream.Stream.concat(validation.facts().stream()
                .map(f -> TextPrimaryScoreFactMapper.map(f, artifact, validation)), recovery.facts().stream()).toList();
    }

    static List<OriginalScoreEvidencePolicy.Qualification> reviewQualifications(
            MultimodalScoreFactValidator.ValidationReport validation, Result recovery) {
        return java.util.stream.Stream.concat(validation.sourceVerification().qualifications().stream(),
                recovery.qualifications().stream()).toList();
    }

    static Set<String> eligibleFactIds(List<OriginalScoreEvidencePolicy.Qualification> qualifications) {
        return qualifications.stream().filter(OriginalScoreEvidencePolicy.Qualification::candidateEligible)
                .map(OriginalScoreEvidencePolicy.Qualification::factId).collect(Collectors.toSet());
    }

    static Result recover(DocumentArtifact artifact, OriginalScoringContext context, String text,
                          MultimodalScoreFactValidator.ValidationReport validation) {
        if (artifact == null || context == null || validation.autoApprovable()) return new Result(List.of(), List.of(), Set.of());
        Set<String> missing = validation.violations().stream().filter(v -> "SOURCE_SCORE_ROW_UNCOVERED".equals(v.code()))
                .map(MultimodalScoreFactValidator.Violation::evidence).collect(Collectors.toSet());
        if (missing.isEmpty()) return new Result(List.of(), List.of(), Set.of());
        Set<String> originalCells = artifact.tables().stream().flatMap(t -> t.cells().stream())
                .map(c -> c.cellId()).collect(Collectors.toSet());
        // An unbound fact may represent any missing row, even with a malformed or blank code.
        // Do not infer it is independent merely because its code differs.
        if (validation.facts().stream().anyMatch(f -> f.sourceCellIds().stream().noneMatch(originalCells::contains)))
            return new Result(List.of(), List.of(), Set.of());
        var all = ScoreFactShadowPipeline.run(artifact, text).tables().stream()
                .flatMap(table -> table.facts().stream()).toList();
        var recovered = new ArrayList<ScoreFact>();
        var qualifications = new ArrayList<OriginalScoreEvidencePolicy.Qualification>();
        var recoveredRows = new java.util.LinkedHashSet<String>();
        for (var fact : all) {
            if (fact.code().isBlank() || fact.weight() == null || fact.weight().signum() < 0
                    || !fact.parentFactId().isBlank() || !ScoreFact.isLeaf(fact, all)) continue;
            var table = artifact.tables().stream().filter(t -> t.tableId().equals(fact.sourceTableId()) && context.owns(t))
                    .findFirst().orElse(null);
            if (table == null) continue;
            var rowNumbers = table.cells().stream().filter(c -> fact.sourceCellIds().contains(c.cellId()) && c.row() != null)
                    .map(c -> c.row()).filter(r -> missing.contains(table.tableId() + ":" + table.tableId() + "-r" + r))
                    .distinct().toList();
            if (rowNumbers.size() != 1) continue;
            int row = rowNumbers.getFirst();
            var rowIds = table.cells().stream().filter(c -> Integer.valueOf(row).equals(c.row())).map(c -> c.cellId()).collect(Collectors.toSet());
            var model = new MultimodalScoreFact("", fact.code(), "", fact.dimension(), fact.weight(), "", "",
                    null, fact.sourceTableId(), fact.sourceCellIds(), "", fact.confidence());
            boolean duplicate = validation.facts().stream().anyMatch(m ->
                    isExistingModelFactDuplicate(fact, m));
            boolean ambiguous = duplicate || validation.facts().stream().anyMatch(m ->
                    fact.factId().equals(OriginalScoreEvidencePolicy.modelFactId(m))
                    || LogicalRuleSourceUnit.sharesLogicalRow(artifact, model, m)
                    || m.sourceCellIds().stream().anyMatch(rowIds::contains)
                    || ((m.tableId().isBlank() || m.tableId().equals(table.tableId()))
                    && (m.code().equals(fact.code()) || m.parentCode().equals(fact.code())
                    || m.code().startsWith(fact.code() + ".") || m.code().startsWith(fact.code() + "-"))));
            if (ambiguous || !LogicalRuleSourceUnit.closed(artifact, fact.sourceCellIds())) continue;
            var unit = LogicalRuleSourceUnit.resolve(artifact, model);
            if (!unit.applicable() || !unit.closed() || unit.spans().isEmpty()) continue;
            String rule = unit.spans().stream().map(SourceSpan::rawText).collect(Collectors.joining("\n"));
            String normalizedRule = EvidenceText.normalize(rule).text().replaceAll("\\s+", "");
            String normalizedFact = EvidenceText.normalize(fact.sourceText()).text().replaceAll("\\s+", "");
            if (normalizedRule.isBlank() || !normalizedFact.contains(normalizedRule)) continue;
            var check = OriginalScoreEvidencePolicy.verifyFacts(context, artifact, List.of(fact));
            var q = check.qualifications().stream().filter(item -> item.factId().equals(fact.factId()) && item.candidateEligible())
                    .findFirst().orElse(null);
            if (q == null) continue;
            var metadata = new ScoreSourceMetadata("", "", SourceSpanResolver.resolve(artifact, fact.sourceCellIds()),
                    List.of("SOURCE_ROW_RECOVERED_FOR_REVIEW"));
            recovered.add(new ScoreFact(fact.factId(), fact.sourceTableId(), fact.sourceCellIds(), rule.strip(),
                    fact.code(), fact.dimension(), fact.weight(), fact.weightEvidence(), fact.scoreRule(), fact.parentFactId(),
                    fact.extractionMethod(), fact.confidence(), ScoreFact.ValidationStatus.REVIEW_REQUIRED,
                    fact.childAggregation(), metadata));
            qualifications.add(q);
            recoveredRows.add(table.tableId() + ":" + table.tableId() + "-r" + row);
        }
        return new Result(recovered, qualifications, recoveredRows);
    }

    private static boolean isExistingModelFactDuplicate(ScoreFact fact, MultimodalScoreFact m) {
        if (m == null || fact == null) return false;
        boolean sameWeight = m.weight() != null && fact.weight() != null
                && m.weight().compareTo(fact.weight()) == 0;
        if (!sameWeight) return false;

        String cFact = cleanCode(fact.code());
        String cModel = cleanCode(m.code());
        if (!cFact.isBlank() && cFact.equals(cModel)) {
            return true;
        }

        String fDim = compactText(fact.dimension());
        String mDim = compactText(m.dimension());
        if (!fDim.isBlank() && !mDim.isBlank() && (fDim.contains(mDim) || mDim.contains(fDim))) {
            return true;
        }
        String fText = compactText(fact.sourceText());
        String mRule = compactText(m.ruleText().isBlank() ? m.quotedText() : m.ruleText());
        if (!fText.isBlank() && !mRule.isBlank() && (fText.contains(mRule) || mRule.contains(fText))) {
            return true;
        }
        return false;
    }

    private static String cleanCode(String code) {
        if (code == null) return "";
        return code.replaceAll("^[（(]|[)）]$", "").replaceAll("[、.\\s]+$", "").trim();
    }

    private static String compactText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "");
    }
}
