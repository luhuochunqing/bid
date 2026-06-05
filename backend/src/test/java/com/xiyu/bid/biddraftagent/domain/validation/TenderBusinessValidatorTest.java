// Input: TenderRequirementProfile（预算、发布日期、截止日期）+ 注入的当前日期
// Output: 业务校验警告列表覆盖测试（纯函数：currentDate 由调用方提供）
// Pos: Test/biddraftagent/domain/validation
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.biddraftagent.domain.validation;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderBusinessValidatorTest {

    private static final LocalDate FIXED_TODAY = LocalDate.of(2025, 6, 1);

    private final TenderBusinessValidator validator = new TenderBusinessValidator();

    @Test
    void shouldWarnOnLowBudget() {
        TenderRequirementProfile profile = createProfile(new BigDecimal("5000"), null, null);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).anyMatch(s -> s.contains("异常偏低"));
    }

    @Test
    void shouldWarnOnHighBudget() {
        TenderRequirementProfile profile = createProfile(new BigDecimal("20000000000"), null, null);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).anyMatch(s -> s.contains("异常偏高"));
    }

    @Test
    void shouldWarnOnNullBudget() {
        TenderRequirementProfile profile = createProfile(null, null, null);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).anyMatch(s -> s.contains("未能提取到预算金额"));
    }

    @Test
    void shouldNotWarnOnNormalBudget() {
        TenderRequirementProfile profile = createProfile(new BigDecimal("5000000"), null, null);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).noneMatch(s -> s.contains("预算金额异常"));
    }

    @Test
    void shouldWarnWhenDeadlineBeforePublish() {
        LocalDate publish = LocalDate.of(2025, 6, 5);
        LocalDateTime deadline = LocalDateTime.of(2025, 6, 4, 0, 0);
        TenderRequirementProfile profile = createProfile(null, publish, deadline);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).anyMatch(s -> s.contains("不能早于发布日期"));
    }

    @Test
    void shouldWarnOnTightTimeline() {
        LocalDate publish = LocalDate.of(2025, 6, 1);
        LocalDateTime deadline = LocalDateTime.of(2025, 6, 2, 23, 59);
        TenderRequirementProfile profile = createProfile(null, publish, deadline);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).anyMatch(s -> s.contains("时间过短"));
    }

    @Test
    void shouldWarnWhenDeadlineInPast() {
        LocalDate publish = LocalDate.of(2025, 4, 1);
        LocalDateTime deadline = LocalDateTime.of(2025, 5, 1, 0, 0);
        TenderRequirementProfile profile = createProfile(null, publish, deadline);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).anyMatch(s -> s.contains("已过期"));
    }

    @Test
    void shouldNotWarnOnFutureDeadlineWithSufficientTimeline() {
        LocalDate publish = LocalDate.of(2025, 5, 1);
        LocalDateTime deadline = LocalDateTime.of(2025, 7, 1, 0, 0);
        TenderRequirementProfile profile = createProfile(null, publish, deadline);
        List<String> warnings = validator.validate(profile, FIXED_TODAY);
        assertThat(warnings).noneMatch(s -> s.contains("不能早于") || s.contains("时间过短") || s.contains("已过期"));
    }

    private TenderRequirementProfile createProfile(BigDecimal budget, LocalDate publish, LocalDateTime deadline) {
        return new TenderRequirementProfile(
                null, null, null, null,
                budget, null, null,
                publish, deadline,
                List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of()
        );
    }
}
