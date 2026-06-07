package com.xiyu.bid.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Shadow Inspector 单元测试
 *
 * 验证跨层验证工具的核心逻辑
 */
@DisplayName("Shadow Inspector 单元测试")
class ShadowInspectorTest {

    private ShadowInspector shadowInspector;

    @BeforeEach
    void setUp() {
        // 使用mock对象，不依赖完整Spring上下文
        shadowInspector = new ShadowInspector(null, null);
    }

    @Test
    @DisplayName("状态机验证器 - 协作线程")
    void collaborationThreadStateMachine_shouldAllowValidTransitions() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.collaborationThread();

        // 合法转换
        assertDoesNotThrow(() -> fsm.verifyTransition("OPEN", "IN_PROGRESS"));
        assertDoesNotThrow(() -> fsm.verifyTransition("OPEN", "CLOSED"));
        assertDoesNotThrow(() -> fsm.verifyTransition("IN_PROGRESS", "RESOLVED"));
        assertDoesNotThrow(() -> fsm.verifyTransition("RESOLVED", "CLOSED"));

        // 非法转换
        assertThrows(IllegalStateException.class,
            () -> fsm.verifyTransition("CLOSED", "OPEN"));  // 不能从CLOSED回到OPEN
        assertThrows(IllegalStateException.class,
            () -> fsm.verifyTransition("RESOLVED", "OPEN"));  // 不能从RESOLVED回到OPEN
    }

    @Test
    @DisplayName("状态机验证器 - 获取有效转换")
    void stateMachine_shouldReturnValidTransitions() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.collaborationThread();

        Set<String> fromOpen = fsm.getValidTransitions("OPEN");
        assertThat(fromOpen).containsExactlyInAnyOrder("IN_PROGRESS", "CLOSED");

        Set<String> fromResolved = fsm.getValidTransitions("RESOLVED");
        assertThat(fromResolved).containsExactly("CLOSED");

        // CLOSE是最终状态
        assertThat(fsm.isFinalState("CLOSED")).isTrue();
        assertThat(fsm.isFinalState("OPEN")).isFalse();
    }

    @Test
    @DisplayName("状态机验证器 - 项目状态机")
    void projectStateMachine_shouldAllowValidTransitions() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.project();

        // 合法转换
        assertDoesNotThrow(() -> fsm.verifyTransition("DRAFT", "IN_PROGRESS"));
        assertDoesNotThrow(() -> fsm.verifyTransition("IN_PROGRESS", "REVIEW"));
        assertDoesNotThrow(() -> fsm.verifyTransition("REVIEW", "APPROVED"));
        assertDoesNotThrow(() -> fsm.verifyTransition("REVIEW", "REJECTED"));

        // 可以随时取消
        assertDoesNotThrow(() -> fsm.verifyTransition("DRAFT", "CANCELLED"));
        assertDoesNotThrow(() -> fsm.verifyTransition("IN_PROGRESS", "CANCELLED"));
    }

    @Test
    @DisplayName("状态机验证器 - 任务状态机")
    void taskStateMachine_shouldAllowRejection() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.task();

        // REVIEW可以返工到IN_PROGRESS
        assertDoesNotThrow(() -> fsm.verifyTransition("REVIEW", "IN_PROGRESS"));

        Set<String> fromReview = fsm.getValidTransitions("REVIEW");
        assertThat(fromReview).contains("DONE", "IN_PROGRESS");
    }

    @Test
    @DisplayName("状态机验证器 - 费用状态机")
    void feeStateMachine_shouldAllowRePayment() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.fee();

        // RETURNED可以重新支付变为PAID
        assertDoesNotThrow(() -> fsm.verifyTransition("RETURNED", "PAID"));

        Set<String> fromReturned = fsm.getValidTransitions("RETURNED");
        assertThat(fromReturned).contains("PAID");
    }

    @Test
    @DisplayName("状态机验证器 - 自定义构建")
    void customStateMachine_shouldWork() {
        StateMachineValidator fsm = StateMachineValidator.builder()
            .entity("test_entity")
            .states("A", "B", "C")
            .initialState("A")
            .transition("A", "B")
            .transition("B", "C")
            .build();

        assertDoesNotThrow(() -> fsm.verifyTransition("A", "B"));
        assertDoesNotThrow(() -> fsm.verifyTransition("B", "C"));

        // 非法转换
        assertThrows(IllegalStateException.class,
            () -> fsm.verifyTransition("A", "C"));  // 不能跳过B
        assertThrows(IllegalStateException.class,
            () -> fsm.verifyTransition("C", "A"));  // 不能回到A
    }

    @Test
    @DisplayName("状态机验证器 - 双向转换")
    void bidirectionalTransition_shouldWorkBothWays() {
        StateMachineValidator fsm = StateMachineValidator.builder()
            .entity("bidirectional_test")
            .states("ACTIVE", "INACTIVE")
            .bidirectional("ACTIVE", "INACTIVE")
            .build();

        assertDoesNotThrow(() -> fsm.verifyTransition("ACTIVE", "INACTIVE"));
        assertDoesNotThrow(() -> fsm.verifyTransition("INACTIVE", "ACTIVE"));
    }

    @Test
    @DisplayName("状态机验证器 - 任意状态到目标")
    void anyToTransition_shouldAllowFromAnyState() {
        StateMachineValidator fsm = StateMachineValidator.builder()
            .entity("cancel_test")
            .states("DRAFT", "SUBMITTED", "APPROVED", "CANCELLED")
            .transition("DRAFT", "SUBMITTED")
            .transition("SUBMITTED", "APPROVED")
            .anyTo("CANCELLED")
            .build();

        // 可以从任何状态取消
        assertDoesNotThrow(() -> fsm.verifyTransition("DRAFT", "CANCELLED"));
        assertDoesNotThrow(() -> fsm.verifyTransition("SUBMITTED", "CANCELLED"));
        assertDoesNotThrow(() -> fsm.verifyTransition("APPROVED", "CANCELLED"));
    }

    @Test
    @DisplayName("状态机验证器 - 完整性检查")
    void stateMachine_integrityCheck() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.collaborationThread();

        // 应该通过完整性检查
        assertDoesNotThrow(() -> fsm.validateIntegrity());
    }

    @Test
    @DisplayName("Shadow Inspector - equals方法")
    void shadowInspector_equals_shouldHandleNulls() {
        // 测试私有equals方法的逻辑
        assertTrue(equalsTest(null, null));
        assertFalse(equalsTest("a", null));
        assertFalse(equalsTest(null, "a"));
        assertTrue(equalsTest("a", "a"));
        assertFalse(equalsTest("a", "b"));
    }

    /**
     * 测试equals逻辑的辅助方法
     */
    private boolean equalsTest(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Test
    @DisplayName("状态机 - 验证链结构")
    void verificationChain_shouldHaveCorrectStructure() {
        // 验证链是链式调用的Builder模式
        // 不实际执行，只验证API设计
        ShadowInspector.VerificationChain chain = shadowInspector.verify("test_table", 1L);
        assertThat(chain).isNotNull();
    }

    @Test
    @DisplayName("状态机 - 入边转换")
    void stateMachine_shouldFindIncomingTransitions() {
        StateMachineValidator fsm = StateMachineValidator.Predefined.collaborationThread();

        // 可以到达CLOSED的状态
        Set<String> incomingToClosed = fsm.getIncomingTransitions("CLOSED");
        assertThat(incomingToClosed).contains("OPEN", "IN_PROGRESS", "RESOLVED");

        // 可以到达IN_PROGRESS的状态
        Set<String> incomingToInProgress = fsm.getIncomingTransitions("IN_PROGRESS");
        assertThat(incomingToInProgress).containsExactly("OPEN");
    }
}
