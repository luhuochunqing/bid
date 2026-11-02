package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.List;
import java.util.stream.Collectors;

/** Full source rule and raw provenance are one pure result; model claims stay separate. */
public final class OriginalRuleRestoration {
    private OriginalRuleRestoration() {}
    public record Result(String text, java.util.List<SourceSpan> spans, boolean closed) {
        public Result {
            spans = java.util.List.copyOf(spans);
        }
        public Result(String text, java.util.List<SourceSpan> spans) {
            this(text, spans, true);
        }
    }

    public static Result restore(MultimodalScoreFact fact, DocumentArtifact artifact, java.util.List<MultimodalScoreFact> facts) {
        return restore(fact, artifact, facts, null);
    }

    private static Result restore(MultimodalScoreFact fact, DocumentArtifact artifact,
            List<MultimodalScoreFact> facts, java.util.function.Function<MultimodalScoreFact, MultimodalScoreFact> parentOf) {
        var fallbackSpans = extractSpans(facts);
        var structural = LogicalRuleSourceUnit.resolve(artifact, fact);
        var cited = SourceSpanResolver.resolve(artifact, fact.sourceCellIds(), fallbackSpans);
        String citedRaw = cited.stream().map(SourceSpan::rawText).collect(Collectors.joining("\n"));
        String modelRule = fact.sourceMetadata() != null && !fact.sourceMetadata().modelRuleText().isBlank()
                ? fact.sourceMetadata().modelRuleText() : fact.ruleText();

        boolean ambiguousCellShare = facts != null && facts.stream().anyMatch(other ->
                !java.util.Objects.equals(other, fact)
                && java.util.Objects.equals(other.schemeId(), fact.schemeId())
                && other.sourceCellIds().stream().anyMatch(fact.sourceCellIds()::contains));

        if (structural.applicable() && !structural.closed()) {
            if (ambiguousCellShare) return new Result("", cited, false);
            String fallback = fallbackRuleText(citedRaw, modelRule, fact.quotedText());
            return new Result(fallback, cited, false);
        }
        var target = unit(fact);
        var peers = facts.stream().filter(other -> OriginalScoreEvidencePolicy.modelFactId(other)
                .equals(OriginalScoreEvidencePolicy.modelFactId(fact)) ||
                (parentOf == null ? java.util.Objects.equals(other.parentCode(), fact.parentCode())
                        : parentOf.apply(other) == parentOf.apply(fact))
                && java.util.Objects.equals(other.schemeId(), fact.schemeId())
                && java.util.Objects.equals(other.scopeStatus(), fact.scopeStatus())
                && (other.sourceCellIds().stream().anyMatch(fact.sourceCellIds()::contains)
                || LogicalRuleSourceUnit.sharesLogicalRow(artifact, fact, other)
                || OriginalRuleUnitText.precedingContinuation(citedRaw, target, unit(other)))).toList();
        var ids = new java.util.LinkedHashSet<String>();
        collectSourceIds(ids, structural, fact.sourceCellIds());
        var validPeers = new java.util.ArrayList<MultimodalScoreFact>();
        validPeers.add(fact);
        if (peers.size() > 1) for (var peer : peers) {
            if (java.util.Objects.equals(peer, fact)) continue;
            var peerUnit = LogicalRuleSourceUnit.resolve(artifact, peer);
            if (peerUnit.applicable() && !peerUnit.closed()) {
                if (structural.applicable() && structural.closed()) continue;
                if (ambiguousCellShare) return new Result("", cited, false);
                String fallback = fallbackRuleText(citedRaw, modelRule, fact.quotedText());
                return new Result(fallback, cited, false);
            }
            collectSourceIds(ids, peerUnit, peer.sourceCellIds());
            validPeers.add(peer);
        }
        var ordered = new java.util.ArrayList<String>();
        if (artifact != null) artifact.tables().forEach(t -> t.cells().forEach(c -> { if (ids.contains(c.cellId())) ordered.add(c.cellId()); }));
        ids.stream().filter(id -> !ordered.contains(id)).forEach(ordered::add);
        var peerSpans = extractSpans(validPeers);
        var spans = structural.applicable() && validPeers.size() == 1 ? structural.spans()
                : SourceSpanResolver.resolve(artifact, ordered, peerSpans);
        String raw = spans.stream().map(SourceSpan::rawText).collect(Collectors.joining("\n"));
        String rule = OriginalRuleUnitText.restore(raw, target, validPeers.stream()
                .map(OriginalRuleRestoration::unit).toList());
        boolean hasChildren = facts.stream().anyMatch(other -> parentOf != null ? parentOf.apply(other) == fact
                : !fact.code().isBlank() && fact.code().equals(other.parentCode()) && fact.schemeId().equals(other.schemeId()));
        if (rule.isBlank() && structural.applicable() && structural.closed() && validPeers.size() == 1
                && !hasChildren && LogicalRuleSourceUnit.isExactNamedRowAnchor(artifact, fact)) {
            rule = raw.strip();
        }
        boolean erased = !structural.applicable() && erasesNumberedMaterials(rule, modelRule);
        if (erased) {
            rule = "";
        }
        boolean closed = !structural.applicable() || structural.closed();
        return new Result(rule, spans, closed);
    }

    /** A unary descendant retains a source boundary already established for its ancestor. */
    static Result restoreForWeight(MultimodalScoreFact fact, DocumentArtifact artifact, List<MultimodalScoreFact> facts) {
        return restoreForWeight(fact, artifact, facts, node -> MultimodalFactParentCodeNormalizer.resolveParent(node, facts));
    }

    private static Result restoreForWeight(MultimodalScoreFact fact, DocumentArtifact artifact,
            List<MultimodalScoreFact> facts, java.util.function.Function<MultimodalScoreFact, MultimodalScoreFact> parentOf) {
        Result current = restore(fact, artifact, facts, parentOf);
        String quote = unit(fact).quote();
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<MultimodalScoreFact, Boolean>());
        var node = fact;
        while (current.closed() && !current.text().isBlank() && quote != null && !quote.isBlank()
                && visited.add(node)) {
            var parent = parentOf.apply(node);
            if (parent == null) break;
            if (parent.sourceCellIds().stream().anyMatch(fact.sourceCellIds()::contains)) {
                var bound = restore(parent, artifact, facts, parentOf);
                if (bound.closed() && !bound.text().isBlank() && !bound.text().equals(current.text())
                        && EvidenceText.normalize(current.text()).uniqueSpan(bound.text()).isPresent()
                        && EvidenceText.normalize(bound.text()).uniqueSpan(quote).isPresent()) {
                    current = new Result(bound.text(), current.spans(), true);
                }
            }
            node = parent;
        }
        return current;
    }

    /** Reuse the same located-unit restoration for deterministic facts without model inference. */
    static java.util.Map<ScoreFact, Result> restoreFacts(DocumentArtifact artifact, List<ScoreFact> facts) {
        var byId = facts.stream().collect(Collectors.toMap(ScoreFact::factId, f -> f, (first, ignored) -> first));
        List<MultimodalScoreFact> projected = facts.stream().map(f -> {
            var parent = byId.get(f.parentFactId());
            return new MultimodalScoreFact("", f.code(), parent == null ? "" : parent.code(), f.dimension(),
                    f.weight(), f.sourceText(), "", null, f.sourceTableId(), f.sourceCellIds(),
                    f.sourceText().isBlank() ? f.dimension() : f.sourceText(), f.confidence(), f.childAggregation(), "", "", f.sourceMetadata());
        }).toList();
        var result = new java.util.LinkedHashMap<ScoreFact, Result>();
        var parents = new java.util.IdentityHashMap<MultimodalScoreFact, MultimodalScoreFact>();
        var projectedById = new java.util.HashMap<String, MultimodalScoreFact>();
        for (int i = 0; i < facts.size(); i++) projectedById.put(facts.get(i).factId(), projected.get(i));
        for (int i = 0; i < facts.size(); i++) {
            var parent = projectedById.get(facts.get(i).parentFactId());
            if (parent != null) parents.put(projected.get(i), parent);
        }
        for (int i = 0; i < facts.size(); i++) {
            result.put(facts.get(i), restoreForWeight(projected.get(i), artifact, projected, parents::get));
        }
        return java.util.Map.copyOf(result);
    }

    private static String fallbackRuleText(String citedRaw, String modelRule, String quotedText) {
        if (citedRaw != null && !citedRaw.isBlank()) return citedRaw.strip();
        if (modelRule != null && !modelRule.isBlank()) return modelRule.strip();
        return quotedText != null && !quotedText.isBlank() ? quotedText.strip() : "";
    }

    private static final java.util.regex.Pattern NUMBERED_MATERIAL_PATTERN =
            java.util.regex.Pattern.compile("(?m)^\\s*(?:(?:\\d+|[一二三四五六七八九十]+)[、.）\\)\\]]|[（\\(\\[]\\d+[）\\)\\]])");

    private static boolean erasesNumberedMaterials(String rule, String modelRule) {
        if (rule == null || modelRule == null || rule.isBlank() || modelRule.isBlank()) return false;
        var matcher = NUMBERED_MATERIAL_PATTERN.matcher(modelRule);
        while (matcher.find()) {
            String marker = matcher.group().trim();
            if (!rule.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static java.util.List<SourceSpan> extractSpans(java.util.List<MultimodalScoreFact> facts) {
        if (facts == null || facts.isEmpty()) return List.of();
        var spans = new java.util.ArrayList<SourceSpan>();
        for (var f : facts) {
            if (f != null && f.sourceMetadata() != null && f.sourceMetadata().sourceSpans() != null) {
                f.sourceMetadata().sourceSpans().forEach(s -> {
                    if (s != null && spans.stream().noneMatch(ex -> ex.sourceId().equals(s.sourceId()))) {
                        spans.add(s);
                    }
                });
            }
        }
        return List.copyOf(spans);
    }

    private static void collectSourceIds(java.util.Set<String> ids, LogicalRuleSourceUnit.Resolution unit, List<String> fallbackIds) {
        if (unit.applicable()) unit.spans().forEach(span -> ids.add(span.sourceId()));
        else ids.addAll(fallbackIds);
    }

    private static OriginalRuleUnitText.Unit unit(MultimodalScoreFact fact) {
        // Apply the preserved quotation to every peer: anchors themselves quote the entire row.
        String quote = fact.sourceMetadata().diagnostics().contains("SOURCE_TEXT_RESTORED_FROM_ANCHOR")
                ? fact.sourceMetadata().modelQuotedText() : fact.quotedText();
        return new OriginalRuleUnitText.Unit(fact.code(), fact.dimension(), quote);
    }
}
