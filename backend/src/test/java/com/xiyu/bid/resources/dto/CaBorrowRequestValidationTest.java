package com.xiyu.bid.resources.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CaBorrowRequestValidationTest {

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
    void caCertificateIdNull_shouldNotFailValidation() {
        CaBorrowRequest request = validRequest();
        request.setCaCertificateId(null);

        Set<String> violations = validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertFalse(violations.contains("caCertificateId"),
                "caCertificateId 为 null 时不应触发校验失败（该值来自 URL 路径参数）");
    }

    @Test
    void purposeBlank_shouldFailValidation() {
        CaBorrowRequest request = validRequest();
        request.setPurpose("");

        Set<String> violations = validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(violations.contains("purpose"));
    }

    @Test
    void borrowDurationTypeBlank_shouldFailValidation() {
        CaBorrowRequest request = validRequest();
        request.setBorrowDurationType("");

        Set<String> violations = validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(violations.contains("borrowDurationType"));
    }

    @Test
    void allOptionalFieldsNull_shouldOnlyFailOnPurposeAndBorrowDuration() {
        CaBorrowRequest request = new CaBorrowRequest();

        Set<String> violations = validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(violations.contains("purpose"));
        assertTrue(violations.contains("borrowDurationType"));
        assertFalse(violations.contains("caCertificateId"),
                "caCertificateId 不应有 @NotNull 校验");
        assertFalse(violations.contains("projectId"));
        assertFalse(violations.contains("expectedReturnDate"));
    }

    private CaBorrowRequest validRequest() {
        CaBorrowRequest request = new CaBorrowRequest();
        request.setCaCertificateId(1L);
        request.setPurpose("项目投标用章");
        request.setProjectId(1001L);
        request.setProjectName("测试项目");
        request.setBorrowDurationType("SHORT_TERM");
        request.setExpectedReturnDate(LocalDate.now().plusDays(7));
        return request;
    }
}
