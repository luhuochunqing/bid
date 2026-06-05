package com.xiyu.bid.dto;

/**
 * 密码重置响应DTO
 */
public record PasswordResetResponse(
        String message,
        String devToken // 仅开发环境返回，用于测试
) {}
