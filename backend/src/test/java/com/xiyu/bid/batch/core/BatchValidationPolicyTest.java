package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchValidationPolicyTest {

    private final BatchValidationPolicy policy = new BatchValidationPolicy();

    @Test
    void validateBatchInput_RejectsOversizedBatch() {
        assertThatThrownBy(() -> policy.validateBatchInput(Collections.nCopies(101, 1L), "IDs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    void validateUserId_RejectsNullAndNegative() {
        assertThatThrownBy(() -> policy.validateUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
        assertThatThrownBy(() -> policy.validateUserId(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void validateUserRole_RejectsNull() {
        assertThatThrownBy(() -> policy.validateUserRole((User.Role) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }
}
