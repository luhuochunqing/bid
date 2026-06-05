package com.xiyu.bid.contractborrow.infrastructure.persistence.repository;

import com.xiyu.bid.contractborrow.application.command.ContractBorrowQueryCriteria;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ContractBorrowSpecification {

    private ContractBorrowSpecification() {
    }

    public static Specification<ContractBorrowApplicationEntity> byCriteria(
            ContractBorrowQueryCriteria criteria,
            LocalDate today
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) {
                return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
            }

            if (hasText(criteria.status())) {
                String status = criteria.status().trim().toUpperCase(Locale.ROOT);
                if ("OVERDUE".equals(status)) {
                    predicates.add(root.get("status").in(activeBorrowingStatuses()));
                    predicates.add(criteriaBuilder.lessThan(root.get("expectedReturnDate"), today));
                } else {
                    predicates.add(criteriaBuilder.equal(root.get("status"), ContractBorrowStatus.valueOf(status)));
                }
            }

            if (hasText(criteria.borrowerName())) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("borrowerName")),
                        normalize(criteria.borrowerName())
                ));
            }

            if (hasText(criteria.keyword())) {
                String pattern = "%" + normalize(criteria.keyword()) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contractNo")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contractName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("borrowerName")), pattern)
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static List<ContractBorrowStatus> activeBorrowingStatuses() {
        return List.of(ContractBorrowStatus.APPROVED, ContractBorrowStatus.BORROWED);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
