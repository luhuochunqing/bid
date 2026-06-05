// Input: 表单数据 Map
// Output: 验证结果
// Pos: Domain 层（纯数据，不含框架依赖）
// 维护声明: 纯记录对象.
package com.xiyu.bid.formengine.domain;

/**
 * 提交结果。
 */
public record SubmitResult(
        boolean success,
        String message,
        Object data
) {

    public static SubmitResult ok() {
        return new SubmitResult(true, "提交成功", null);
    }

    public static SubmitResult ok(Object data) {
        return new SubmitResult(true, "提交成功", data);
    }

    public static SubmitResult failure(String message) {
        return new SubmitResult(false, message, null);
    }
}
