package com.xiyu.bid.scoreparse.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Normalizes model parent references to the canonical parent code in the same table. */
public final class MultimodalFactParentCodeNormalizer {
    private MultimodalFactParentCodeNormalizer() {
    }

    public static List<MultimodalScoreFact> normalize(List<MultimodalScoreFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        Map<String, String> canonicalCodes = new HashMap<>();
        for (MultimodalScoreFact fact : facts) {
            if (fact == null || fact.code().isBlank()) {
                continue;
            }
            canonicalCodes.merge(key(fact.tableId(), fact.sectionTitle(), fact.code()), fact.code(),
                    (existing, ignored) -> "\u0000AMBIGUOUS\u0000");
        }
        return facts.stream().map(fact -> {
            if (fact == null || fact.parentCode().isBlank()) {
                return fact;
            }
            String canonical = canonicalParentCode(fact, canonicalCodes);
            return canonical == null || canonical.equals(fact.parentCode())
                    ? fact
                    : new MultimodalScoreFact(fact.sectionTitle(), fact.code(), canonical, fact.dimension(),
                    fact.weight(), fact.ruleText(), fact.scoreType(), fact.pageNumber(), fact.tableId(),
                    fact.sourceCellIds(), fact.quotedText(), fact.confidence(), fact.childAggregation());
        }).toList();
    }

    private static String canonicalParentCode(MultimodalScoreFact fact, Map<String, String> canonicalCodes) {
        String raw = fact.parentCode().trim();
        String direct = unique(canonicalCodes.get(key(fact.tableId(), fact.sectionTitle(), raw)));
        if (direct != null) {
            return direct;
        }
        String[] segments = raw.split("[/\\\\]");
        for (int i = segments.length - 1; i >= 0; i--) {
            String candidate = segments[i].trim();
            if (!candidate.isBlank()) {
                String match = unique(canonicalCodes.get(key(fact.tableId(), fact.sectionTitle(), candidate)));
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String unique(String value) {
        return value == null || value.startsWith("\u0000AMBIGUOUS\u0000") ? null : value;
    }

    private static String key(String tableId, String sectionTitle, String code) {
        return compact(tableId) + "\u0000" + compact(sectionTitle) + "\u0000" + compact(code);
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }
}
