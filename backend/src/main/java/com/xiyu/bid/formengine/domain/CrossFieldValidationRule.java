// Input: cross-field validation rule definition
// Output: validation errors if rule violated
// Pos: Domain 层（纯数据，不含框架依赖）
// 维护声明: 纯记录对象，评估逻辑在 application 层.
package com.xiyu.bid.formengine.domain;

/**
 * 跨字段验证规则。
 * 支持的操作符：
 * <ul>
 *   <li>less_than — fieldA < fieldB 或 fieldA < targetValue</li>
 *   <li>greater_than — fieldA > fieldB 或 fieldA > targetValue</li>
 *   <li>equals — fieldA == fieldB 或 fieldA == targetValue</li>
 *   <li>not_equals — fieldA != fieldB 且 fieldA != targetValue</li>
 *   <li>sum_equals — sum(fields) == targetValue</li>
 *   <li>one_filled — fieldA 或 fieldB 至少有一个非空</li>
 *   <li>both_filled — fieldA 和 fieldB 均非空</li>
 *   <li>not_after — fieldA 日期不晚于 fieldB 日期</li>
 * </ul>
 */
public record CrossFieldValidationRule(
        Long id,
        Long definitionId,
        String scope,
        String fieldA,
        String operator,
        String fieldB,
        String targetValue,
        String errorMessage,
        Integer priority
) {

    public CrossFieldValidationRule {
        if (scope == null) scope = "";
        if (priority == null) priority = 0;
    }

    public static CrossFieldValidationRule of(
            String scope,
            String fieldA,
            String operator,
            String fieldB,
            String targetValue,
            String errorMessage) {
        return new CrossFieldValidationRule(null, null, scope, fieldA, operator, fieldB, targetValue, errorMessage, 0);
    }

    public static CrossFieldValidationRule of(
            String scope,
            String fieldA,
            String operator,
            String fieldB,
            String targetValue,
            String errorMessage,
            Integer priority) {
        return new CrossFieldValidationRule(null, null, scope, fieldA, operator, fieldB, targetValue, errorMessage, priority);
    }
}
