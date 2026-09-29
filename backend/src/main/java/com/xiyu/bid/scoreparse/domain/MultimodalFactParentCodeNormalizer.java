package com.xiyu.bid.scoreparse.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Normalizes parent references within a scheme, including parent/child rows split across physical tables. */
public final class MultimodalFactParentCodeNormalizer {
    private MultimodalFactParentCodeNormalizer() {
    }

    public static List<MultimodalScoreFact> normalize(List<MultimodalScoreFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        Map<String, MultimodalScoreFact> canonicalCodes = new HashMap<>();
        Map<String, MultimodalScoreFact> schemeCodes = new HashMap<>();
        Map<String, MultimodalScoreFact> schemeWideCodes = new HashMap<>();
        for (MultimodalScoreFact fact : facts) {
            if (fact == null || fact.code().isBlank()) {
                continue;
            }
            canonicalCodes.merge(key(fact.tableId(), fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), fact.code()), fact,
                    MultimodalFactParentCodeNormalizer::mergeCandidate);
            if (!fact.schemeId().isBlank()) {
                schemeCodes.merge(key("", fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), fact.code()), fact,
                        MultimodalFactParentCodeNormalizer::mergeCandidate);
                schemeWideCodes.merge(key("", fact.schemeId(), fact.scopeStatus(), "", fact.code()), fact,
                        MultimodalFactParentCodeNormalizer::mergeCandidate);
            }
        }
        return facts.stream().map(fact -> {
            if (fact == null) return null;
            String parentCode = fact.parentCode();
            if (parentCode.isBlank() && !fact.code().isBlank()) {
                int lastDot = Math.max(fact.code().lastIndexOf('.'), fact.code().lastIndexOf('_'));
                if (lastDot > 0) {
                    String prefix = fact.code().substring(0, lastDot).trim();
                    if (canonicalCodes.containsKey(key(fact.tableId(), fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), prefix))
                            || schemeCodes.containsKey(key("", fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), prefix))
                            || schemeWideCodes.containsKey(key("", fact.schemeId(), fact.scopeStatus(), "", prefix))) {
                        parentCode = prefix;
                    }
                }
            }
            if (parentCode.isBlank()) return fact;
            fact = parentCode.equals(fact.parentCode()) ? fact : fact.withParentCode(parentCode);
            String canonical = canonicalParentCode(fact, canonicalCodes, schemeCodes, schemeWideCodes);
            if (canonical == null && structuralParentReference(fact)) {
                // A text chunk can contain children while the parent heading is
                // outside the chunk and therefore absent from the model pool.
                // Keep the child as a top-level scoring unit instead of creating
                // a dangling relation that can never be source-verified.
                return fact.withParentCode("");
            }
            return canonical == null || canonical.equals(fact.parentCode()) ? fact : fact.withParentCode(canonical);
        }).toList();
    }

    /** Resolves a parent by source scheme and code, using section title only to disambiguate. */
    public static MultimodalScoreFact resolveParent(MultimodalScoreFact child, List<MultimodalScoreFact> facts) {
        if (child == null || child.parentCode().isBlank() || facts == null) return null;
        List<MultimodalScoreFact> candidates = facts.stream().filter(parent -> parent != null
                && parent.code().equals(compact(child.parentCode()))
                && parent.scopeStatus().equals(child.scopeStatus())
                && parent.schemeId().equals(child.schemeId())).toList();
        List<MultimodalScoreFact> sameSection = candidates.stream()
                .filter(parent -> compact(parent.sectionTitle()).equals(compact(child.sectionTitle()))).toList();
        if (sameSection.size() == 1) return sameSection.get(0);
        if (sameSection.size() > 1) {
            List<MultimodalScoreFact> positive = sameSection.stream()
                    .filter(p -> p.weight() != null && p.weight().compareTo(java.math.BigDecimal.ZERO) > 0).toList();
            if (positive.size() == 1) return positive.get(0);
            return null;
        }
        if (!child.schemeId().isBlank()) {
            if (candidates.size() == 1) return candidates.get(0);
            List<MultimodalScoreFact> positive = candidates.stream()
                    .filter(p -> p.weight() != null && p.weight().compareTo(java.math.BigDecimal.ZERO) > 0).toList();
            if (positive.size() == 1) return positive.get(0);
        }
        return null;
    }

    private static boolean structuralParentReference(MultimodalScoreFact fact) {
        String parent = compact(fact.parentCode());
        String code = compact(fact.code());
        return !parent.isBlank() && !code.isBlank()
                && (code.startsWith(parent + "_") || code.startsWith(parent + "."));
    }

    private static String canonicalParentCode(MultimodalScoreFact fact, Map<String, MultimodalScoreFact> canonicalCodes,
                                              Map<String, MultimodalScoreFact> schemeCodes,
                                              Map<String, MultimodalScoreFact> schemeWideCodes) {
        String raw = fact.parentCode().trim();
        String direct = unique(canonicalCodes.get(key(fact.tableId(), fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), raw)));
        if (direct != null) {
            return direct;
        }
        String[] segments = raw.split("[/\\\\]");
        for (int i = segments.length - 1; i >= 0; i--) {
            String candidate = segments[i].trim();
            if (!candidate.isBlank()) {
                String match = unique(canonicalCodes.get(key(fact.tableId(), fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), candidate)));
                if (match != null) {
                    return match;
                }
            }
        }
        if (!fact.schemeId().isBlank()) {
            String schemeMatch = unique(schemeCodes.get(key("", fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), raw)));
            if (schemeMatch != null) return schemeMatch;
            for (int i = segments.length - 1; i >= 0; i--) {
                String candidate = segments[i].trim();
                if (!candidate.isBlank()) {
                    schemeMatch = unique(schemeCodes.get(key("", fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), candidate)));
                    if (schemeMatch != null) return schemeMatch;
                    schemeMatch = unique(schemeWideCodes.get(key("", fact.schemeId(), fact.scopeStatus(), "", candidate)));
                    if (schemeMatch != null) return schemeMatch;
                }
            }
        }
        return null;
    }

    private static final MultimodalScoreFact AMBIGUOUS_FACT = new MultimodalScoreFact(
            "", "\u0000AMBIGUOUS\u0000", "", "", null, "", "", null, "", List.of(), "", 0.0,
            ChildAggregation.SUM, "", "", ScoreSourceMetadata.EMPTY);

    private static MultimodalScoreFact mergeCandidate(MultimodalScoreFact existing, MultimodalScoreFact incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        if (isAmbiguous(existing) || isAmbiguous(incoming)) {
            return AMBIGUOUS_FACT;
        }
        boolean existingPositive = existing.weight() != null && existing.weight().compareTo(java.math.BigDecimal.ZERO) > 0;
        boolean incomingPositive = incoming.weight() != null && incoming.weight().compareTo(java.math.BigDecimal.ZERO) > 0;
        if (existingPositive && !incomingPositive) {
            return existing;
        }
        if (!existingPositive && incomingPositive) {
            return incoming;
        }
        return AMBIGUOUS_FACT;
    }

    private static boolean isAmbiguous(MultimodalScoreFact fact) {
        return fact == AMBIGUOUS_FACT || (fact != null && "\u0000AMBIGUOUS\u0000".equals(fact.code()));
    }

    private static String unique(MultimodalScoreFact value) {
        return value == null || isAmbiguous(value) ? null : value.code();
    }

    private static String key(String tableId, String schemeId, String scopeStatus, String sectionTitle, String code) {
        return compact(tableId) + "\u0000" + compact(schemeId) + "\u0000" + compact(scopeStatus)
                + "\u0000" + compact(sectionTitle) + "\u0000" + compact(code);
    }

    /** Whitespace-insensitive form codes are compared in; package-private so fact identity matches it. */
    static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
