package com.xiyu.bid.personnel.domain.importvalidation;

import java.util.List;

/**
 * 导入校验结果（纯核心返回值对象）
 * 采用不可变设计，便于在应用层收集后决定是否继续处理。
 */
public record ValidationResult(
        List<ImportValidationError> errors,
        List<ImportValidationWarning> warnings
) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ValidationResult empty() {
        return new ValidationResult(List.of(), List.of());
    }

    public static ValidationResult withError(ImportValidationError error) {
        return new ValidationResult(List.of(error), List.of());
    }

    public static ValidationResult withWarning(ImportValidationWarning warning) {
        return new ValidationResult(List.of(), List.of(warning));
    }

    public boolean hasBlockingErrors() {
        return !errors.isEmpty();
    }

    public boolean isValidForProceed() {
        return errors.isEmpty();   // 只有错误会阻断导入，警告不会
    }

    public int totalIssues() {
        return errors.size() + warnings.size();
    }
}
