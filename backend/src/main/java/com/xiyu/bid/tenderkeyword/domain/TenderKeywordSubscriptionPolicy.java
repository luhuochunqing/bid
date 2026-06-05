// Input: subscription validation inputs
// Output: pure ValidationResult values
// Pos: Pure Core/标讯关键词订阅策略
package com.xiyu.bid.tenderkeyword.domain;

import java.util.List;

/**
 * 纯核心：标讯关键词订阅校验策略
 *
 * <p>返回业务校验结果，不抛出异常。
 */
public final class TenderKeywordSubscriptionPolicy {

    private static final int MAX_KEYWORDS = 20;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final int MIN_KEYWORDS = 1;

    private TenderKeywordSubscriptionPolicy() {
    }

    public record ValidationResult(boolean isValid, String errorCode, String errorMessage) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
    }

    /**
     * 验证创建订阅请求
     */
    public static ValidationResult validateCreate(Long userId, String name, List<String> keywords, String logicOperator) {
        if (userId == null) {
            return ValidationResult.invalid("INVALID_USER", "用户ID不能为空");
        }
        if (name == null || name.isBlank()) {
            return ValidationResult.invalid("INVALID_NAME", "订阅名称不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return ValidationResult.invalid("NAME_TOO_LONG", "订阅名称不能超过" + MAX_NAME_LENGTH + "个字符");
        }
        if (keywords == null || keywords.isEmpty()) {
            return ValidationResult.invalid("INVALID_KEYWORDS", "关键词不能为空");
        }
        if (keywords.size() > MAX_KEYWORDS) {
            return ValidationResult.invalid("TOO_MANY_KEYWORDS", "关键词数量不能超过" + MAX_KEYWORDS + "个");
        }
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                return ValidationResult.invalid("INVALID_KEYWORD", "关键词不能为空");
            }
            if (kw.length() > MAX_KEYWORD_LENGTH) {
                return ValidationResult.invalid("KEYWORD_TOO_LONG", "单关键词不能超过" + MAX_KEYWORD_LENGTH + "个字符");
            }
        }
        if (!KeywordMatchPolicy.isValidOperator(logicOperator)) {
            return ValidationResult.invalid("INVALID_OPERATOR", "逻辑关系必须是 AND 或 OR");
        }
        return ValidationResult.valid();
    }

    /**
     * 验证关键词是否包含有效内容
     */
    public static boolean hasValidKeywords(List<String> keywords) {
        return keywords != null && !keywords.isEmpty()
            && keywords.stream().anyMatch(k -> k != null && !k.isBlank());
    }
}
