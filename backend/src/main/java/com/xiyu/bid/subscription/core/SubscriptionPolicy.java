// Input: subscription validation inputs (userId, entityType, entityId)
// Output: pure ValidationResult values indicating validity or error code
// Pos: Pure Core/订阅策略
package com.xiyu.bid.subscription.core;

import java.util.Set;

/**
 * Pure validation policy for subscription targets.
 *
 * <p>Returns business validation outcomes as values; never throws.
 */
public final class SubscriptionPolicy {

    private static final Set<String> ALLOWED_TYPES =
        Set.of("PROJECT", "DOCUMENT", "QUALIFICATION", "TENDER", "TASK");

    private SubscriptionPolicy() {
    }

    public record ValidationResult(boolean isValid, String errorCode, String errorMessage) {

        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
    }

    public static ValidationResult validate(Long userId, String entityType, Long entityId) {
        if (userId == null || entityId == null || entityId <= 0) {
            return ValidationResult.invalid("INVALID_TARGET", "非法订阅参数");
        }
        if (entityType == null || !ALLOWED_TYPES.contains(entityType)) {
            return ValidationResult.invalid("INVALID_ENTITY_TYPE", "不支持的订阅类型");
        }
        return ValidationResult.valid();
    }

    public static Set<String> allowedEntityTypes() {
        return ALLOWED_TYPES;
    }
}
