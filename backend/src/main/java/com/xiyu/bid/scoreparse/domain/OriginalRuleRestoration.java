package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.stream.Collectors;

/** Full source rule and raw provenance are one pure result; model claims stay separate. */
public final class OriginalRuleRestoration {
    private OriginalRuleRestoration() {}
    public record Result(String text, java.util.List<SourceSpan> spans) {
        public Result { spans = java.util.List.copyOf(spans); }
    }

    public static Result restore(MultimodalScoreFact fact, DocumentArtifact artifact, java.util.List<MultimodalScoreFact> facts) {
        var structural = LogicalRuleSourceUnit.resolve(artifact, fact);
        var cited = SourceSpanResolver.resolve(artifact, fact.sourceCellIds());
        if (structural.applicable() && !structural.closed()) return new Result("", cited);
        var target = unit(fact);
        String citedRaw=SourceSpanResolver.resolve(artifact,fact.sourceCellIds()).stream()
                .map(SourceSpan::rawText).collect(Collectors.joining("\n"));
        var peers=facts.stream().filter(other -> OriginalScoreEvidencePolicy.modelFactId(other)
                .equals(OriginalScoreEvidencePolicy.modelFactId(fact)) ||
                java.util.Objects.equals(other.parentCode(),fact.parentCode())
                && java.util.Objects.equals(other.schemeId(),fact.schemeId())
                && java.util.Objects.equals(other.scopeStatus(),fact.scopeStatus())
                && (other.sourceCellIds().stream().anyMatch(fact.sourceCellIds()::contains)
                || LogicalRuleSourceUnit.sharesLogicalRow(artifact, fact, other)
                || OriginalRuleUnitText.precedingContinuation(citedRaw,target,unit(other)))).toList();
        var ids=new java.util.LinkedHashSet<>(fact.sourceCellIds());
        if (structural.applicable()) structural.spans().forEach(span -> ids.add(span.sourceId()));
        var validPeers = new java.util.ArrayList<MultimodalScoreFact>();
        validPeers.add(fact);
        if (peers.size() > 1) for (var peer : peers) {
            if (java.util.Objects.equals(peer, fact)) continue;
            var peerUnit = LogicalRuleSourceUnit.resolve(artifact, peer);
            if (peerUnit.applicable() && !peerUnit.closed()) {
                if (structural.applicable() && structural.closed()) continue;
                return new Result("", cited);
            }
            ids.addAll(peer.sourceCellIds());
            peerUnit.spans().forEach(span -> ids.add(span.sourceId()));
            validPeers.add(peer);
        }
        var ordered=new java.util.ArrayList<String>();
        if(artifact!=null) artifact.tables().forEach(t -> t.cells().forEach(c -> {if(ids.contains(c.cellId())) ordered.add(c.cellId());}));
        ids.stream().filter(id -> !ordered.contains(id)).forEach(ordered::add);
        var spans = structural.applicable() && validPeers.size() == 1 ? structural.spans()
                : SourceSpanResolver.resolve(artifact, ordered);
        String raw = spans.stream().map(SourceSpan::rawText).collect(Collectors.joining("\n"));
        String rule = OriginalRuleUnitText.restore(raw,target,validPeers.stream()
                .map(OriginalRuleRestoration::unit).toList());
        return new Result(rule, spans);
    }

    private static OriginalRuleUnitText.Unit unit(MultimodalScoreFact fact) {
        // Apply the preserved quotation to every peer: anchors themselves quote the entire row.
        String quote = fact.sourceMetadata().diagnostics().contains("SOURCE_TEXT_RESTORED_FROM_ANCHOR")
                ? fact.sourceMetadata().modelQuotedText() : fact.quotedText();
        return new OriginalRuleUnitText.Unit(fact.code(), fact.dimension(), quote);
    }
}
