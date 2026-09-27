package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable prompt anchors made from original rows before the model is called. */
public final class SourceAnchorCatalog {
    private SourceAnchorCatalog() {}
    public record Anchor(String id, String schemeId, String sectionTitle, String tableId,
                         List<String> sourceCellIds, String rawText, List<SourceSpan> sourceSpans) {}

    public static List<Anchor> build(DocumentArtifact artifact) {
        if (artifact == null) return List.of();
        Map<String, List<SourceSpan>> rows = new LinkedHashMap<>();
        Map<String, String> sections = new LinkedHashMap<>();
        for (var table : artifact.tables()) {
            String scheme = OriginalScoringContext.schemeTitleForTable(table);
            if (scheme.isBlank()) continue;
            for (var cell : table.cells()) {
                if (cell.row() == null || cell.rawText() == null || cell.rawText().isBlank()
                        || Boolean.TRUE.equals(cell.isMergedContinue())) continue;
                String row = cell.logicalRowId().isBlank() ? table.tableId() + "-r" + cell.row() : cell.logicalRowId();
                // A logical ID never joins different files or sections.
                var meta = artifact.documentFor(cell.cellId());
                String key = (meta == null ? "" : meta.documentId()) + "|" + scheme + "|" + row;
                rows.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .addAll(SourceSpanResolver.resolve(artifact, List.of(cell.cellId())));
                sections.put(key, table.sourceSection());
            }
        }
        List<Anchor> anchors = new ArrayList<>();
        rows.forEach((key, spans) -> {
            if (spans.isEmpty()) return;
            var first = spans.getFirst();
            anchors.add(new Anchor("anchor:" + first.sourceId(), first.schemeId(), sections.get(key), first.tableId(),
                    spans.stream().map(SourceSpan::sourceId).toList(),
                    spans.stream().map(SourceSpan::rawText).collect(java.util.stream.Collectors.joining("")),
                    List.copyOf(spans)));
        });
        for (var block : artifact.textBlocks()) {
            if (!block.isVerifiable()) continue;
            var spans = SourceSpanResolver.resolve(artifact, List.of(block.locator()));
            if (!spans.isEmpty()) anchors.add(new Anchor("anchor:" + block.blockId(),
                    OriginalScoringContext.schemeId(block.sourceSection()), block.sourceSection(), "",
                    List.of(block.locator()), block.text(), spans));
        }
        return List.copyOf(anchors);
    }

    public static MultimodalScoreFact restore(MultimodalScoreFact fact, List<Anchor> anchors) {
        List<String> ids = fact.sourceCellIds().stream().filter(id -> id.startsWith("anchor:")).toList();
        if (ids.isEmpty()) return fact;
        List<Anchor> matches = anchors.stream().filter(anchor -> ids.contains(anchor.id())).toList();
        boolean valid = ids.size() == 1 && fact.sourceCellIds().size() == 1 && matches.size() == 1
                && (fact.schemeId().isBlank() || fact.schemeId().equals(matches.getFirst().schemeId()));
        if (!valid) return copy(fact, fact.tableId(), fact.sourceCellIds(), fact.pageNumber(), fact.quotedText(),
                new ScoreSourceMetadata(fact.ruleText(), fact.quotedText(), List.of(), List.of("SOURCE_ANCHOR_INVALID"),
                        category(fact), fact.scoreType(), sourceClaim(fact)));
        var anchor = matches.getFirst();
        return copy(fact, anchor.tableId(), anchor.sourceCellIds(), anchor.sourceSpans().getFirst().pageNumber(),
                anchor.rawText(), new ScoreSourceMetadata(fact.ruleText(), fact.quotedText(), anchor.sourceSpans(),
                        fact.quotedText().equals(anchor.rawText()) ? List.of() : List.of("SOURCE_TEXT_RESTORED_FROM_ANCHOR"), category(fact), fact.scoreType(), sourceClaim(fact)));
    }

    private static ModelSourceClaim sourceClaim(MultimodalScoreFact fact) {
        return fact.sourceMetadata().modelSourceClaim().sectionTitle().isBlank()
                ? ModelSourceClaim.from(fact) : fact.sourceMetadata().modelSourceClaim();
    }

    private static String category(MultimodalScoreFact fact) {
        return fact.sourceMetadata().modelCategory().isBlank() ? fact.dimension() : fact.sourceMetadata().modelCategory();
    }

    private static MultimodalScoreFact copy(MultimodalScoreFact fact, String table, List<String> ids,
                                            Integer page, String quote, ScoreSourceMetadata metadata) {
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(),
                fact.weight(), fact.ruleText(), fact.scoreType(), page, table, ids, quote, fact.confidence(),
                fact.childAggregation(), fact.schemeId(), fact.scopeStatus(), metadata);
    }
}
