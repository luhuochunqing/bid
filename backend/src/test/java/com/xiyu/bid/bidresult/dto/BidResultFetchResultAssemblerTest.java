package com.xiyu.bid.bidresult.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BidResultFetchResultAssemblerTest {

    @Test
    void toDto_shouldTreatNullEntityAsNormalAbsence() {
        assertThat(BidResultFetchResultAssembler.toDto(null)).isNull();
    }
}
