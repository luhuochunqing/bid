package com.xiyu.bid.bidresult.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AwardRegistrationValidationTest {

    @Test
    @DisplayName("Should pass validation for a valid WON registration")
    void testValidWonRegistration() {
        AwardRegistration reg = new AwardRegistration(
                1L, "Project A", AwardRegistration.ResultOutcome.WON,
                new BigDecimal("1000.00"), LocalDate.now(), LocalDate.now().plusDays(30),
                1, "Notes", 5, "https://docs.com/win"
        );

        AwardRegistrationValidation.ValidationResult result = AwardRegistrationValidation.validate(reg);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when projectId is missing")
    void testMissingProjectId() {
        AwardRegistration reg = new AwardRegistration(
                null, "Project A", AwardRegistration.ResultOutcome.WON,
                new BigDecimal("1000.00"), null, null, null, null, null, null
        );

        AwardRegistrationValidation.ValidationResult result = AwardRegistrationValidation.validate(reg);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("项目不能为空"));
    }

    @Test
    @DisplayName("Should fail validation when amount is missing for WON result")
    void testMissingAmountForWon() {
        AwardRegistration reg = new AwardRegistration(
                1L, "Project A", AwardRegistration.ResultOutcome.WON,
                null, null, null, null, null, null, null
        );

        AwardRegistrationValidation.ValidationResult result = AwardRegistrationValidation.validate(reg);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("中标时必须填写金额且大于 0"));
    }

    @Test
    @DisplayName("Should fail validation when contract dates are invalid")
    void testInvalidContractDates() {
        AwardRegistration reg = new AwardRegistration(
                1L, "Project A", AwardRegistration.ResultOutcome.LOST,
                null, LocalDate.now().plusDays(10), LocalDate.now(), 
                null, null, null, null
        );

        AwardRegistrationValidation.ValidationResult result = AwardRegistrationValidation.validate(reg);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("合同结束日期不得早于开始日期"));
    }

    @Test
    @DisplayName("ValidationResult should defensively copy error list")
    void validationResultShouldDefensivelyCopyErrors() {
        ArrayList<String> errors = new ArrayList<>(List.of("A", "B"));

        AwardRegistrationValidation.ValidationResult result =
                AwardRegistrationValidation.ValidationResult.failure(errors);

        errors.add("C");

        assertEquals(List.of("A", "B"), result.errors());
        assertThrows(UnsupportedOperationException.class, () -> result.errors().add("D"));
    }
}
