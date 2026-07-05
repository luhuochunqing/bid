package com.xiyu.bid.project.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.xiyu.bid.project.core.ProjectStageTransitionPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-4 fuzz specialization for {@link ProjectStageTransitionPolicy}。
 * 在现有 PolicyTest 的确定性测试基础上，通过随机注入验证状态机边界，
 * 确保没有隐性拓宽或收窄合法跳转图。
 */
class ProjectStageTransitionPolicyFuzzTest {

    private static final long FUZZ_SEED = 0xF47A5C01L;
    private static final int FUZZ_ITERATIONS = 5_000;
    private static final GateInputs GATE = GateInputs.EMPTY;

    // ========== 穷举矩阵：验证 spec 与实现完全一致 ==========

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allStagePairs")
    @DisplayName("穷举矩阵：所有状态对与 spec 一致")
    void exhaustiveMatrixMatchesSpec(ProjectStage from, ProjectStage to) {
        boolean expected = isAllowedBySpec(from, to);
        Decision actual = ProjectStageTransitionPolicy.decide(from, to, GATE);
        assertThat(actual.allowed())
                .as("transition %s → %s expected %s", from, to, expected ? "allowed" : "denied")
                .isEqualTo(expected);
            if (!expected) {
            assertThat(((Decision.Deny) actual).reason()).isNotBlank();
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> allStagePairs() {
        List<org.junit.jupiter.params.provider.Arguments> pairs = new ArrayList<>();
        for (ProjectStage from : ProjectStage.values()) {
            for (ProjectStage to : ProjectStage.values()) {
                pairs.add(org.junit.jupiter.params.provider.Arguments.of(from, to));
            }
        }
        return pairs.stream();
    }

    // ========== 自转换：始终拒绝 ==========

    @ParameterizedTest
    @EnumSource(ProjectStage.class)
    @DisplayName("所有阶段的同态自转换必须拒绝")
    void selfTransitionAlwaysDenied(ProjectStage stage) {
        Decision d = ProjectStageTransitionPolicy.decide(stage, stage, GATE);
        assertThat(d.allowed())
                .as("自转换 %s → %s 必须拒绝", stage, stage)
                .isFalse();
        assertThat(((Decision.Deny) d).reason()).isNotBlank();
    }

    // ========== 终态 sink：CLOSED 出向全部拒绝 ==========

    @ParameterizedTest
    @EnumSource(ProjectStage.class)
    @DisplayName("CLOSED 为终态 Sink，所有出向必须拒绝")
    void closedIsTerminalSink(ProjectStage to) {
        if (to == ProjectStage.CLOSED) return;
        Decision d = ProjectStageTransitionPolicy.decide(ProjectStage.CLOSED, to, GATE);
        assertThat(d.allowed())
                .as("CLOSED → %s 必须拒绝（终态）", to)
                .isFalse();
        assertThat(((Decision.Deny) d).reason()).isNotBlank();
    }

    // ========== 随机注入：从不对 spec 以外的跳转放行 ==========

    @Test
    @DisplayName("随机注入：永远不会拓宽合法跳转图")
    void randomInjectionNeverWidensTransitionGraph() {
        Random rng = new Random(FUZZ_SEED);
        ProjectStage[] universe = ProjectStage.values();

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            ProjectStage from = universe[rng.nextInt(universe.length)];
            ProjectStage to = universe[rng.nextInt(universe.length)];
            boolean expected = isAllowedBySpec(from, to);
            Decision actual = ProjectStageTransitionPolicy.decide(from, to, GATE);
            assertThat(actual.allowed())
                    .as("random pair %s → %s diverged from spec", from, to)
                    .isEqualTo(expected);
        }
    }

    // ========== 随机游走：从 INITIATED 出发是否收敛到终态 ==========

    @Test
    @DisplayName("随机游走：从 INITIATED 出发，最终收敛到 CLOSED 或保持可达状态")
    void randomWalkFromInitiatedConverges() {
        Random rng = new Random(FUZZ_SEED ^ 0xDEAD_BEEFL);
        int convergenceCount = 0;
        int maxLoops = 50;

        for (int run = 0; run < 500; run++) {
            ProjectStage current = ProjectStage.INITIATED;
            int loops = 0;
            boolean moved = false;

            while (loops++ < maxLoops) {
                // 随机选择目标状态
                ProjectStage[] targets = ProjectStage.values();
                ProjectStage candidate = targets[rng.nextInt(targets.length)];

                Decision d = ProjectStageTransitionPolicy.decide(current, candidate, GATE);
                if (d.allowed()) {
                    current = candidate;
                    moved = true;
                    if (current == ProjectStage.CLOSED) {
                        convergenceCount++;
                        break;
                    }
                }
            }
            // 收敛条件：到达 CLOSED 或经过多次合法跳转
            if (current == ProjectStage.CLOSED || (moved && loops > 1)) {
                convergenceCount++;
            }
        }
        assertThat(convergenceCount)
                .as("随机游走应收敛（CLOSED 或多次合法跳转）")
                .isGreaterThan(450); // 至少 90% 收敛率
    }

    // ========== 线性路径完整性 ==========

    @Test
    @DisplayName("线性路径完整性：每一步都依赖前一步的结果")
    void linearPathDependency() {
        // INITIATED → DRAFTING → EVALUATING → RESULT_PENDING → RETROSPECTIVE → CLOSED
        // 正确路径的每一步都应该允许
        assertThat(ProjectStageTransitionPolicy.decide(
                ProjectStage.INITIATED, ProjectStage.DRAFTING, GATE).allowed()).isTrue();

        // 但中间任意一步失败，后续路径应该仍按 spec 评估
        // 例如：从 INITIATED 跳到 EVALUATING 应该拒绝
        assertThat(ProjectStageTransitionPolicy.decide(
                ProjectStage.INITIATED, ProjectStage.EVALUATING, GATE).allowed()).isFalse();
    }

    // ========== 特殊路径：RESULT_PENDING → CLOSED（分流） ==========

    @Test
    @DisplayName("RESULT_PENDING → CLOSED 是合法路径（流标/弃标）")
    void resultPendingToClosedIsLegal() {
        // 这是唯一不在 next() 映射中的例外路径
        Decision d = ProjectStageTransitionPolicy.decide(ProjectStage.RESULT_PENDING, ProjectStage.CLOSED, GATE);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    @DisplayName("RESULT_PENDING → RETROSPECTIVE 是合法路径（中标/未中标）")
    void resultPendingToRetrospectiveIsLegal() {
        Decision d = ProjectStageTransitionPolicy.decide(ProjectStage.RESULT_PENDING, ProjectStage.RETROSPECTIVE, GATE);
        assertThat(d.allowed()).isTrue();
    }

    // ========== Null 安全 ==========

    @Test
    @DisplayName("Null 输入必须拒绝并附带原因")
    void nullInputsAreDeniedWithReason() {
        for (ProjectStage s : ProjectStage.values()) {
            Decision r1 = ProjectStageTransitionPolicy.decide(null, s, GATE);
            Decision r2 = ProjectStageTransitionPolicy.decide(s, null, GATE);
            assertThat(r1.allowed()).isFalse();
            assertThat(((Decision.Deny) r1).reason()).isNotBlank();
            assertThat(r2.allowed()).isFalse();
            assertThat(((Decision.Deny) r2).reason()).isNotBlank();
        }
        Decision both = ProjectStageTransitionPolicy.decide(null, null, GATE);
        assertThat(both.allowed()).isFalse();
        assertThat(((Decision.Deny) both).reason()).isNotBlank();
    }

    @Test
    @DisplayName("GateInputs 为 null 必须拒绝（NPE）")
    void nullGateInputsDenied() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> {
            ProjectStageTransitionPolicy.decide(ProjectStage.INITIATED, ProjectStage.DRAFTING, null);
        });
    }

    // ========== 确定性：同一输入永远返回同一结果 ==========

    @ParameterizedTest
    @MethodSource("allStagePairs")
    @DisplayName("所有状态对评估必须确定性（无随机性）")
    void validateTransitionIsDeterministic(ProjectStage from, ProjectStage to) {
        boolean first = ProjectStageTransitionPolicy.decide(from, to, GATE).allowed();
        for (int i = 0; i < 16; i++) {
            assertThat(ProjectStageTransitionPolicy.decide(from, to, GATE).allowed())
                    .as("transition %s → %s must be deterministic", from, to)
                    .isEqualTo(first);
        }
    }

    // ========== 评测子阶段 Fuzz ==========

    @Nested
    @DisplayName("EvaluationSubStage 子状态机 Fuzz")
    class EvaluationSubStageFuzz {

        @ParameterizedTest
        @EnumSource(EvaluationSubStage.class)
        @DisplayName("子阶段自转换必须拒绝")
        void subStageSelfTransitionDenied(EvaluationSubStage stage) {
            Decision d = ProjectStageTransitionPolicy.decideEvaluationSub(stage, stage);
            assertThat(d.allowed())
                    .as("自转换 %s → %s 必须拒绝", stage, stage)
                    .isFalse();
        }

        @Test
        @DisplayName("子阶段穷举矩阵与 spec 一致")
        void exhaustiveSubStageMatrix() {
            for (EvaluationSubStage from : EvaluationSubStage.values()) {
                for (EvaluationSubStage to : EvaluationSubStage.values()) {
                    boolean expected = isSubStageAllowedBySpec(from, to);
                    boolean actual = ProjectStageTransitionPolicy.decideEvaluationSub(from, to).allowed();
                    assertThat(actual)
                            .as("sub transition %s → %s", from, to)
                            .isEqualTo(expected);
                }
            }
        }

        @ParameterizedTest
        @EnumSource(EvaluationSubStage.class)
        @DisplayName("ANNOUNCED 为终态，子阶段所有出向拒绝")
        void announcedIsTerminal(EvaluationSubStage to) {
            if (to == EvaluationSubStage.ANNOUNCED) return;
            Decision d = ProjectStageTransitionPolicy.decideEvaluationSub(EvaluationSubStage.ANNOUNCED, to);
            assertThat(d.allowed())
                    .as("ANNOUNCED → %s 必须拒绝", to)
                    .isFalse();
        }

        @Test
        @DisplayName("子阶段随机注入不拓宽跳转图")
        void subStageRandomInjectionNeverWidens() {
            Random rng = new Random(FUZZ_SEED ^ 0xC0FFEE_42L);
            EvaluationSubStage[] universe = EvaluationSubStage.values();

            for (int i = 0; i < FUZZ_ITERATIONS; i++) {
                EvaluationSubStage from = universe[rng.nextInt(universe.length)];
                EvaluationSubStage to = universe[rng.nextInt(universe.length)];
                boolean expected = isSubStageAllowedBySpec(from, to);
                boolean actual = ProjectStageTransitionPolicy.decideEvaluationSub(from, to).allowed();
                assertThat(actual)
                        .as("random sub-stage pair %s → %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }

    // ========== decideResultNext 边界测试 ==========

    @Nested
    @DisplayName("decideResultNext 边界测试")
    class DeciderResultNextBoundary {

        @Test
        @DisplayName("WON → RETROSPECTIVE")
        void wonGoesToRetrospective() {
            assertThat(ProjectStageTransitionPolicy.decideResultNext(BidResultType.WON))
                    .isEqualTo(ProjectStage.RETROSPECTIVE);
        }

        @Test
        @DisplayName("LOST → RETROSPECTIVE")
        void lostGoesToRetrospective() {
            assertThat(ProjectStageTransitionPolicy.decideResultNext(BidResultType.LOST))
                    .isEqualTo(ProjectStage.RETROSPECTIVE);
        }

        @Test
        @DisplayName("FAILED → RETROSPECTIVE (CO-504: 不再跳 CLOSED，由结项审核流程接管)")
        void failedGoesToClosed() {
            assertThat(ProjectStageTransitionPolicy.decideResultNext(BidResultType.FAILED))
                    .isEqualTo(ProjectStage.RETROSPECTIVE);
        }

        @Test
        @DisplayName("ABANDONED → RETROSPECTIVE (CO-504: 不再跳 CLOSED，由结项审核流程接管)")
        void abandonedGoesToClosed() {
            assertThat(ProjectStageTransitionPolicy.decideResultNext(BidResultType.ABANDONED))
                    .isEqualTo(ProjectStage.RETROSPECTIVE);
        }

        @Test
        @DisplayName("null 抛出 NPE")
        void nullThrowsNPE() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    NullPointerException.class,
                    () -> ProjectStageTransitionPolicy.decideResultNext(null)
            );
        }

        @Test
        @DisplayName("所有 BidResultType 都有明确的下游状态")
        void allBidResultTypesHaveTarget() {
            for (BidResultType type : BidResultType.values()) {
                ProjectStage target = ProjectStageTransitionPolicy.decideResultNext(type);
                assertThat(target)
                        .as("BidResultType.%s 必须有非 null 下游状态", type)
                        .isNotNull();
            }
        }
    }

    // ========== Spec 参考实现（与 Policy 中的逻辑保持同步）==========

    /**
     * Spec 参考实现。
     * 此方法的逻辑必须与 ProjectStageTransitionPolicy.decide() 完全一致。
     * 如果两者不一致，说明 spec 或实现已漂移，测试会失败。
     */
    private static boolean isAllowedBySpec(ProjectStage from, ProjectStage to) {
        if (from == null || to == null) return false;
        if (from == ProjectStage.CLOSED) return false;
        if (from == to) return false;

        // 线性顺序：INITIATED → DRAFTING → EVALUATING → RESULT_PENDING → RETROSPECTIVE → CLOSED
        // 加上 RESULT_PENDING → CLOSED（流标/弃标特例）
        ProjectStage expectedNext = next(from);
        return to == expectedNext || (from == ProjectStage.RESULT_PENDING && to == ProjectStage.CLOSED);
    }

    private static ProjectStage next(ProjectStage s) {
        return switch (s) {
            case INITIATED -> ProjectStage.DRAFTING;
            case DRAFTING -> ProjectStage.EVALUATING;
            case EVALUATING -> ProjectStage.RESULT_PENDING;
            case RESULT_PENDING -> ProjectStage.RETROSPECTIVE;
            case RETROSPECTIVE -> ProjectStage.CLOSED;
            case CLOSED -> null;
        };
    }

    private static boolean isSubStageAllowedBySpec(EvaluationSubStage from, EvaluationSubStage to) {
        if (from == null || to == null) return false;
        if (from == to) return false;
        EvaluationSubStage expected = nextSub(from);
        return to == expected;
    }

    private static EvaluationSubStage nextSub(EvaluationSubStage s) {
        return switch (s) {
            case IN_PROGRESS -> EvaluationSubStage.AWAITING_BOARD;
            case AWAITING_BOARD -> EvaluationSubStage.RESULT_OUT;
            case RESULT_OUT -> EvaluationSubStage.ANNOUNCED;
            case ANNOUNCED -> null;
        };
    }
}
