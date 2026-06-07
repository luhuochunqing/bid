package com.xiyu.bid.dto;

import java.util.List;

/**
 * 统一分页响应 DTO。
 * 用于所有列表查询端点的标准化分页返回。
 */
public record PageDTO<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int number,
    int size
) {
    public static <T> PageDTO<T> of(List<T> content, long totalElements, int number, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageDTO<>(content, totalElements, totalPages, number, size);
    }

    public boolean hasMore() {
        return number + 1 < totalPages;
    }
}
