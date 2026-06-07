package com.xiyu.bid.common.domain;

/**
 * 分页结果，替代 Spring Data 的 Page/Pageable，避免污染纯核心层。
 */
public record PagedResult<T>(
        java.util.List<T> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PagedResult<T> of(java.util.List<T> content, long totalElements, int pageNumber, int pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        return new PagedResult<>(
                content,
                totalElements,
                totalPages,
                pageNumber,
                pageSize,
                pageNumber < totalPages - 1,
                pageNumber > 0
        );
    }

    public static <T> PagedResult<T> empty(int pageNumber, int pageSize) {
        return of(java.util.List.of(), 0, pageNumber, pageSize);
    }
}
