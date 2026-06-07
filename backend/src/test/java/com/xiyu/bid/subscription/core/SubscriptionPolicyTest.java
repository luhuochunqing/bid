// Input: subscription validation inputs (userId, entityType, entityId)
// Output: pure SubscriptionPolicy validation coverage
// Pos: Test/订阅策略纯核心单元测试
package com.xiyu.bid.subscription.core;

import com.xiyu.bid.subscription.core.SubscriptionPolicy.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionPolicy pure validation")
class SubscriptionPolicyTest {

    @Test
    @DisplayName("valid inputs produce valid result")
    void validInputs_ReturnsValid() {
        ValidationResult result = SubscriptionPolicy.validate(1L, "PROJECT", 42L);
        assertThat(result.isValid()).isTrue();
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("null userId returns INVALID_TARGET")
    void nullUserId_ReturnsInvalidTarget() {
        ValidationResult result = SubscriptionPolicy.validate(null, "PROJECT", 42L);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TARGET");
    }

    @Test
    @DisplayName("null entityId returns INVALID_TARGET")
    void nullEntityId_ReturnsInvalidTarget() {
        ValidationResult result = SubscriptionPolicy.validate(1L, "PROJECT", null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TARGET");
    }

    @Test
    @DisplayName("non-positive entityId returns INVALID_TARGET")
    void nonPositiveEntityId_ReturnsInvalidTarget() {
        ValidationResult result = SubscriptionPolicy.validate(1L, "PROJECT", 0L);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_TARGET");

        ValidationResult negResult = SubscriptionPolicy.validate(1L, "PROJECT", -5L);
        assertThat(negResult.isValid()).isFalse();
        assertThat(negResult.errorCode()).isEqualTo("INVALID_TARGET");
    }

    @Test
    @DisplayName("null entityType returns INVALID_ENTITY_TYPE")
    void nullEntityType_ReturnsInvalidEntityType() {
        ValidationResult result = SubscriptionPolicy.validate(1L, null, 42L);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ENTITY_TYPE");
    }

    @Test
    @DisplayName("unsupported entityType returns INVALID_ENTITY_TYPE")
    void unsupportedEntityType_ReturnsInvalidEntityType() {
        ValidationResult result = SubscriptionPolicy.validate(1L, "FOOBAR", 42L);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ENTITY_TYPE");
    }

    @Test
    @DisplayName("all allowed types pass validation")
    void allAllowedTypes_PassValidation() {
        assertThat(SubscriptionPolicy.validate(1L, "PROJECT", 1L).isValid()).isTrue();
        assertThat(SubscriptionPolicy.validate(1L, "DOCUMENT", 1L).isValid()).isTrue();
        assertThat(SubscriptionPolicy.validate(1L, "QUALIFICATION", 1L).isValid()).isTrue();
        assertThat(SubscriptionPolicy.validate(1L, "TENDER", 1L).isValid()).isTrue();
        assertThat(SubscriptionPolicy.validate(1L, "TASK", 1L).isValid()).isTrue();
    }

    @Test
    @DisplayName("allowedEntityTypes exposes the whitelist")
    void allowedEntityTypes_ExposesWhitelist() {
        assertThat(SubscriptionPolicy.allowedEntityTypes())
            .containsExactlyInAnyOrder("PROJECT", "DOCUMENT", "QUALIFICATION", "TENDER", "TASK");
    }
}
