package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Text-only facts become scoring units without running geometry restoration. */
public final class PrimaryScoreFactPolicy {
    private PrimaryScoreFactPolicy() {}

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/compliance/domain/policy/CaPlatformNamePolicy.java — CA平台名称候选与评分事实核验的输入输出及领域职责不同。
    public static List<ScoreCandidate> candidates(List<MultimodalScoreFact> input, String text) {
        if (input == null || input.isEmpty()) return List.of();
        List<MultimodalScoreFact> facts = MultimodalFactParentCodeNormalizer.normalize(input);
        for (int i = 0; i < input.size(); i++) {
            if (!input.get(i).parentCode().isBlank() && facts.get(i).parentCode().isBlank()) return List.of();
        }
        Set<String> identities = new HashSet<>();
        Set<String> schemes = new HashSet<>();
        for (var fact : facts) {
            if (fact.weight() == null || fact.weight().signum() < 0 || fact.dimension().isBlank()
                    || "OUT_OF_SCHEME".equals(fact.scopeStatus())) return List.of();
            if (!fact.schemeId().isBlank()) schemes.add(fact.schemeId());
            String identity = fact.code().isBlank() ? TextInclusionVerifier.normalize(fact.dimension()) : fact.code();
            if (!identities.add(identity) || schemes.size() > 1) return List.of();
            if (!fact.parentCode().isBlank() && MultimodalFactParentCodeNormalizer.resolveParent(fact, facts) == null)
                return List.of();
        }
        // A balanced leaf total cannot hide an incomplete or ambiguous parent group.
        for (var parent : facts) {
            var children = facts.stream().filter(f -> !f.parentCode().isBlank()
                    && parent.equals(MultimodalFactParentCodeNormalizer.resolveParent(f, facts))).toList();
            if (children.isEmpty()) continue;
            if (parent.childAggregation() != ChildAggregation.SUM) return List.of();
            BigDecimal subtotal = children.stream().map(MultimodalScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (subtotal.compareTo(parent.weight()) != 0) return List.of();
        }
        Set<String> parentCodes = facts.stream().map(MultimodalScoreFact::parentCode)
                .filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
        var units = facts.stream().filter(f -> !parentCodes.contains(f.code()) && f.weight().signum() > 0).toList();
        if (units.isEmpty()) return List.of();
        EvidenceText source = EvidenceText.normalize(text);
        var spans = new java.util.LinkedHashMap<MultimodalScoreFact, EvidenceText.Span>();
        for (var fact : units) {
            var span = source.uniqueSpan(fact.ruleText()).orElse(null);
            if (span == null || !TextInclusionVerifier.verify(fact.dimension(), fact.ruleText()).verified()
                    || spans.values().stream().anyMatch(used -> span.start() < used.end() && used.start() < span.end())
                    || !OriginalScoreWeightEvidencePolicy.matchesTextMaximum(fact.ruleText(), fact.weight())
                    || fact.weight().stripTrailingZeros().scale() > 2) return List.of();
            spans.put(fact, span);
        }
        if (spans.values().stream().anyMatch(span -> !closedTextSuffix(text, span, spans.values()))) return List.of();
        return units.stream().map(f -> {
            var span = spans.get(f);
            String original = text.substring(span.start(), span.end());
            return new ScoreCandidate(f.code(), f.dimension(), original, f.weight(),
                    new ScoreTypeClassificationPolicy().classify(original, f.scoreType()), null, original,
                    "text:" + span.start() + "-" + span.end(), "DUAL_TRACK_PRIMARY");
        }).toList();
    }
    private static boolean closedTextSuffix(String text, EvidenceText.Span span,
                                            java.util.Collection<EvidenceText.Span> units) {
        var paragraph = java.util.regex.Pattern.compile("\\r?\\n[\\t ]*\\r?\\n").matcher(text);
        int end = paragraph.find(span.end()) ? paragraph.start() : text.length();
        int next = units.stream().filter(other -> other.start() >= span.end())
                .mapToInt(EvidenceText.Span::start).min().orElse(end);
        // A first sentence is not a complete rule if uncited prose follows in the same block.
        return text.substring(span.end(), Math.min(end, next)).matches("[\\s\\p{P}\\p{Z}]*");
    }
}
