package com.xiyu.bid.platform.async.application;

import com.xiyu.bid.platform.async.domain.AsyncAction;
import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;
import com.xiyu.bid.platform.async.domain.ExponentialBackoffRetrySchedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncDecisionResolverTest {
    private final AsyncDecisionResolver resolver = new AsyncDecisionResolver();
    private final ExponentialBackoffRetrySchedule schedule = new ExponentialBackoffRetrySchedule(300, 3600, 4);

    @Test
    void shouldRetryTransientDependencyBeforeExhausted() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.TRANSIENT_DEPENDENCY,
                1,
                5,
                schedule,
                true
        );

        assertEquals(AsyncAction.RETRY, decision.action());
        assertEquals("TRANSIENT_DEPENDENCY", decision.reasonCode());
        assertEquals(600, decision.nextRetryDelaySeconds());
        assertFalse(decision.alertRequired());
    }

    @Test
    void shouldDeadLetterTransientDependencyAfterExhausted() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.TRANSIENT_DEPENDENCY,
                5,
                5,
                schedule,
                true
        );

        assertEquals(AsyncAction.DEAD_LETTER, decision.action());
        assertEquals("TRANSIENT_DEPENDENCY_EXHAUSTED", decision.reasonCode());
        assertTrue(decision.alertRequired());
    }

    @Test
    void shouldDropOptionalSideEffect() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.SIDE_EFFECT_OPTIONAL,
                0,
                3,
                schedule,
                false
        );

        assertEquals(AsyncAction.DROP, decision.action());
        assertEquals("SIDE_EFFECT_OPTIONAL", decision.reasonCode());
        assertFalse(decision.alertRequired());
    }

    @Test
    void shouldDeadLetterPersistentDependency() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.PERSISTENT_DEPENDENCY,
                1,
                3,
                schedule,
                true
        );

        assertEquals(AsyncAction.DEAD_LETTER, decision.action());
        assertEquals("PERSISTENT_DEPENDENCY", decision.reasonCode());
        assertTrue(decision.alertRequired());
    }

    @Test
    void shouldDeadLetterContractInvalid() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.CONTRACT_INVALID,
                1,
                3,
                schedule,
                true
        );

        assertEquals(AsyncAction.DEAD_LETTER, decision.action());
        assertEquals("CONTRACT_INVALID", decision.reasonCode());
        assertTrue(decision.alertRequired());
    }

    @Test
    void shouldDeadLetterBug() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.BUG,
                1,
                3,
                schedule,
                true
        );

        assertEquals(AsyncAction.DEAD_LETTER, decision.action());
        assertEquals("BUG", decision.reasonCode());
        assertTrue(decision.alertRequired());
    }

    @Test
    void shouldSucceedWithLogForIdempotentDuplicate() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.IDEMPOTENT_DUPLICATE,
                1,
                3,
                schedule,
                true
        );

        assertEquals(AsyncAction.SUCCEED_WITH_LOG, decision.action());
        assertEquals("IDEMPOTENT_DUPLICATE", decision.reasonCode());
        assertFalse(decision.alertRequired());
    }

    @Test
    void shouldFailMainTransactionWhenRequired() {
        AsyncHandlingDecision decision = resolver.resolve(
                AsyncFailureKind.MAIN_TRANSACTION_REQUIRED,
                0,
                1,
                schedule,
                false
        );

        assertEquals(AsyncAction.FAIL_MAIN_TRANSACTION, decision.action());
        assertTrue(decision.rollbackRequired());
        assertTrue(decision.alertRequired());
    }
}
