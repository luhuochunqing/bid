package com.xiyu.bid.workbench.dto;

/**
 * 工作台截止时间单个条目 DTO。
 *
 * <ul>
 *   <li>{@code id} — 来源实体 ID（Tender.id 或 Fee.id）</li>
 *   <li>{@code name} — 显示名称（标讯名称或项目名称）</li>
 *   <li>{@code date} — 截止日期，格式 {@code yyyy-MM-dd}</li>
 *   <li>{@code targetId} — 点击跳转目标 ID（标讯 ID 或项目 ID）</li>
 *   <li>{@code targetType} — 跳转目标类型：{@code tender} 或 {@code project}</li>
 * </ul>
 */
public record DeadlineItemDTO(
        Long id,
        String name,
        String date,
        Long targetId,
        String targetType
) {}
