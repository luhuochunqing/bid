// Input: CrossFieldValidationRule + 表单数据 Map
// Output: 验证错误列表（空 = 通过）
// Pos: Application 层（纯函数，无状态，可单测）
// 维护声明: 跨字段评估逻辑全部在此，无框架依赖.
package com.xiyu.bid.formengine.application;
import lombok.extern.slf4j.Slf4j;

import com.xiyu.bid.formengine.domain.CrossFieldValidationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨字段验证器。
 * 支持的操作符：less_than / greater_than / equals / not_equals /
 *              sum_equals / one_filled / both_filled / not_after
 */
@Component
@Slf4j
public class CrossFieldValidator {

    /**
     * 验证单条跨字段规则。
     *
     * @param rule    验证规则
     * @param formData 表单数据（fieldKey -> value）
     * @return 错误消息列表（空 = 通过）
     */
    public List<String> validate(CrossFieldValidationRule rule, Map<String, Object> formData) {
        Object valA = formData.get(rule.fieldA());
        Object valB = rule.fieldB() != null ? formData.get(rule.fieldB()) : null;
        String target = rule.targetValue();

        boolean valid = switch (rule.operator()) {
            case "less_than" -> compareNumeric(valA, valB) < 0 || compareNumeric(valA, parseNumber(target)) < 0;
            case "greater_than" -> compareNumeric(valA, valB) > 0 || compareNumeric(valA, parseNumber(target)) > 0;
            case "equals" -> Objects.equals(valA, valB) || Objects.equals(valA, target);
            case "not_equals" -> !Objects.equals(valA, valB) && !Objects.equals(valA, target);
            case "sum_equals" -> sumEquals(formData, rule.fieldA(), rule.fieldB(), target);
            case "one_filled" -> isFilled(valA) || isFilled(valB);
            case "both_filled" -> isFilled(valA) && isFilled(valB);
            case "not_after" -> !isAfter(valA, valB);
            default -> true; // 未知操作符默认通过
        };

        return valid ? List.of() : List.of(rule.errorMessage());
    }

    /**
     * 批量验证所有规则。
     */
    public List<String> validateAll(List<CrossFieldValidationRule> rules, Map<String, Object> formData) {
        List<String> errors = new ArrayList<>();
        for (CrossFieldValidationRule rule : rules) {
            errors.addAll(validate(rule, formData));
        }
        return errors;
    }

    // -------------------- Private Helpers --------------------

    private boolean isFilled(Object value) {
        if (value == null) return false;
        if (value instanceof String s) return !s.isBlank();
        return true;
    }

    private int compareNumeric(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        try {
            double na = toNumber(a);
            double nb = toNumber(b);
            return Double.compare(na, nb);
        } catch (NumberFormatException e) {
            return String.valueOf(a).compareTo(String.valueOf(b));
        }
    }

    private double parseNumber(String s) {
        if (s == null || s.isBlank()) return 0;
        return Double.parseDouble(s.trim());
    }

    private double toNumber(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private boolean sumEquals(Map<String, Object> formData, String fieldA, String fieldB, String target) {
        if (target == null || target.isBlank()) return false;
        double targetVal = Double.parseDouble(target.trim());
        double sum = 0;
        for (String key : extractKeys(fieldA, fieldB)) {
            Object v = formData.get(key.trim());
            if (v instanceof Number n) {
                sum += n.doubleValue();
            } else {
                try {
                    sum += Double.parseDouble(String.valueOf(v));
                } catch (NumberFormatException ignored) {
                    log.debug("Non-numeric field skipped", ignored);
                    // 非数值字段不计入
                }
            }
        }
        return Math.abs(sum - targetVal) < 1e-9;
    }

    private List<String> extractKeys(String fieldA, String fieldB) {
        var keys = new java.util.ArrayList<String>();
        for (String k : fieldA.split(",")) keys.add(k.trim());
        if (fieldB != null) {
            for (String k : fieldB.split(",")) keys.add(k.trim());
        }
        return keys;
    }

    private boolean isAfter(Object a, Object b) {
        if (a == null || b == null) return false;
        try {
            LocalDate dateA = parseDate(a);
            LocalDate dateB = parseDate(b);
            return dateA != null && dateB != null && dateA.isAfter(dateB);
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDate parseDate(Object value) {
        String s = String.valueOf(value).trim();
        return LocalDate.parse(s);
    }
}
