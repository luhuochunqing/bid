package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TenderSpecification {

    private static final String CRM_SOURCE_LABEL = Tender.SourceType.CRM_OPPORTUNITY.getLabel();
    private static final String LEGACY_CRM_SOURCE_LABEL = "CRM 创建";

    private TenderSpecification() {
    }

    public static Specification<Tender> byCriteria(TenderSearchCriteria criteria) {
        TenderSearchCriteria safeCriteria = criteria == null ? TenderSearchCriteria.empty() : criteria;
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(safeCriteria.getKeyword())) {
                String pattern = containsPattern(safeCriteria.getKeyword());
                predicates.add(criteriaBuilder.like(root.get("searchTextNormalized"), pattern));
            }

            if (safeCriteria.getStatus() != null && !safeCriteria.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(safeCriteria.getStatus()));
            }
            if (safeCriteria.getSource() != null && !safeCriteria.getSource().isEmpty()) {
                List<String> normalizedSources = normalizeSourceValues(safeCriteria.getSource());
                if (!normalizedSources.isEmpty()) {
                    predicates.add(root.get("sourceNormalized").in(normalizedSources));
                }
            }
            if (safeCriteria.getSourceType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceType"), safeCriteria.getSourceType()));
            }
            addStringEquals(predicates, criteriaBuilder, root.get("regionNormalized"), safeCriteria.getRegion());
            addStringEquals(predicates, criteriaBuilder, root.get("industryNormalized"), safeCriteria.getIndustry());
            addStringContains(predicates, criteriaBuilder, root.get("purchaserNameNormalized"), safeCriteria.getPurchaserName());
            addStringEquals(predicates, criteriaBuilder, root.get("purchaserHashNormalized"), safeCriteria.getPurchaserHash());

            if (safeCriteria.getBudgetMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("budget"), safeCriteria.getBudgetMin()));
            }
            if (safeCriteria.getBudgetMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("budget"), safeCriteria.getBudgetMax()));
            }
            if (safeCriteria.getDeadlineFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("deadline"), safeCriteria.getDeadlineFrom()));
            }
            if (safeCriteria.getDeadlineTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("deadline"), safeCriteria.getDeadlineTo()));
            }
            if (safeCriteria.getPublishDateFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("publishDate"), safeCriteria.getPublishDateFrom()));
            }
            if (safeCriteria.getPublishDateTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("publishDate"), safeCriteria.getPublishDateTo()));
            }
            if (safeCriteria.getAiScoreMin() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("aiScore"), safeCriteria.getAiScoreMin()));
            }
            if (safeCriteria.getAiScoreMax() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("aiScore"), safeCriteria.getAiScoreMax()));
            }

            if (safeCriteria.getCustomerType() != null && !safeCriteria.getCustomerType().isEmpty()) {
                predicates.add(root.get("customerType").in(safeCriteria.getCustomerType()));
            }
            if (safeCriteria.getPriority() != null && !safeCriteria.getPriority().isEmpty()) {
                predicates.add(root.get("priority").in(safeCriteria.getPriority()));
            }

            addStringEquals(predicates, criteriaBuilder, root.get("projectType"), safeCriteria.getProjectType());
            if (safeCriteria.getProjectManagerId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("projectManagerId"), safeCriteria.getProjectManagerId()));
            }
            if (safeCriteria.getBiddingPersonId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("biddingPersonId"), safeCriteria.getBiddingPersonId()));
            }
            if (safeCriteria.getCreatorId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("creatorId"), safeCriteria.getCreatorId()));
            }

            if (safeCriteria.getBidOpeningTimeFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("bidOpeningTime"), safeCriteria.getBidOpeningTimeFrom()));
            }
            if (safeCriteria.getBidOpeningTimeTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("bidOpeningTime"), safeCriteria.getBidOpeningTimeTo()));
            }
            if (safeCriteria.getRegistrationDeadlineFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("registrationDeadline"), safeCriteria.getRegistrationDeadlineFrom()));
            }
            if (safeCriteria.getRegistrationDeadlineTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("registrationDeadline"), safeCriteria.getRegistrationDeadlineTo()));
            }
            if (safeCriteria.getUpdatedSince() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("updatedAt"), safeCriteria.getUpdatedSince()));
            }
            if (safeCriteria.getCreatedAtFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), safeCriteria.getCreatedAtFrom()));
            }
            if (safeCriteria.getCreatedAtTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), safeCriteria.getCreatedAtTo()));
            }

            if (query != null) {
                query.orderBy(
                        criteriaBuilder.desc(root.get("publishDate")),
                        criteriaBuilder.desc(root.get("createdAt"))
                );
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static List<String> normalizeSourceValues(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!hasText(value)) {
                continue;
            }
            String item = normalize(value);
            normalized.add(item);
            String crm = normalize(CRM_SOURCE_LABEL);
            String legacyCrm = normalize(LEGACY_CRM_SOURCE_LABEL);
            if (crm.equals(item) || legacyCrm.equals(item)) {
                normalized.add(crm);
                normalized.add(legacyCrm);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static void addStringEquals(List<Predicate> predicates, CriteriaBuilder cb, Expression<String> path, String value) {
        if (hasText(value)) {
            predicates.add(cb.equal(path, normalize(value)));
        }
    }

    private static void addStringContains(List<Predicate> predicates, CriteriaBuilder cb, Expression<String> path, String value) {
        if (hasText(value)) {
            predicates.add(cb.like(path, containsPattern(value)));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String containsPattern(String value) {
        return "%" + normalize(value) + "%";
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
