package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderStatusTransitionPolicyTest {

    private final TenderStatusTransitionPolicy policy = new TenderStatusTransitionPolicy();

    @Test
    void shouldAllowPendingToTracking() {
        assertTrue(policy.canTransition(Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING));
        assertDoesNotThrow(() -> policy.assertTransition(Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING));
    }

    @Test
    void shouldAllowTrackingToEvaluated() {
        assertTrue(policy.canTransition(Tender.Status.TRACKING, Tender.Status.EVALUATED));
    }

    @Test
    void shouldAllowEvaluatedToBidding() {
        assertTrue(policy.canTransition(Tender.Status.EVALUATED, Tender.Status.BIDDING));
    }

    @Test
    void shouldAllowBiddingToWonOrLost() {
        assertTrue(policy.canTransition(Tender.Status.BIDDING, Tender.Status.WON));
        assertTrue(policy.canTransition(Tender.Status.BIDDING, Tender.Status.LOST));
    }

    @Test
    void shouldAllowAnyStateToAbandon() {
        assertTrue(policy.canTransition(Tender.Status.PENDING_ASSIGNMENT, Tender.Status.ABANDONED));
        assertTrue(policy.canTransition(Tender.Status.TRACKING, Tender.Status.ABANDONED));
        assertTrue(policy.canTransition(Tender.Status.EVALUATED, Tender.Status.ABANDONED));
        assertTrue(policy.canTransition(Tender.Status.BIDDING, Tender.Status.ABANDONED));
    }

    @Test
    void shouldRejectIllegalTransitions() {
        assertFalse(policy.canTransition(Tender.Status.PENDING_ASSIGNMENT, Tender.Status.BIDDING));
        assertFalse(policy.canTransition(Tender.Status.WON, Tender.Status.BIDDING));
        assertThrows(IllegalArgumentException.class,
                () -> policy.assertTransition(Tender.Status.WON, Tender.Status.BIDDING));
    }
}
