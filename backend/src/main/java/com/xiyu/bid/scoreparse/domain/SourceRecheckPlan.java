package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A single bounded retry of unresolved original rows; never a full-document resampling loop. */
public record SourceRecheckPlan(DocumentArtifact artifact, String text, List<String> diagnostics, List<RetryTarget> targets) {
    public record RetryTarget(String factId, List<String> sourceCellIds) {
        public RetryTarget { sourceCellIds = List.copyOf(sourceCellIds); }
    }
    public SourceRecheckPlan(DocumentArtifact artifact, String text, List<String> diagnostics) {
        this(artifact, text, diagnostics, List.of());
    }

    private static final int MAX_ANCHORS = 8;
    private static final int MAX_CHARS = 12_000;

    public static SourceRecheckPlan build(DocumentArtifact artifact, List<MultimodalScoreFact> facts,
                                          List<MultimodalScoreFactValidator.Violation> violations) {
        Set<String> invalid = new LinkedHashSet<>();
        violations.stream().map(MultimodalScoreFactValidator.Violation::factId).filter(id -> !id.isBlank())
                .forEach(invalid::add);
        Set<String> missingRows = violations.stream().filter(v -> SourceScoreRowCoveragePolicy.UNCOVERED.equals(v.code()))
                .map(MultimodalScoreFactValidator.Violation::evidence).collect(java.util.stream.Collectors.toSet());
        Set<String> missingCells = new LinkedHashSet<>();
        for (var table : artifact.tables()) for (var cell : table.cells()) {
            if (cell.row() != null && missingRows.contains(table.tableId() + ":" + table.tableId() + "-r" + cell.row()))
                missingCells.add(cell.cellId());
        }
        var unresolved = SourceAnchorCatalog.build(artifact).stream().filter(anchor -> {
            if (anchor.sourceCellIds().stream().anyMatch(missingCells::contains)) return true;
            List<MultimodalScoreFact> owners = facts.stream().filter(fact -> fact.sourceCellIds().stream()
                    .anyMatch(anchor.sourceCellIds()::contains)).toList();
            if (!owners.isEmpty()) return owners.stream().anyMatch(f -> invalid.contains(OriginalScoreEvidencePolicy.modelFactId(f)));
            return anchor.rawText().matches("(?s).*(?:[（(](?:合计|满分)?|最高(?:得)?|满分|得)\\s*\\d+(?:\\.\\d+)?\\s*分.*");
        }).toList();
        StringBuilder text = new StringBuilder();
        Set<String> cells = new LinkedHashSet<>();
        List<RetryTarget> targets = new ArrayList<>();
        int selected = 0;
        for (var anchor : unresolved) {
            String fragment = "\n# " + anchor.sectionTitle() + "\n" + anchor.rawText() + "\n";
            if (selected >= MAX_ANCHORS || text.length() + fragment.length() > MAX_CHARS) continue;
            selected++;
            text.append(fragment);
            cells.addAll(anchor.sourceCellIds());
            facts.stream().filter(fact -> invalid.contains(OriginalScoreEvidencePolicy.modelFactId(fact)))
                    .filter(fact -> fact.sourceCellIds().stream().anyMatch(anchor.sourceCellIds()::contains))
                    .forEach(fact -> targets.add(new RetryTarget(OriginalScoreEvidencePolicy.modelFactId(fact), anchor.sourceCellIds())));
        }
        List<String> diagnostics = selected < unresolved.size()
                ? List.of("SOURCE_RECHECK_LIMIT_REMAINING:" + (unresolved.size() - selected)) : List.of();
        var tables = artifact.tables().stream().filter(table -> table.cells().stream().anyMatch(cell -> cells.contains(cell.cellId())))
                .map(table -> new DocumentArtifact.CanonicalTable(table.tableId(), table.sourcePage(), table.sourceSection(),
                        table.rows(), table.columns(), table.mergedRegions(), table.cells().stream()
                        .filter(cell -> cells.contains(cell.cellId())).toList(), table.isScoreTable(), table.sourceFileType())).toList();
        var blocks = artifact.textBlocks().stream()
                .filter(block -> cells.contains(block.blockId()) || cells.contains(block.locator())).toList();
        return new SourceRecheckPlan(new DocumentArtifact(artifact.artifact(), tables, artifact.warnings(), blocks,
                artifact.schemaVersion(), artifact.documents()), text.toString(), diagnostics, List.copyOf(targets));
    }

    public static List<MultimodalScoreFact> merge(List<MultimodalScoreFact> first, List<MultimodalScoreFact> retry,
                                                 List<String> verifiedFactIds, List<String> diagnostics) {
        return merge(first, retry, verifiedFactIds, diagnostics, List.of(), null);
    }

    public List<MultimodalScoreFact> merge(List<MultimodalScoreFact> first, List<MultimodalScoreFact> retry,
                                          List<String> verifiedFactIds) {
        return merge(first, retry, verifiedFactIds, diagnostics, targets, artifact);
    }

    private static List<MultimodalScoreFact> merge(List<MultimodalScoreFact> first, List<MultimodalScoreFact> retry,
                                                  List<String> verifiedFactIds, List<String> diagnostics,
                                                  List<RetryTarget> targets, DocumentArtifact artifact) {
        List<MultimodalScoreFact> merged = new ArrayList<>(first);
        var graph = MultimodalFactParentCodeNormalizer.normalize(java.util.stream.Stream.concat(first.stream(), retry.stream()).toList());
        var hierarchyUnits = SourceFactIdentity.ambiguousHierarchyUnits(graph, artifact);
        for (var candidate : retry) {
            List<MultimodalScoreFact> previous = first.stream()
                    .filter(f -> !SourceFactIdentity.related(f, candidate, graph))
                    .filter(f -> key(f).equals(key(candidate)) || (SourceFactIdentity.sameUnit(f, candidate, artifact)
                            && hierarchyUnits.stream().noneMatch(unit ->
                                    SourceFactIdentity.sameUnit(unit, f.withParentCode(""), artifact))))
                    .filter(f -> targets.stream().anyMatch(target -> target.factId().equals(OriginalScoreEvidencePolicy.modelFactId(f))
                            && !candidate.sourceCellIds().isEmpty()
                            && target.sourceCellIds().containsAll(candidate.sourceCellIds())))
                    .toList();
            boolean verified = verifiedFactIds.contains(OriginalScoreEvidencePolicy.modelFactId(candidate))
                    && retry.stream().filter(f -> key(f).equals(key(candidate)) || SourceFactIdentity.sameUnit(f, candidate, artifact)).count() == 1;
            if (previous.size() == 1 && verified) {
                var old = previous.getFirst();
                var notes = new ArrayList<>(old.sourceMetadata().diagnostics());
                notes.addAll(candidate.sourceMetadata().diagnostics());
                notes.add("SOURCE_RECHECK_REPLACED:" + old.quotedText());
                var replacement = candidate.parentCode().isBlank() ? candidate.withParentCode(old.parentCode()) : candidate;
                for (int i = 0; i < merged.size(); i++) {
                    if (old.equals(MultimodalFactParentCodeNormalizer.resolveParent(merged.get(i), first)))
                        merged.set(i, merged.get(i).withParentCode(replacement.code()));
                }
                int index = merged.indexOf(old);
                if (index >= 0) merged.set(index, annotate(replacement, notes));
            } else if (previous.isEmpty() && first.stream().noneMatch(f -> f.equals(candidate))) {
                merged.add(verified ? candidate : annotate(candidate, List.of("SOURCE_RECHECK_UNVERIFIED")));
            } else if (previous.size() == 1) {
                int index = merged.indexOf(previous.getFirst());
                if (index >= 0) merged.set(index, annotate(merged.get(index),
                        List.of("SOURCE_RECHECK_FAILED:" + candidate.quotedText())));
            }
        }
        List<MultimodalScoreFact> combined = diagnostics.isEmpty() ? List.copyOf(merged)
                : merged.stream().map(fact -> annotate(fact, diagnostics)).toList();
        return MultimodalFactParentCodeNormalizer.normalize(SourceFactDeduplication.merge(combined, artifact));
    }

    private static String key(MultimodalScoreFact fact) {
        return fact.schemeId() + "|" + fact.scopeStatus() + "|" + fact.sectionTitle() + "|"
                + EvidenceText.normalize(fact.code().isBlank() ? fact.dimension() : fact.code()).text() + "|" + fact.parentCode();
    }

    private static MultimodalScoreFact annotate(MultimodalScoreFact fact, List<String> notes) {
        var current = fact.sourceMetadata();
        var diagnostics = new ArrayList<>(current.diagnostics());
        diagnostics.addAll(notes);
        var metadata = new ScoreSourceMetadata(current.modelRuleText().isBlank() ? fact.ruleText() : current.modelRuleText(),
                current.modelQuotedText().isBlank() ? fact.quotedText() : current.modelQuotedText(), current.sourceSpans(), diagnostics, current.modelCategory(), current.modelScoreType(), current.modelSourceClaim());
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), fact.weight(),
                fact.ruleText(), fact.scoreType(), fact.pageNumber(), fact.tableId(), fact.sourceCellIds(), fact.quotedText(),
                fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), metadata);
    }
}
