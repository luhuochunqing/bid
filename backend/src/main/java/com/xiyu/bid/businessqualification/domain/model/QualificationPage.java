package com.xiyu.bid.businessqualification.domain.model;

import java.util.List;

/**
 * Domain-layer pagination result.
 *
 * <p>Used by {@link com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository}
 * to avoid leaking Spring Data's {@code Page}/{@code Pageable} framework types
 * into the pure-core domain package.</p>
 *
 * <p>The infrastructure adapter is responsible for translating
 * {@code org.springframework.data.domain.Page} into this record before
 * returning from the port boundary.</p>
 *
 * <p>CO-155 fix: introduced after FPJavaArchitectureTest flagged
 * {@code org.springframework.data.domain.Page} as a forbidden
 * framework-shell dependency in pure-core packages.</p>
 */
public record QualificationPage<T>(
        List<T> content,
        long totalElements,
        int page,
        int size
) {
    public QualificationPage {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
