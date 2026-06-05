package com.xiyu.bid.bidmatch.domain;

import java.util.Locale;
import java.util.stream.Stream;

public final class BidMatchEvidenceScopePolicy {

    public BidMatchEvidenceScope fromTender(
            String title,
            String description,
            String tags,
            String region,
            String industry,
            String purchaserName
    ) {
        String normalizedIndustry = normalize(industry);
        String normalizedTags = firstTag(tags);
        return new BidMatchEvidenceScope(
                caseIndustryCode(title, description, tags, industry),
                firstNonBlank(normalizedTags, normalizedIndustry, normalize(purchaserName), normalize(region), normalize(title)),
                normalize(purchaserName),
                normalize(region)
        );
    }

    private String caseIndustryCode(String title, String description, String tags, String industry) {
        String searchText = Stream.of(industry, title, description, tags)
                .map(value -> value == null ? "" : value)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
        if (containsAny(searchText, "能源", "电力", "energy", "power")) {
            return "ENERGY";
        }
        if (containsAny(searchText, "交通", "运输", "轨道", "transport")) {
            return "TRANSPORTATION";
        }
        if (containsAny(searchText, "制造", "生产", "manufactur")) {
            return "MANUFACTURING";
        }
        if (containsAny(searchText, "地产", "房地产", "物业", "real estate")) {
            return "REAL_ESTATE";
        }
        if (containsAny(searchText, "基建", "基础设施", "园区建设", "infrastructure")) {
            return "INFRASTRUCTURE";
        }
        if (containsAny(searchText, "环保", "环境", "污水", "environment")) {
            return "ENVIRONMENTAL";
        }
        return null;
    }

    private boolean containsAny(String source, String... candidates) {
        return Stream.of(candidates).anyMatch(source::contains);
    }

    private String firstTag(String tags) {
        String normalizedTags = normalize(tags);
        if (normalizedTags == null) {
            return null;
        }
        return Stream.of(normalizedTags.split("[,，;；、\\s]+"))
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String firstNonBlank(String... values) {
        return Stream.of(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
