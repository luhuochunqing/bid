package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-4 fuzz specialization: exhaustive and randomized property tests for
 * {@link FormInstanceStatusPolicy}. The policy differs from task / tender
 * transition policies in that self-transition is NOT allowed — this test
 * pins that asymmetry so a future refactor cannot silently widen it.
 */
class FormInstanceStatusPolicyFuzzTest {

    private static final long FUZZ_SEED = 0xF4_F0_7DL;
    private static final int FUZZ_ITERATIONS = 2_000;

    private static final Set<WorkflowFormStatus> TERMINAL_STATES = Set.of(
            WorkflowFormStatus.OA_REJECTED,
            WorkflowFormStatus.OA_FAILED,
            WorkflowFormStatus.BUSINESS_APPLIED
    );

    static Stream<org.junit.jupiter.params.provider.Arguments> allPairs() {
        return Stream.of(WorkflowFormStatus.values())
                .flatMap(from -> Stream.of(WorkflowFormStatus.values())
                        .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void exhaustiveMatrixMatchesSpec(WorkflowFormStatus from, WorkflowFormStatus to) {
        boolean expected = isAllowedBySpec(from, to);
        boolean actual = FormInstanceStatusPolicy.canTransit(from, to);
        assertThat(actual)
                .as("transition %s -> %s expected %s", from, to, expected ? "allowed" : "denied")
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(WorkflowFormStatus.class)
    void selfTransitionIsNotAllowed(WorkflowFormStatus status) {
        // Policy-specific invariant: OA submissions do not permit self-loops,
        // because the driving system (OA callback) always moves state forward.
        assertThat(FormInstanceStatusPolicy.canTransit(status, status))
                .as("self-transition %s -> %s must be denied", status, status)
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkflowFormStatus.class)
    void terminalStatesHaveNoOutgoingTransitions(WorkflowFormStatus to) {
        for (WorkflowFormStatus terminal : TERMINAL_STATES) {
            assertThat(FormInstanceStatusPolicy.canTransit(terminal, to))
                    .as("terminal %s must have no outgoing transition to %s", terminal, to)
                    .isFalse();
        }
    }

    @Test
    void nullInputsNeverAllowTransition() {
        for (WorkflowFormStatus status : WorkflowFormStatus.values()) {
            assertNeverReturnsTrue(() -> FormInstanceStatusPolicy.canTransit(null, status));
            assertNeverReturnsTrue(() -> FormInstanceStatusPolicy.canTransit(status, null));
        }
        assertNeverReturnsTrue(() -> FormInstanceStatusPolicy.canTransit(null, null));
    }

    @ParameterizedTest
    @MethodSource("allPairs")
    void canTransitIsDeterministic(WorkflowFormStatus from, WorkflowFormStatus to) {
        boolean first = FormInstanceStatusPolicy.canTransit(from, to);
        for (int i = 0; i < 16; i++) {
            assertThat(FormInstanceStatusPolicy.canTransit(from, to)).isEqualTo(first);
        }
    }

    @Test
    void randomInjectionNeverWidensTransitionGraph() {
        Random rng = new Random(FUZZ_SEED);
        WorkflowFormStatus[] universe = WorkflowFormStatus.values();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            WorkflowFormStatus from = universe[rng.nextInt(universe.length)];
            WorkflowFormStatus to = universe[rng.nextInt(universe.length)];
            boolean expected = isAllowedBySpec(from, to);
            boolean actual = FormInstanceStatusPolicy.canTransit(from, to);
            assertThat(actual)
                    .as("random pair %s -> %s diverged from spec", from, to)
                    .isEqualTo(expected);
        }
    }

    @Test
    void randomWalkFromDraftReachesOnlyKnownStates() {
        Random rng = new Random(FUZZ_SEED ^ 0x99L);
        WorkflowFormStatus[] universe = WorkflowFormStatus.values();
        WorkflowFormStatus current = WorkflowFormStatus.DRAFT;

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            WorkflowFormStatus candidate = universe[rng.nextInt(universe.length)];
            if (FormInstanceStatusPolicy.canTransit(current, candidate)) {
                current = candidate;
            }
            assertThat(current).isNotNull();
            assertThat(Stream.of(universe)).contains(current);
        }
    }

    private static void assertNeverReturnsTrue(java.util.function.BooleanSupplier call) {
        try {
            assertThat(call.getAsBoolean()).isFalse();
        } catch (NullPointerException expectedForNull) {
            // Acceptable: Map.of / Set.of reject null lookups. Either way,
            // the key property is that null inputs never silently allow a
            // transition.
        }
    }

    /**
     * Duplicated authoritative spec kept in the test so a drift in the policy
     * produces a red test, not a silent widening. Self-transitions are NOT
     * allowed per the OA lifecycle.
     */
    private static boolean isAllowedBySpec(WorkflowFormStatus from, WorkflowFormStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case DRAFT -> to == WorkflowFormStatus.SUBMITTED;
            case SUBMITTED -> to == WorkflowFormStatus.OA_STARTING
                    || to == WorkflowFormStatus.OA_APPROVING
                    || to == WorkflowFormStatus.OA_FAILED;
            case OA_STARTING -> to == WorkflowFormStatus.OA_APPROVING
                    || to == WorkflowFormStatus.OA_FAILED;
            case OA_APPROVING -> to == WorkflowFormStatus.OA_APPROVED
                    || to == WorkflowFormStatus.OA_REJECTED
                    || to == WorkflowFormStatus.OA_FAILED;
            case OA_APPROVED -> to == WorkflowFormStatus.BUSINESS_APPLIED;
            case OA_REJECTED, OA_FAILED, BUSINESS_APPLIED -> false;
        };
    }
}
