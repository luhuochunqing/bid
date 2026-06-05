package com.xiyu.bid.batch.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchOperationBoundaryTest extends AbstractBatchOperationServiceTest {

    @Test
    void batchOperation_ExceedsMaxBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchClaimTenders(java.util.Collections.nCopies(101, 1L), 100L));
    }

    @Test
    void batchOperation_NullIds() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchClaimTenders(null, 100L));
    }

    @Test
    void batchOperation_NullUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchClaimTenders(Collections.singletonList(1L), null));
    }

    @Test
    void batchOperation_NegativeUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> batchOperationService.batchClaimTenders(Collections.singletonList(1L), -1L));
    }
}
