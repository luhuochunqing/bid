// checkstyle:off
package com.xiyu.bid.performance.infrastructure.persistence.spec;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PerformanceRecordSpecification {

    private PerformanceRecordSpecification() {}

    public static Specification<PerformanceRecordEntity> build(
            PerformanceSearchCriteria criteria, PerformanceAlertConfig config) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // keyword
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String kw = "%" + criteria.keyword() + "%";
                predicates.add(cb.or(
                    cb.like(root.get("contractName"), kw),
                    cb.like(root.get("signingEntity"), kw),
                    cb.like(root.get("groupCompany"), kw)));
            }

            // customerTypes (multi-select)
            if (criteria.customerTypes() != null && !criteria.customerTypes().isEmpty()) {
                List<CustomerType> types = criteria.customerTypes().stream()
                    .map(s -> parseEnum(CustomerType.class, "客户类型", s))
                    .toList();
                predicates.add(root.get("customerType").in(types));
            }

            // projectTypes (multi-select)
            if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
                List<ProjectType> types = criteria.projectTypes().stream()
                    .map(s -> parseEnum(ProjectType.class, "项目类型", s))
                    .toList();
                predicates.add(root.get("projectType").in(types));
            }

            // customerLevels (multi-select)
            if (criteria.customerLevels() != null && !criteria.customerLevels().isEmpty()) {
                List<CustomerLevel> levels = criteria.customerLevels().stream()
                    .map(s -> parseEnum(CustomerLevel.class, "客户级别", s))
                    .toList();
                predicates.add(root.get("customerLevel").in(levels));
            }

            // territory
            if (criteria.territory() != null && !criteria.territory().isBlank()) {
                predicates.add(cb.like(root.get("territory"), "%" + criteria.territory() + "%"));
            }

            // signing date range
            if (criteria.signingDateStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("signingDate"), criteria.signingDateStart()));
            }
            if (criteria.signingDateEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("signingDate"), criteria.signingDateEnd()));
            }

            // expiry date range
            if (criteria.expiryDateStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), criteria.expiryDateStart()));
            }
            if (criteria.expiryDateEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), criteria.expiryDateEnd()));
            }

            // hasBidNotice
            if (criteria.hasBidNotice() != null) {
                predicates.add(cb.equal(root.get("hasBidNotice"), criteria.hasBidNotice()));
            }

            // projectManagerKeyword
            if (criteria.projectManagerKeyword() != null && !criteria.projectManagerKeyword().isBlank()) {
                predicates.add(cb.like(root.get("xiyuProjectManager"), "%" + criteria.projectManagerKeyword() + "%"));
            }

            // status (multi-select, computed)
            if (criteria.statuses() != null && !criteria.statuses().isEmpty()) {
                LocalDate today = LocalDate.now();
                LocalDate expiringDateSOE = today.plusDays(config.alertDaysSoe());
                LocalDate expiringDateDefault = today.plusDays(config.alertDaysDefault());

                List<Predicate> statusPreds = new ArrayList<>();
                for (String status : criteria.statuses()) {
                    statusPreds.add(buildStatusPredicate(root, cb, status, today, expiringDateSOE, expiringDateDefault));
                }
                predicates.add(cb.or(statusPreds.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> clazz, String label, String value) {
        try {
            return Enum.valueOf(clazz, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的" + label + ": " + value);
        }
    }

    private static Predicate buildStatusPredicate(
            jakarta.persistence.criteria.Root<PerformanceRecordEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String status, LocalDate today, LocalDate expiringDateSOE, LocalDate expiringDateDefault) {
        return switch (status) {
            case "EXPIRED" -> cb.lessThan(root.get("expiryDate"), today);
            case "EXPIRING" -> cb.and(
                cb.greaterThanOrEqualTo(root.get("expiryDate"), today),
                cb.or(
                    cb.and(cb.equal(root.get("customerType"), CustomerType.CENTRAL_SOE),
                           cb.lessThanOrEqualTo(root.get("expiryDate"), expiringDateSOE)),
                    cb.and(cb.notEqual(root.get("customerType"), CustomerType.CENTRAL_SOE),
                           cb.lessThanOrEqualTo(root.get("expiryDate"), expiringDateDefault))));
            case "IN_PERFORMANCE" -> cb.or(
                cb.isNull(root.get("expiryDate")),
                cb.and(
                    cb.greaterThanOrEqualTo(root.get("expiryDate"), today),
                    cb.or(
                        cb.and(cb.equal(root.get("customerType"), CustomerType.CENTRAL_SOE),
                               cb.greaterThan(root.get("expiryDate"), expiringDateSOE)),
                        cb.and(cb.notEqual(root.get("customerType"), CustomerType.CENTRAL_SOE),
                               cb.greaterThan(root.get("expiryDate"), expiringDateDefault)))));
            default -> cb.conjunction();
        };
    }
}
