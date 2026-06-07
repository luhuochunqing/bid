// Input: 字段错误集合
// Output: 表示一次完整校验结果的不可变值对象
// Pos: 纯核心层（core）- 不依赖 Spring / JPA
// 维护声明: 不承载业务逻辑；errors 列表语义为「全部一次性收集」，不是首条短路。
package com.xiyu.bid.tender.core;

import java.util.List;
import java.util.Objects;

/**
 * 校验结果。承载 0..N 条 {@link FieldError}。
 * <p>{@link #isValid()} 当且仅当无任何错误时返回 true。
 */
public record ValidationResult(List<FieldError> errors) {

    public ValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** 便捷构造：表示校验通过。 */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }
}
