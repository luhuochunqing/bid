package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.LinkedHashSet;
import java.util.List;

/** Preserves source-proven continuation text when the deterministic grid loses row metadata. */
final class ShadowLogicalRowRestoration {
    private ShadowLogicalRowRestoration() { }

    static List<ScoreFact> restore(DocumentArtifact artifact, List<ScoreFact> facts) {
        return facts.stream().map(fact -> restoreOne(artifact, facts, fact)).toList();
    }

    private static ScoreFact restoreOne(DocumentArtifact artifact, List<ScoreFact> facts, ScoreFact fact) {
        if (fact.code().isBlank() || !LogicalRuleSourceUnit.closed(artifact, fact.sourceCellIds())) return fact;
        var model = new MultimodalScoreFact("", fact.code(), "", fact.dimension(), fact.weight(), "", "",
                null, fact.sourceTableId(), fact.sourceCellIds(), "", fact.confidence());
        var unit = LogicalRuleSourceUnit.resolve(artifact, model);
        if (!unit.applicable() || !unit.closed() || unit.spans().isEmpty()) return fact;
        var missing = unit.spans().stream().filter(span -> !fact.sourceCellIds().contains(span.sourceId())).toList();
        if (missing.isEmpty()) return fact;
        if (facts.stream().filter(other -> !other.factId().equals(fact.factId()))
                .anyMatch(other -> unit.spans().stream().anyMatch(span -> other.sourceCellIds().contains(span.sourceId())))) return fact;
        var present = unit.spans().stream().filter(span -> fact.sourceCellIds().contains(span.sourceId())).toList();
        if (present.size() != 1) return fact;
        String first = present.getFirst().rawText();
        int pos = fact.sourceText().indexOf(first);
        if (first.isBlank() || pos < 0 || fact.sourceText().indexOf(first, pos + first.length()) >= 0) return fact;
        String rule = unit.spans().stream().map(SourceSpan::rawText).collect(java.util.stream.Collectors.joining("\n"));
        String text = fact.sourceText().substring(0, pos) + rule + fact.sourceText().substring(pos + first.length());
        var ids = new LinkedHashSet<>(fact.sourceCellIds());
        unit.spans().forEach(span -> ids.add(span.sourceId()));
        return new ScoreFact(fact.factId(), fact.sourceTableId(), List.copyOf(ids), text, fact.code(), fact.dimension(),
                fact.weight(), fact.weightEvidence(), fact.scoreRule(), fact.parentFactId(), fact.extractionMethod(),
                fact.confidence(), fact.validationStatus(), fact.childAggregation(), fact.sourceMetadata());
    }
}
