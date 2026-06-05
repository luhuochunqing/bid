// Input: FormFieldCondition + 字段当前值
// Output: 布尔值（条件是否满足）
// Pos: Domain 层（纯函数，无框架依赖）
// 维护声明: 纯条件评估逻辑，可单测.
package com.xiyu.bid.formengine.application;

import com.xiyu.bid.formengine.domain.FormFieldCondition;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 字段条件评估器。
 * 支持的操作符：eq / neq / in / not_in / contains / not_contains / gt / gte / lt / lte
 */
@Component
public class ConditionEvaluator {

    public boolean evaluate(FormFieldCondition condition, Object fieldValue) {
        if (condition == null) {
            return true;
        }
        String op = condition.operator();
        String target = condition.targetValue();

        return switch (op) {
            case "eq" -> Objects.equals(stringOf(fieldValue), target);
            case "neq" -> !Objects.equals(stringOf(fieldValue), target);
            case "in" -> inList(stringOf(fieldValue), target);
            case "not_in" -> !inList(stringOf(fieldValue), target);
            case "gt" -> compare(fieldValue, target) > 0;
            case "gte" -> compare(fieldValue, target) >= 0;
            case "lt" -> compare(fieldValue, target) < 0;
            case "lte" -> compare(fieldValue, target) <= 0;
            case "contains" -> stringOf(fieldValue).contains(target);
            case "not_contains" -> !stringOf(fieldValue).contains(target);
            default -> true; // 未知操作符默认满足
        };
    }

    public String actionFor(FormFieldCondition condition) {
        return condition.action();
    }

    public String targetFieldOf(FormFieldCondition condition) {
        return condition.targetField() != null ? condition.targetField() : condition.sourceField();
    }

    private String stringOf(Object value) {
        if (value == null) return "";
        return String.valueOf(value);
    }

    private boolean inList(String value, String targetList) {
        if (targetList == null || targetList.isBlank()) return false;
        if (value == null) return false;
        String[] parts = targetList.split(",");
        for (String part : parts) {
            if (value.equals(part.trim())) return true;
        }
        return false;
    }

    private int compare(Object fieldValue, String target) {
        if (fieldValue == null) return -1;
        if (target == null) return 1;

        try {
            double fv = toNumber(fieldValue);
            double tv = Double.parseDouble(target.trim());
            return Double.compare(fv, tv);
        } catch (NumberFormatException e) {
            // Fall back to string comparison
            return stringOf(fieldValue).compareTo(target.trim());
        }
    }

    private double toNumber(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
}
