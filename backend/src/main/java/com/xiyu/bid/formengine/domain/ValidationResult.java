// Input: scope / formData
// Output: 验证结果（valid + errors 列表）
// Pos: Domain 层（纯数据，不含框架依赖）
// 维护声明: 纯记录对象，验证规则在 application 层.
package com.xiyu.bid.formengine.domain;

/**
 * 表单验证结果。
 */
public record ValidationResult(
        boolean valid,
        java.util.List<String> errors
) {

    public static ValidationResult success() {
        return new ValidationResult(true, java.util.List.of());
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, java.util.List.of(error));
    }

    public static ValidationResult failure(java.util.List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
