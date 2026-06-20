package com.xiyu.bid.tender.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenderEvaluationCustomerInfoPolicy - 客户信息角色校验")
class TenderEvaluationCustomerInfoPolicyTest {

    @Test
    @DisplayName("EXTERNAL_ROLE_N 外部兜底角色允许最终提交")
    void validate_externalRoleKey_isValid() {
        ValidationResult result = TenderEvaluationCustomerInfoPolicy.validate(List.of(
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "EXTERNAL_ROLE_1", "NAME", "张三", "TEXT")
        ));

        assertThat(result.isValid()).isTrue();
        assertThat(TenderEvaluationCustomerInfoPolicy.isValidRoleKey("EXTERNAL_ROLE_1")).isTrue();
        assertThat(TenderEvaluationCustomerInfoPolicy.isValidRoleKey("EXTERNAL_ROLE_2")).isTrue();
    }

    @Test
    @DisplayName("非法外部兜底角色仍然拒绝")
    void validate_invalidExternalRoleKey_returnsInvalidRole() {
        ValidationResult result = TenderEvaluationCustomerInfoPolicy.validate(List.of(
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "EXTERNAL_ROLE_ABC", "NAME", "张三", "TEXT"),
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "EXTERNAL_ROLE_", "NAME", "李四", "TEXT")
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).extracting(FieldError::code)
                .containsOnly("INVALID_ROLE");
        assertThat(TenderEvaluationCustomerInfoPolicy.isValidRoleKey("EXTERNAL_ROLE_ABC")).isFalse();
        assertThat(TenderEvaluationCustomerInfoPolicy.isValidRoleKey("EXTERNAL_ROLE_")).isFalse();
    }
}
