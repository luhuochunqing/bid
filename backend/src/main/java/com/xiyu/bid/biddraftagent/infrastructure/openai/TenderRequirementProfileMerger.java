package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.ScoringCriterion;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;

import java.util.ArrayList;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TenderRequirementProfileMerger {

    private TenderRequirementProfileMerger() {
    }

    static TenderRequirementProfile merge(List<TenderRequirementProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return emptyProfile();
        }
        return new TenderRequirementProfile(
                firstNonBlank(profiles, TenderRequirementProfile::projectName),
                firstNonBlank(profiles, TenderRequirementProfile::tenderTitle),
                firstNonBlank(profiles, TenderRequirementProfile::tenderScope),
                firstNonBlank(profiles, TenderRequirementProfile::purchaserName),
                firstNonNull(profiles, TenderRequirementProfile::budget),
                firstNonBlank(profiles, TenderRequirementProfile::region),
                firstNonBlank(profiles, TenderRequirementProfile::industry),
                firstNonNull(profiles, TenderRequirementProfile::publishDate),
                firstNonNull(profiles, TenderRequirementProfile::deadline),
                collect(profiles, TenderRequirementProfile::qualificationRequirements),
                collect(profiles, TenderRequirementProfile::technicalRequirements),
                collect(profiles, TenderRequirementProfile::commercialRequirements),
                collect(profiles, TenderRequirementProfile::scoringCriteria),
                collectScoringCriteriaItems(profiles),
                firstNonBlank(profiles, TenderRequirementProfile::deadlineText),
                collect(profiles, TenderRequirementProfile::requiredMaterials),
                collect(profiles, TenderRequirementProfile::riskPoints),
                collect(profiles, TenderRequirementProfile::tags),
                collectItems(profiles)
        );
    }

    private static TenderRequirementProfile emptyProfile() {
        return new TenderRequirementProfile(null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                null, List.of(), List.of(), List.of(), List.of());
    }

    private static String firstNonBlank(
            List<TenderRequirementProfile> profiles,
            StringFieldExtractor extractor
    ) {
        return profiles.stream()
                .map(extractor::extract)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static <T> T firstNonNull(
            List<TenderRequirementProfile> profiles,
            ValueFieldExtractor<T> extractor
    ) {
        return profiles.stream()
                .map(extractor::extract)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private static List<String> collect(
            List<TenderRequirementProfile> profiles,
            ListFieldExtractor extractor
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        profiles.stream()
                .flatMap(profile -> extractor.extract(profile).stream())
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(value -> values.putIfAbsent(value, value));
        return new ArrayList<>(values.values());
    }

    private static List<TenderRequirementItemSnapshot> collectItems(List<TenderRequirementProfile> profiles) {
        Map<String, TenderRequirementItemSnapshot> items = new LinkedHashMap<>();
        profiles.stream()
                .flatMap(profile -> profile.items().stream())
                .forEach(item -> items.putIfAbsent(itemKey(item), item));
        return new ArrayList<>(items.values());
    }

        private static List<ScoringCriterion> collectScoringCriteriaItems(List<TenderRequirementProfile> profiles) {
        return profiles.stream()
                .flatMap(p -> p.scoringCriteriaItems().stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String itemKey(TenderRequirementItemSnapshot item) {
        return normalize(item.category()) + "|" + normalize(item.title()) + "|" + normalize(item.content());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    @FunctionalInterface
    private interface StringFieldExtractor {
        String extract(TenderRequirementProfile profile);
    }

    @FunctionalInterface
    private interface ListFieldExtractor {
        List<String> extract(TenderRequirementProfile profile);
    }

    @FunctionalInterface
    private interface ValueFieldExtractor<T> {
        T extract(TenderRequirementProfile profile);
    }
}
