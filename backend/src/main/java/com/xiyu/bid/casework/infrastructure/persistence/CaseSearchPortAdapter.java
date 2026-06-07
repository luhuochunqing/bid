package com.xiyu.bid.casework.infrastructure.persistence;

import com.xiyu.bid.casework.application.port.CaseSearchPort;
import com.xiyu.bid.casework.domain.model.CaseSearchCriteria;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.repository.CaseRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CaseSearchPortAdapter implements CaseSearchPort {

    private final CaseRepository caseRepository;

    @Override
    public Page<Case> search(CaseSearchCriteria criteria, Pageable pageable) {
        return caseRepository.findAll(buildSearchSpecification(criteria), pageable);
    }

    @Override
    public List<Case> findRelatedCandidates(Long excludedCaseId, Pageable pageable) {
        return caseRepository.findAll(relatedCandidatesSpec(excludedCaseId), pageable).stream().toList();
    }

    @Override
    public List<String> findDistinctProductLines() {
        return caseRepository.findDistinctProductLines();
    }

    @Override
    public List<String> findDistinctStatuses() {
        return caseRepository.findDistinctStatuses();
    }

    @Override
    public List<String> findDistinctVisibilities() {
        return caseRepository.findDistinctVisibilities();
    }

    @Override
    public List<String> findDistinctTags() {
        return caseRepository.findDistinctTags();
    }

    @Override
    public Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        }

        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "popular" -> Sort.by(Sort.Order.desc("useCount"), Sort.Order.desc("viewCount"), Sort.Order.desc("id"));
            case "amountasc" -> Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("id"));
            case "amountdesc" -> Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("id"));
            case "oldest" -> Sort.by(Sort.Order.asc("publishedAt"), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        };
    }

    private Specification<Case> buildSearchSpecification(CaseSearchCriteria criteria) {
        return (root, query, cb) -> {
            query.distinct(true);

            if (criteria == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new java.util.ArrayList<>();

            if (hasText(criteria.keyword())) {
                String keywordPattern = likePattern(criteria.keyword());
                predicates.add(cb.or(
                        cb.like(lowerOrEmpty(root.get("searchDocument"), cb), keywordPattern, '\\'),
                        cb.like(lowerOrEmpty(root.get("title"), cb), keywordPattern, '\\'),
                        cb.like(lowerOrEmpty(root.get("customerName"), cb), keywordPattern, '\\'),
                        cb.like(lowerOrEmpty(root.get("productLine"), cb), keywordPattern, '\\'),
                        cb.like(lowerOrEmpty(root.get("archiveSummary"), cb), keywordPattern, '\\')
                ));
            }

            Case.Industry industry = parseEnum(Case.Industry.class, criteria.industry());
            if (hasText(criteria.industry())) {
                predicates.add(industry == null ? cb.disjunction() : cb.equal(root.get("industry"), industry));
            }

            if (hasText(criteria.productLine())) {
                predicates.add(cb.equal(lowerOrEmpty(root.get("productLine"), cb), normalize(criteria.productLine())));
            }

            Case.Outcome outcome = parseEnum(Case.Outcome.class, criteria.outcome());
            if (hasText(criteria.outcome())) {
                predicates.add(outcome == null ? cb.disjunction() : cb.equal(root.get("outcome"), outcome));
            }

            if (criteria.year() != null) {
                LocalDate start = LocalDate.of(criteria.year(), 1, 1);
                LocalDate end = start.withMonth(12).withDayOfMonth(31);
                predicates.add(cb.between(root.get("projectDate"), start, end));
            }

            if (criteria.amountMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), criteria.amountMin()));
            }

            if (criteria.amountMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), criteria.amountMax()));
            }

            if (criteria.tags() != null && !criteria.tags().isEmpty()) {
                Join<Case, String> tagsJoin = root.join("tags", JoinType.LEFT);
                predicates.add(lowerOrEmpty(tagsJoin, cb).in(
                        criteria.tags().stream()
                                .filter(this::hasText)
                                .map(this::normalize)
                                .toList()));
            }

            if (hasText(criteria.status())) {
                predicates.add(cb.equal(lowerOrEmpty(root.get("status"), cb), normalize(criteria.status())));
            }

            if (hasText(criteria.visibility())) {
                predicates.add(cb.equal(lowerOrEmpty(root.get("visibility"), cb), normalize(criteria.visibility())));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Case> relatedCandidatesSpec(Long excludedCaseId) {
        return (root, query, cb) -> {
            query.distinct(true);
            if (excludedCaseId == null) {
                return cb.conjunction();
            }
            return cb.notEqual(root.get("id"), excludedCaseId);
        };
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Expression<String> lowerOrEmpty(Expression<String> expression, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.lower(cb.coalesce(expression, ""));
    }

    private String likePattern(String value) {
        return "%" + escapeLike(normalize(value)) + "%";
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
