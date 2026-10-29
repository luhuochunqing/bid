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
                java.util.Objects.equals(other.parentCode(), fact.parentCode())
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
        boolean hasChildren = facts.stream().anyMatch(other -> !fact.code().isBlank()
                && fact.code().equals(other.parentCode()) && fact.schemeId().equals(other.schemeId()));
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
