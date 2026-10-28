package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Promotes a closed full source rule only when the existing restoration proves the whole standalone row. */
final class SourceRuleEvidenceExpansion {
    private SourceRuleEvidenceExpansion() { }

    static List<MultimodalScoreFact> expand(List<MultimodalScoreFact> facts, DocumentArtifact artifact) {
        if (artifact == null) return facts;
        return facts.stream().map(f -> expandOne(f, facts, artifact)).toList();
    }

    private static MultimodalScoreFact expandOne(MultimodalScoreFact fact, List<MultimodalScoreFact> facts,
                                                  DocumentArtifact artifact) {
        if (!LogicalRuleSourceUnit.isExactNamedRowAnchor(artifact, fact)
                || !LogicalRuleSourceUnit.closed(artifact, fact.sourceCellIds())) return fact;
        boolean shared = facts.stream().filter(other -> other != fact).anyMatch(other ->
                (!fact.code().isBlank() && fact.code().equals(other.parentCode())
                        && Objects.equals(fact.schemeId(), other.schemeId()))
                || LogicalRuleSourceUnit.sharesLogicalRow(artifact, fact, other));
        if (shared) return fact;
        var unit = LogicalRuleSourceUnit.resolve(artifact, fact);
        if (!unit.applicable() || !unit.closed() || unit.spans().isEmpty()) return fact;
        var restored = OriginalRuleRestoration.restore(fact, artifact, facts);
        String raw = unit.spans().stream().map(SourceSpan::rawText).collect(Collectors.joining("\n")).strip();
        if (raw.isBlank() || !raw.equals(restored.text())) return fact;
        var ids = new LinkedHashSet<>(fact.sourceCellIds());
        unit.spans().forEach(span -> ids.add(span.sourceId()));
        if (!LogicalRuleSourceUnit.closed(artifact, List.copyOf(ids))) return fact;
        return new MultimodalScoreFact(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(),
                fact.weight(), fact.ruleText(), fact.scoreType(), fact.pageNumber(), fact.tableId(), List.copyOf(ids),
                fact.quotedText(), fact.confidence(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus(),
                fact.sourceMetadata());
    }
}
