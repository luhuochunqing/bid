package com.xiyu.bid.file.domain;

/**
 * 校验结果值对象。
 *
 * <p>预期内业务失败作为普通值返回，不抛异常做业务流程控制。
 * 符合 FP-Java Profile 第 5 条：业务失败用 Result/Optional/ValidationResult 返回。</p>
 */
public record ValidationResult(boolean ok, String reason) {

    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult failure(String reason) {
        return new ValidationResult(false, reason);
    }

    public boolean isFailure() {
        return !ok;
    }

    public boolean isSuccess() {
        return ok;
    }
}
