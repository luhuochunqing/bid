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
    @DisplayName("CONTACT_METHOD 为空时不触发必填校验（CRM 同步场景不可编辑）")
    void validate_blankContactMethod_isValid() {
        ValidationResult result = TenderEvaluationCustomerInfoPolicy.validate(List.of(
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "ELECTRONICS_COMPANY_CHAIRMAN", "CONTACT_METHOD", "", "ENUM7"),
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "ELECTRONICS_COMPANY_CHAIRMAN", "NAME", "张三", "TEXT")
        ));

        assertThat(result.isValid()).isTrue();
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

    @Test
    @DisplayName("TEL 是合法 infoKey，值类型为 TEXT，显示名为对接人座机号")
    void validate_telInfoKey_isValidWithTextTypeAndDisplayName() {
        assertThat(TenderEvaluationCustomerInfoPolicy.isValidInfoKey("TEL")).isTrue();
        assertThat(TenderEvaluationCustomerInfoPolicy.VALID_INFO_KEYS).contains("TEL");
        assertThat(TenderEvaluationCustomerInfoPolicy.INFO_KEY_VALUE_TYPES.get("TEL")).isEqualTo("TEXT");
        assertThat(TenderEvaluationCustomerInfoPolicy.INFO_DISPLAY_NAMES.get("TEL")).isEqualTo("对接人座机号");

        ValidationResult result = TenderEvaluationCustomerInfoPolicy.validate(List.of(
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "ELECTRONICS_COMPANY_CHAIRMAN", "TEL", "010-12345678", "TEXT")
        ));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("CONTACT_INFO 显示名为对接人手机号")
    void infoDisplayNames_contactInfo_isMobilePhone() {
        assertThat(TenderEvaluationCustomerInfoPolicy.INFO_DISPLAY_NAMES.get("CONTACT_INFO"))
                .isEqualTo("对接人手机号");
    }

    @Test
    @DisplayName("固定信息维度共 16 个，TEL 选填（不在必填集中）")
    void infoKeys_totalCountIsSixteen_andTelOptional() {
        assertThat(TenderEvaluationCustomerInfoPolicy.VALID_INFO_KEYS).hasSize(16);
        assertThat(TenderEvaluationCustomerInfoPolicy.INFO_KEY_VALUE_TYPES).hasSize(16);
        assertThat(TenderEvaluationCustomerInfoPolicy.INFO_DISPLAY_NAMES).hasSize(16);
        assertThat(TenderEvaluationCustomerInfoPolicy.REQUIRED_INFO_KEYS).doesNotContain("TEL");

        ValidationResult result = TenderEvaluationCustomerInfoPolicy.validate(List.of(
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "ELECTRONICS_COMPANY_CHAIRMAN", "TEL", "", "TEXT")
        ));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("TEL 值类型不匹配时报 INVALID_VALUE_TYPE")
    void validate_telWrongValueType_returnsInvalidValueType() {
        ValidationResult result = TenderEvaluationCustomerInfoPolicy.validate(List.of(
                new TenderEvaluationCustomerInfoPolicy.CustomerInfoRow(
                        "ELECTRONICS_COMPANY_CHAIRMAN", "TEL", "010-12345678", "DROPDOWN")
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting(FieldError::code)
                .containsOnly("INVALID_VALUE_TYPE");
    }
}
