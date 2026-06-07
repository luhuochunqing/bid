package com.xiyu.bid.service;

import java.util.List;

/**
 * 分页查询结果。
 *
 * @param <T> 数据类型
 */
public record PaginatedResult<T>(List<T> list, int totalCount, int pageIndex, int pageSize) {}
