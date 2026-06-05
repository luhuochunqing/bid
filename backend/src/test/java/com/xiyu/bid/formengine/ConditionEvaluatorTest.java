package com.xiyu.bid.formengine;

import com.xiyu.bid.formengine.application.ConditionEvaluator;
import com.xiyu.bid.formengine.domain.FormFieldCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConditionEvaluator 单元测试。
 * 覆盖所有 10 种操作符：eq / neq / in / not_in / gt / gte / lt / lte / contains / not_contains
 * 包括边界情况：null 值、空字符串、数值 vs 字符串比较。
 */
@DisplayName("ConditionEvaluator")
class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
    }

    private FormFieldCondition cond(String sourceField, String operator, String targetValue, String action) {
        return FormFieldCondition.of(sourceField, operator, targetValue, action, null, 0);
    }

    // ==================== eq ====================

    @Nested
    @DisplayName("eq")
    class Eq {

        @Test
        @DisplayName("fieldValue == targetValue → true")
        void eq_matching() {
            FormFieldCondition c = cond("status", "eq", "draft", "hide");
            assertThat(evaluator.evaluate(c, "draft")).isTrue();
        }

        @Test
        @DisplayName("fieldValue != targetValue → false")
        void eq_notMatching() {
            FormFieldCondition c = cond("status", "eq", "draft", "hide");
            assertThat(evaluator.evaluate(c, "published")).isFalse();
        }

        @Test
        @DisplayName("null fieldValue → false")
        void eq_nullValue() {
            FormFieldCondition c = cond("status", "eq", "draft", "hide");
            assertThat(evaluator.evaluate(c, null)).isFalse();
        }
    }

    // ==================== neq ====================

    @Nested
    @DisplayName("neq")
    class Neq {

        @Test
        @DisplayName("fieldValue != targetValue → true")
        void neq_notMatching() {
            FormFieldCondition c = cond("status", "neq", "draft", "hide");
            assertThat(evaluator.evaluate(c, "published")).isTrue();
        }

        @Test
        @DisplayName("fieldValue == targetValue → false")
        void neq_matching() {
            FormFieldCondition c = cond("status", "neq", "draft", "hide");
            assertThat(evaluator.evaluate(c, "draft")).isFalse();
        }

        @Test
        @DisplayName("null fieldValue → true")
        void neq_nullValue() {
            FormFieldCondition c = cond("status", "neq", "draft", "hide");
            assertThat(evaluator.evaluate(c, null)).isTrue();
        }
    }

    // ==================== in ====================

    @Nested
    @DisplayName("in")
    class In {

        @Test
        @DisplayName("fieldValue 在列表中 → true")
        void in_found() {
            FormFieldCondition c = cond("status", "in", "draft,published,archived", "hide");
            assertThat(evaluator.evaluate(c, "draft")).isTrue();
        }

        @Test
        @DisplayName("fieldValue 不在列表中 → false")
        void in_notFound() {
            FormFieldCondition c = cond("status", "in", "draft,published", "hide");
            assertThat(evaluator.evaluate(c, "deleted")).isFalse();
        }

        @Test
        @DisplayName("带空格的列表项能正确匹配")
        void in_withSpaces() {
            FormFieldCondition c = cond("status", "in", "draft, published, archived", "hide");
            assertThat(evaluator.evaluate(c, "published")).isTrue();
        }

        @Test
        @DisplayName("null fieldValue → false")
        void in_nullValue() {
            FormFieldCondition c = cond("status", "in", "draft,published", "hide");
            assertThat(evaluator.evaluate(c, null)).isFalse();
        }
    }

    // ==================== not_in ====================

    @Nested
    @DisplayName("not_in")
    class NotIn {

        @Test
        @DisplayName("fieldValue 不在列表中 → true")
        void notIn_notFound() {
            FormFieldCondition c = cond("status", "not_in", "draft,published", "hide");
            assertThat(evaluator.evaluate(c, "deleted")).isTrue();
        }

        @Test
        @DisplayName("fieldValue 在列表中 → false")
        void notIn_found() {
            FormFieldCondition c = cond("status", "not_in", "draft,published", "hide");
            assertThat(evaluator.evaluate(c, "draft")).isFalse();
        }
    }

    // ==================== gt / gte / lt / lte ====================

    @Nested
    @DisplayName("数值比较操作符")
    class NumericComparison {

        @Test
        @DisplayName("gt: 10 > 5 → true")
        void gt_true() {
            FormFieldCondition c = cond("amount", "gt", "5", "hide");
            assertThat(evaluator.evaluate(c, 10)).isTrue();
        }

        @Test
        @DisplayName("gt: 5 > 5 → false")
        void gt_false_equal() {
            FormFieldCondition c = cond("amount", "gt", "5", "hide");
            assertThat(evaluator.evaluate(c, 5)).isFalse();
        }

        @Test
        @DisplayName("gt: 3 > 5 → false")
        void gt_false() {
            FormFieldCondition c = cond("amount", "gt", "5", "hide");
            assertThat(evaluator.evaluate(c, 3)).isFalse();
        }

        @Test
        @DisplayName("gte: 5 >= 5 → true")
        void gte_equal() {
            FormFieldCondition c = cond("amount", "gte", "5", "hide");
            assertThat(evaluator.evaluate(c, 5)).isTrue();
        }

        @Test
        @DisplayName("gte: 10 >= 5 → true")
        void gte_true() {
            FormFieldCondition c = cond("amount", "gte", "5", "hide");
            assertThat(evaluator.evaluate(c, 10)).isTrue();
        }

        @Test
        @DisplayName("lt: 3 < 5 → true")
        void lt_true() {
            FormFieldCondition c = cond("amount", "lt", "5", "hide");
            assertThat(evaluator.evaluate(c, 3)).isTrue();
        }

        @Test
        @DisplayName("lt: 5 < 5 → false")
        void lt_false_equal() {
            FormFieldCondition c = cond("amount", "lt", "5", "hide");
            assertThat(evaluator.evaluate(c, 5)).isFalse();
        }

        @Test
        @DisplayName("lte: 5 <= 5 → true")
        void lte_equal() {
            FormFieldCondition c = cond("amount", "lte", "5", "hide");
            assertThat(evaluator.evaluate(c, 5)).isTrue();
        }

        @Test
        @DisplayName("lte: 3 <= 5 → true")
        void lte_true() {
            FormFieldCondition c = cond("amount", "lte", "5", "hide");
            assertThat(evaluator.evaluate(c, 3)).isTrue();
        }

        @Test
        @DisplayName("String 数值比较")
        void numericWithString() {
            FormFieldCondition c = cond("amount", "gt", "100", "hide");
            assertThat(evaluator.evaluate(c, "200")).isTrue();
        }

        @Test
        @DisplayName("null fieldValue → false for gt/lt")
        void nullValue_comparison() {
            FormFieldCondition c = cond("amount", "gt", "5", "hide");
            assertThat(evaluator.evaluate(c, null)).isFalse();
        }
    }

    // ==================== contains / not_contains ====================

    @Nested
    @DisplayName("contains / not_contains")
    class Contains {

        @Test
        @DisplayName("contains: 子串存在 → true")
        void contains_true() {
            FormFieldCondition c = cond("name", "contains", "Xiyu", "hide");
            assertThat(evaluator.evaluate(c, "Xiyu Bid Platform")).isTrue();
        }

        @Test
        @DisplayName("contains: 子串不存在 → false")
        void contains_false() {
            FormFieldCondition c = cond("name", "contains", "Xiyu", "hide");
            assertThat(evaluator.evaluate(c, "Alibaba Cloud")).isFalse();
        }

        @Test
        @DisplayName("not_contains: 子串不存在 → true")
        void notContains_true() {
            FormFieldCondition c = cond("name", "not_contains", "Xiyu", "show");
            assertThat(evaluator.evaluate(c, "Alibaba Cloud")).isTrue();
        }

        @Test
        @DisplayName("not_contains: 子串存在 → false")
        void notContains_false() {
            FormFieldCondition c = cond("name", "not_contains", "Xiyu", "show");
            assertThat(evaluator.evaluate(c, "Xiyu Bid Platform")).isFalse();
        }

        @Test
        @DisplayName("contains null fieldValue → false")
        void contains_nullValue() {
            FormFieldCondition c = cond("name", "contains", "Xiyu", "hide");
            assertThat(evaluator.evaluate(c, null)).isFalse();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("null condition → true（默认通过）")
        void nullCondition() {
            assertThat(evaluator.evaluate(null, "value")).isTrue();
        }

        @Test
        @DisplayName("空字符串 fieldValue")
        void emptyString() {
            FormFieldCondition c = cond("name", "eq", "", "hide");
            assertThat(evaluator.evaluate(c, "")).isTrue();
        }

        @Test
        @DisplayName("空字符串 targetValue")
        void emptyTarget() {
            FormFieldCondition c = cond("name", "eq", "", "hide");
            assertThat(evaluator.evaluate(c, "something")).isFalse();
        }

        @Test
        @DisplayName("未知操作符 → true（默认通过）")
        void unknownOperator() {
            FormFieldCondition c = cond("field", "unknown_op", "value", "hide");
            assertThat(evaluator.evaluate(c, "value")).isTrue();
        }

        @Test
        @DisplayName("null targetValue")
        void nullTargetValue() {
            FormFieldCondition c = cond("field", "eq", null, "hide");
            assertThat(evaluator.evaluate(c, "value")).isFalse();
        }

        @Test
        @DisplayName("非数值与数值目标比较 → 回退到字符串比较（字母序 abc < 5 为 false）")
        void nonNumericFallback() {
            FormFieldCondition c = cond("field", "gt", "abc", "hide");
            assertThat(evaluator.evaluate(c, "xyz")).isTrue();  // "xyz".compareTo("abc") > 0
        }

        @Test
        @DisplayName("actionFor 返回 action")
        void actionFor_returnsAction() {
            FormFieldCondition c = cond("field", "eq", "v", "hide");
            assertThat(evaluator.actionFor(c)).isEqualTo("hide");
        }

        @Test
        @DisplayName("targetFieldOf: 有 targetField → 返回 targetField")
        void targetFieldOf_withTargetField() {
            FormFieldCondition c = FormFieldCondition.of("source", "eq", "v", "hide", "target", 0);
            assertThat(evaluator.targetFieldOf(c)).isEqualTo("target");
        }

        @Test
        @DisplayName("targetFieldOf: 无 targetField → 返回 sourceField")
        void targetFieldOf_withoutTargetField() {
            FormFieldCondition c = cond("source", "eq", "v", "hide");
            assertThat(evaluator.targetFieldOf(c)).isEqualTo("source");
        }
    }
}
