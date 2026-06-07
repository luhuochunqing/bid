// Input: 表单条件定义（sourceField / operator / targetValue / action / targetField）
// Output: 单条条件记录（用于存储与传输）
// Pos: Domain 层（纯数据，不含框架依赖）
// 维护声明: 纯记录对象，评估逻辑在 ConditionEvaluator.
package com.xiyu.bid.formengine.domain;

/**
 * 表单字段条件定义。
 */
public record FormFieldCondition(
        Long id,
        String sourceField,
        String operator,
        String targetValue,
        String action,
        String targetField,
        int displayOrder
) {

    public static FormFieldCondition of(
            String sourceField,
            String operator,
            String targetValue,
            String action,
            String targetField,
            int displayOrder) {
        return new FormFieldCondition(null, sourceField, operator, targetValue, action, targetField, displayOrder);
    }

    public static FormFieldCondition withId(Long id,
            String sourceField, String operator, String targetValue,
            String action, String targetField, int displayOrder) {
        return new FormFieldCondition(id, sourceField, operator, targetValue, action, targetField, displayOrder);
    }
}
