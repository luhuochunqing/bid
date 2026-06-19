package com.xiyu.bid.tender.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void shouldAllowFrameworkAgreementWithoutBudget() {
        TenderRequest request = validRequest();
        request.setBudget(null);

        boolean hasBudgetViolation = validator.validate(request).stream()
                .anyMatch(violation -> "budget".equals(violation.getPropertyPath().toString()));

        assertFalse(hasBudgetViolation);
    }

    @Test
    void shouldRejectNegativeBudgetWhenBudgetIsProvided() {
        TenderRequest request = validRequest();
        request.setBudget(new BigDecimal("-0.01"));

        boolean hasNegativeBudgetViolation = validator.validate(request).stream()
                .anyMatch(violation -> "budget".equals(violation.getPropertyPath().toString())
                        && violation.getConstraintDescriptor().getAnnotation() instanceof DecimalMin);

        assertTrue(hasNegativeBudgetViolation);
    }

    @Test
    void shouldAllowGovernanceFieldsWhenProvided() {
        TenderRequest request = validRequest();
        request.setTenderAgency("上海招标代理有限公司");
        request.setBidOpeningTime(LocalDateTime.now().plusDays(31));
        request.setCustomerType("KA 客户");
        request.setPriority("S");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectRegionInPlainProvinceNameFormat() {
        TenderRequest request = validRequest();
        request.setRegion("北京");

        boolean hasRegionViolation = validator.validate(request).stream()
                .anyMatch(violation -> "region".equals(violation.getPropertyPath().toString()));

        assertTrue(hasRegionViolation);
    }

    @Test
    void shouldAcceptRegionInMunicipalityOrProvincePlusCityFormat() {
        TenderRequest request = validRequest();
        request.setRegion("北京市");
        assertTrue(validator.validate(request).stream()
                .noneMatch(violation -> "region".equals(violation.getPropertyPath().toString())));

        request.setRegion("北京市-北京市");
        assertTrue(validator.validate(request).stream()
                .noneMatch(violation -> "region".equals(violation.getPropertyPath().toString())));

        request.setRegion("广东省深圳市");
        assertTrue(validator.validate(request).stream()
                .noneMatch(violation -> "region".equals(violation.getPropertyPath().toString())));
    }

    private TenderRequest validRequest() {
        TenderRequest request = new TenderRequest();
        request.setTitle("框架协议供应商引入项目");
        request.setBudget(new BigDecimal("100.00"));
        request.setDeadline(LocalDateTime.now().plusDays(30));
        return request;
    }
}
