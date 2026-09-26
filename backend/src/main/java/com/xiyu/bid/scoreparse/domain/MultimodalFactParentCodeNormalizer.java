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
        Map<String, String> canonicalCodes = new HashMap<>();
        Map<String, String> schemeCodes = new HashMap<>();
        Map<String, String> schemeWideCodes = new HashMap<>();
        for (MultimodalScoreFact fact : facts) {
            if (fact == null || fact.code().isBlank()) {
                continue;
            }
            canonicalCodes.merge(key(fact.tableId(), fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), fact.code()), fact.code(),
                    (existing, ignored) -> "\u0000AMBIGUOUS\u0000");
            if (!fact.schemeId().isBlank()) {
                schemeCodes.merge(key("", fact.schemeId(), fact.scopeStatus(), fact.sectionTitle(), fact.code()), fact.code(),
                        (existing, ignored) -> "\u0000AMBIGUOUS\u0000");
                schemeWideCodes.merge(key("", fact.schemeId(), fact.scopeStatus(), "", fact.code()), fact.code(),
                        (existing, ignored) -> "\u0000AMBIGUOUS\u0000");
            }
        }
        return facts.stream().map(fact -> {
            if (fact == null || fact.parentCode().isBlank()) {
                return fact;
            }
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
        if (!sameSection.isEmpty()) return null;
        return !child.schemeId().isBlank() && candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean structuralParentReference(MultimodalScoreFact fact) {
        String parent = compact(fact.parentCode());
        String code = compact(fact.code());
        return !parent.isBlank() && !code.isBlank()
                && (code.startsWith(parent + "_") || code.startsWith(parent + "."));
    }

    private static String canonicalParentCode(MultimodalScoreFact fact, Map<String, String> canonicalCodes,
                                              Map<String, String> schemeCodes,
                                              Map<String, String> schemeWideCodes) {
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

    private static String unique(String value) {
        return value == null || value.startsWith("\u0000AMBIGUOUS\u0000") ? null : value;
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
