// Input: 字段级校验失败的三元组（字段名 / 错误码 / 人类可读消息）
// Output: 不可变的字段错误值对象
// Pos: 纯核心层（core）- 不依赖 Spring / JPA
// 维护声明: 仅承载值；不允许加入行为、序列化注解或框架耦合
package com.xiyu.bid.tender.core;

/**
 * 字段级校验错误的纯值对象。
 * <p>{@code field}：失败字段（驼峰；逻辑组合错误用约定的逻辑名，如 contractPeriod）。
 * <p>{@code code}：稳定的错误码（如 REQUIRED / INVALID_RANGE / MIN_VALUE）。
 * <p>{@code message}：面向用户的可读消息。
 */
public record FieldError(String field, String code, String message) {}
