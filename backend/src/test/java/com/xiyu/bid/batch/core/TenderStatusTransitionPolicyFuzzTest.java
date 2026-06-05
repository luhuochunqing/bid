package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-4 fuzz specialization: exhaustive and randomized property tests for
 * {@link TenderStatusTransitionPolicy}. Verifies the transition table is
 * exactly what production contracts require — no accidental widening.
 */
class TenderStatusTransitionPolicyFuzzTest {

    private static final long FUZZ_SEED = 0xF4_1EADL;
    private static final int FUZZ_ITERATIONS = 2_000;

    private final TenderStatusTransitionPolicy policy = new TenderStatusTransitionPolicy();

    static Stream<org.junit.jupiter.params.provider.Arguments> allPairs() {
        return Stream.of(Tender.Status.values())
                .flatMap(from -> Stream.of(Tender.Status.values())
                        .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void exhaustiveMatrixMatchesSpec(Tender.Status from, Tender.Status to) {
        boolean expected = isAllowedBySpec(from, to);
        assertEquals(expected, policy.canTransition(from, to),
                () -> String.format("transition %s -> %s should be %s", from, to, expected ? "allowed" : "denied"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void assertTransitionThrowsIffCanTransitionIsFalse(Tender.Status from, Tender.Status to) {
        if (policy.canTransition(from, to)) {
            assertDoesNotThrow(() -> policy.assertTransition(from, to));
        } else {
            assertThrows(IllegalArgumentException.class, () -> policy.assertTransition(from, to));
        }
    }

    @ParameterizedTest
    @EnumSource(Tender.Status.class)
    void selfTransitionIsAlwaysAllowed(Tender.Status status) {
        assertTrue(policy.canTransition(status, status));
        assertDoesNotThrow(() -> policy.assertTransition(status, status));
    }

    @ParameterizedTest
    @EnumSource(Tender.Status.class)
    void biddingTransitionsToExpectedSinkStates(Tender.Status other) {
        if (other == Tender.Status.BIDDING) {
            return;
        }
        boolean allowed = policy.canTransition(Tender.Status.BIDDING, other);
        assertEquals(
            other == Tender.Status.WON || other == Tender.Status.LOST || other == Tender.Status.ABANDONED,
            allowed,
            () -> "BIDDING -> " + other + " should only be allowed for WON/LOST/ABANDONED"
        );
    }

    @Test
    void nullInputsAreRejectedWithoutThrowing() {
        for (Tender.Status status : Tender.Status.values()) {
            assertFalse(policy.canTransition(null, status));
            assertFalse(policy.canTransition(status, null));
        }
        assertFalse(policy.canTransition(null, null));
        assertThrows(IllegalArgumentException.class, () -> policy.assertTransition(null, null));
    }

    @ParameterizedTest
    @MethodSource("allPairs")
    void canTransitionIsDeterministic(Tender.Status from, Tender.Status to) {
        boolean first = policy.canTransition(from, to);
        for (int i = 0; i < 16; i++) {
            assertEquals(first, policy.canTransition(from, to));
        }
    }

    @Test
    void randomWalkFromInitialStaysInsideLegalGraph() {
        Random rng = new Random(FUZZ_SEED);
        Tender.Status[] universe = Tender.Status.values();
        Tender.Status current = Tender.Status.PENDING_ASSIGNMENT;

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            Tender.Status candidate = universe[rng.nextInt(universe.length)];
            if (policy.canTransition(current, candidate)) {
                current = candidate;
            }
            assertNotNull(current);
        }
    }

    @Test
    void randomInjectionNeverWidensTransitionGraph() {
        Random rng = new Random(FUZZ_SEED ^ 0x5A5AL);
        Tender.Status[] universe = Tender.Status.values();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            Tender.Status from = universe[rng.nextInt(universe.length)];
            Tender.Status to = universe[rng.nextInt(universe.length)];
            boolean actual = policy.canTransition(from, to);
            assertEquals(isAllowedBySpec(from, to), actual,
                    () -> String.format("random pair %s -> %s diverged from spec", from, to));
        }
    }

    /**
     * Duplicated authoritative spec kept in the test so a drift in the policy
     * produces a red test, not a silent widening.
     */
    /**
     * Authoritative spec from design.md.
     * PENDING_ASSIGNMENT -> TRACKING, ABANDONED
     * TRACKING -> PENDING_ASSIGNMENT (revert), EVALUATED, ABANDONED
     * EVALUATED -> BIDDING, ABANDONED
     * BIDDING -> WON, LOST, ABANDONED
     * WON/LOST/ABANDONED -> (terminal, no transitions)
     */
    private static boolean isAllowedBySpec(Tender.Status from, Tender.Status to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return switch (from) {
            case PENDING_ASSIGNMENT -> to == Tender.Status.TRACKING || to == Tender.Status.ABANDONED;
            case TRACKING -> to == Tender.Status.PENDING_ASSIGNMENT || to == Tender.Status.EVALUATED || to == Tender.Status.ABANDONED;
            case EVALUATED -> to == Tender.Status.BIDDING || to == Tender.Status.ABANDONED;
            case BIDDING -> to == Tender.Status.WON || to == Tender.Status.LOST || to == Tender.Status.ABANDONED;
            case WON, LOST, ABANDONED -> false;
        };
    }
}
