package com.xiyu.bid.dto;

/**
 * 字段级校验错误详情。
 */
public record ErrorDetail(
    String field,
    String message,
    String code
) {}
