package com.xiyu.bid.task.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.stream.Stream;

import static com.xiyu.bid.task.core.TaskTransitionPolicy.TaskStatus;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-4 fuzz specialization: exhaustive and randomized property tests for
 * {@link TaskTransitionPolicy}. Guards the policy against silent widening or
 * narrowing via duplicated authoritative spec in the test.
 */
class TaskTransitionPolicyFuzzTest {

    private static final long FUZZ_SEED = 0xF4_7A5CL;
    private static final int FUZZ_ITERATIONS = 2_000;

    static Stream<org.junit.jupiter.params.provider.Arguments> allPairs() {
        return Stream.of(TaskStatus.values())
                .flatMap(from -> Stream.of(TaskStatus.values())
                        .map(to -> org.junit.jupiter.params.provider.Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void exhaustiveMatrixMatchesSpec(TaskStatus from, TaskStatus to) {
        boolean expected = isAllowedBySpec(from, to);
        boolean actual = TaskTransitionPolicy.validateTransition(from, to).allowed();
        assertThat(actual)
                .as("transition %s -> %s expected %s", from, to, expected ? "allowed" : "denied")
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void selfTransitionIsAlwaysAllowed(TaskStatus status) {
        assertThat(TaskTransitionPolicy.validateTransition(status, status).allowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    void completedIsTerminalSink(TaskStatus other) {
        if (other == TaskStatus.COMPLETED) {
            return;
        }
        var result = TaskTransitionPolicy.validateTransition(TaskStatus.COMPLETED, other);
        assertThat(result.allowed())
                .as("COMPLETED is terminal; COMPLETED -> %s must be denied", other)
                .isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    void nullInputsAreDeniedWithReason() {
        for (TaskStatus status : TaskStatus.values()) {
            var r1 = TaskTransitionPolicy.validateTransition(null, status);
            var r2 = TaskTransitionPolicy.validateTransition(status, null);
            assertThat(r1.allowed()).isFalse();
            assertThat(r2.allowed()).isFalse();
            assertThat(r1.reason()).isNotBlank();
            assertThat(r2.reason()).isNotBlank();
        }
        var both = TaskTransitionPolicy.validateTransition(null, null);
        assertThat(both.allowed()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("allPairs")
    void validateTransitionIsDeterministic(TaskStatus from, TaskStatus to) {
        boolean first = TaskTransitionPolicy.validateTransition(from, to).allowed();
        for (int i = 0; i < 16; i++) {
            assertThat(TaskTransitionPolicy.validateTransition(from, to).allowed()).isEqualTo(first);
        }
    }

    @Test
    void randomInjectionNeverWidensTransitionGraph() {
        Random rng = new Random(FUZZ_SEED);
        TaskStatus[] universe = TaskStatus.values();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            TaskStatus from = universe[rng.nextInt(universe.length)];
            TaskStatus to = universe[rng.nextInt(universe.length)];
            boolean expected = isAllowedBySpec(from, to);
            boolean actual = TaskTransitionPolicy.validateTransition(from, to).allowed();
            assertThat(actual)
                    .as("random pair %s -> %s diverged from spec", from, to)
                    .isEqualTo(expected);
        }
    }

    @Test
    void randomWalkFromTodoTerminatesOrStaysInReachableSet() {
        Random rng = new Random(FUZZ_SEED ^ 0x1234L);
        TaskStatus[] universe = TaskStatus.values();
        TaskStatus current = TaskStatus.TODO;

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            TaskStatus candidate = universe[rng.nextInt(universe.length)];
            if (TaskTransitionPolicy.validateTransition(current, candidate).allowed()) {
                current = candidate;
            }
            assertThat(current).isNotNull();
        }
    }

    @Test
    void computeAutoStatusFuzzAlwaysProducesReachableOrCurrent() {
        Random rng = new Random(FUZZ_SEED ^ 0xABCDL);
        TaskStatus[] universe = TaskStatus.values();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            TaskStatus current = universe[rng.nextInt(universe.length)];
            // Mix of negative, zero, positive and boundary counts.
            int existingCount = rng.nextInt(1000) - 100;
            TaskStatus suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(current, existingCount);
            assertThat(suggested).isNotNull();
            // Invariant: suggestion must equal current OR be a legal direct transition target.
            boolean legal = suggested == current
                    || TaskTransitionPolicy.validateTransition(current, suggested).allowed();
            assertThat(legal)
                    .as("auto-status %s from current=%s count=%d must equal current or be a legal target",
                            suggested, current, existingCount)
                    .isTrue();
        }
    }

    @Test
    void computeAutoStatusAlwaysReturnsCurrentStatus() {
        // CO-361：三态模型收口，上传交付物不改变任务状态
        for (TaskStatus current : TaskStatus.values()) {
            for (int count : new int[]{0, 1, 5, 100}) {
                TaskStatus suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(current, count);
                assertThat(suggested).isEqualTo(current);
            }
        }
    }

    @Test
    void computeAutoStatusNullCurrentDefaultsToTodo() {
        assertThat(TaskTransitionPolicy.computeAutoStatusOnDeliverable(null, 0))
                .isEqualTo(TaskStatus.TODO);
        assertThat(TaskTransitionPolicy.computeAutoStatusOnDeliverable(null, 999))
                .isEqualTo(TaskStatus.TODO);
    }

    private static boolean isAllowedBySpec(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return switch (from) {
            // CO-361 三态模型：TODO → REVIEW → COMPLETED（审核驳回回 TODO）
            case TODO -> to == TaskStatus.REVIEW;
            case REVIEW -> to == TaskStatus.COMPLETED || to == TaskStatus.TODO;
            case COMPLETED -> false;
        };
    }
}
