package com.xiyu.bid.tender.core;

import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenderEvaluationFormPolicy - V1026 字段重构后校验")
class TenderEvaluationFormPolicyTest {

    // ---------- helpers ----------

    private static TenderEvaluationSubmitRequest validRequest() {
        return new TenderEvaluationSubmitRequest(
                null,
                new EvaluationBasicDTO(
                        3,
                        new BigDecimal("500000"),
                        "有较大技术偏离",
                        "竞争对手强势",
                        "已有备选方案",
                        "熟悉",
                        "需要法务支持",
                        "时间紧张",
                        new BigDecimal("120000")
                ),
                Collections.emptyList(),
                null
        );
    }

    private static TenderEvaluationSubmitRequest withBasic(EvaluationBasicDTO basic) {
        return new TenderEvaluationSubmitRequest(
                null, basic, Collections.emptyList(), null
        );
    }

    // ---------- 1) happy path ----------

    @Test
    @DisplayName("所有字段有效 -> 校验通过")
    void validate_allValid_returnsValid() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(validRequest());

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    // ---------- 2) plannedShortlistedCount ----------

    @Test
    @DisplayName("plannedShortlistedCount = 0 -> MIN_VALUE 错误")
    void validate_plannedShortlistedCountZero_returnsError() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(0, BigDecimal.ZERO, "", "", "", "", "", "", BigDecimal.ZERO)
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting(FieldError::field).contains("plannedShortlistedCount");
    }

    @Test
    @DisplayName("plannedShortlistedCount = null -> 有效（非必填）")
    void validate_plannedShortlistedCountNull_isValid() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(null, BigDecimal.ZERO, "", "", "", "", "", "", BigDecimal.ZERO)
        ));

        assertThat(result.isValid()).isTrue();
    }

    // ---------- 3) mroOfficeFlowAmount ----------

    @Test
    @DisplayName("mroOfficeFlowAmount 为负数 -> MIN_VALUE 错误")
    void validate_mroOfficeFlowAmountNegative_returnsError() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(3, new BigDecimal("-1"), "", "", "", "", "", "", BigDecimal.ZERO)
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting(FieldError::field).contains("mroOfficeFlowAmount");
    }

    @Test
    @DisplayName("mroOfficeFlowAmount = null -> 有效（非必填）")
    void validate_mroOfficeFlowAmountNull_isValid() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(3, null, "", "", "", "", "", "", BigDecimal.ZERO)
        ));

        assertThat(result.isValid()).isTrue();
    }

    // ---------- 4) customerRevenue ----------

    @Test
    @DisplayName("customerRevenue 为负数 -> MIN_VALUE 错误")
    void validate_customerRevenueNegative_returnsError() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(3, BigDecimal.ZERO, "", "", "", "", "", "", new BigDecimal("-1"))
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting(FieldError::field).contains("customerRevenue");
    }

    @Test
    @DisplayName("customerRevenue = null -> 有效（非必填）")
    void validate_customerRevenueNull_isValid() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(3, BigDecimal.ZERO, "", "", "", "", "", "", null)
        ));

        assertThat(result.isValid()).isTrue();
    }

    // ---------- 5) aggregate errors ----------

    @Test
    @DisplayName("多个字段同时越界 -> 多条错误")
    void validate_multipleErrors_returnsAllOfThem() {
        ValidationResult result = TenderEvaluationFormPolicy.validate(withBasic(
                new EvaluationBasicDTO(0, new BigDecimal("-1"), "", "", "", "", "", "", new BigDecimal("-1"))
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors()).extracting(FieldError::field)
                .containsExactlyInAnyOrder(
                        "plannedShortlistedCount",
                        "mroOfficeFlowAmount",
                        "customerRevenue");
    }

    // ---------- 6) programmer error: null input ----------

    @Test
    @DisplayName("validate(null) -> 抛 NullPointerException")
    void validate_nullInput_throwsNPE() {
        assertThatThrownBy(() -> TenderEvaluationFormPolicy.validate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
