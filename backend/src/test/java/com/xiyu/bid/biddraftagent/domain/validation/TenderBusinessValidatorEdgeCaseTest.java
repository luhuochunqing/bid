package com.xiyu.bid.biddraftagent.domain.validation;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderBusinessValidatorEdgeCaseTest {

    private final TenderBusinessValidator validator = new TenderBusinessValidator();

    @Test
    void testNegativeBudget() {
        TenderRequirementProfile profile = new TenderRequirementProfile(
                null, null, null, null,
                new BigDecimal("-500"), null, null,
                null, null,
                List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of()
        );
        List<String> warnings = validator.validate(profile, LocalDate.now());
        assertThat(warnings).anyMatch(s -> s.contains("预算金额异常偏低"));
    }
}
