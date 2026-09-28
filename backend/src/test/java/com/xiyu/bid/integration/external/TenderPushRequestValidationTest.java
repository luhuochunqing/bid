package com.xiyu.bid.integration.external;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenderPushRequest Bean Validation 校验规则测试。
 * <p>覆盖 /api/integration/tenders/push 必填字段约束：
 * customerName（招标主体）、bidOpeningTime（开标时间）、registrationDeadline（报名截止时间）。
 */
class TenderPushRequestValidationTest {

    private static Validator validator;
    private static ValidatorFactory factory;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private TenderPushRequest buildValidRequest() {
        TenderPushRequest req = new TenderPushRequest();
        req.setSourceSystem("CRM");
        req.setSourceId("900");
        req.setTitle("测试推送");
        req.setCustomerName("西域智慧供应链（上海）股份公司");
        req.setRegistrationDeadline("2026-07-15T18:20:00");
        req.setBidOpeningTime("2026-07-18T18:20:00");
        return req;
    }

    @Test
    @DisplayName("完整请求 → 校验通过")
    void validRequest_shouldPass() {
        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(buildValidRequest());
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("customerName 为 null → 校验失败")
    void nullCustomerName_shouldFail() {
        TenderPushRequest req = buildValidRequest();
        req.setCustomerName(null);

        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(req);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("customerName");
            assertThat(v.getMessage()).isEqualTo("招标主体不能为空");
        });
    }

    @Test
    @DisplayName("customerName 为空白字符串 → 校验失败")
    void blankCustomerName_shouldFail() {
        TenderPushRequest req = buildValidRequest();
        req.setCustomerName("  ");

        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(req);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("customerName");
            assertThat(v.getMessage()).isEqualTo("招标主体不能为空");
        });
    }

    @Test
    @DisplayName("registrationDeadline 为 null → 校验失败")
    void nullRegistrationDeadline_shouldFail() {
        TenderPushRequest req = buildValidRequest();
        req.setRegistrationDeadline(null);

        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(req);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("registrationDeadline");
            assertThat(v.getMessage()).isEqualTo("报名截止时间不能为空");
        });
    }

    @Test
    @DisplayName("registrationDeadline 为空白字符串 → 校验失败")
    void blankRegistrationDeadline_shouldFail() {
        TenderPushRequest req = buildValidRequest();
        req.setRegistrationDeadline("  ");

        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(req);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("registrationDeadline");
            assertThat(v.getMessage()).isEqualTo("报名截止时间不能为空");
        });
    }

    @Test
    @DisplayName("bidOpeningTime 为 null → 校验失败")
    void nullBidOpeningTime_shouldFail() {
        TenderPushRequest req = buildValidRequest();
        req.setBidOpeningTime(null);

        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(req);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("bidOpeningTime");
            assertThat(v.getMessage()).isEqualTo("开标时间不能为空");
        });
    }

    @Test
    @DisplayName("bidOpeningTime 为空白字符串 → 校验失败")
    void blankBidOpeningTime_shouldFail() {
        TenderPushRequest req = buildValidRequest();
        req.setBidOpeningTime("  ");

        Set<ConstraintViolation<TenderPushRequest>> violations = validator.validate(req);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.getPropertyPath().toString()).isEqualTo("bidOpeningTime");
            assertThat(v.getMessage()).isEqualTo("开标时间不能为空");
        });
    }
}
