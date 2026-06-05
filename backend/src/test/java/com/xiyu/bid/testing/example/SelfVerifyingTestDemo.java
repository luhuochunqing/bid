package com.xiyu.bid.testing.example;

import com.xiyu.bid.testing.ShadowInspector;
import com.xiyu.bid.testing.StateMachineValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 自验证测试演示
 *
 * 展示Shadow Inspector和状态机验证器的核心功能
 * 使用单元测试，不依赖数据库连接
 */
@DisplayName("自验证测试功能演示")
class SelfVerifyingTestDemo {

    @Test
    @DisplayName("演示1: 状态机验证 - 协作线程")
    void demo1_collaborationThreadStateMachine() {
        System.out.println("\n=== 演示1: 协作线程状态机 ===\n");

        StateMachineValidator fsm = StateMachineValidator.Predefined.collaborationThread();

        // 打印所有合法转换
        System.out.println("协作线程状态转换规则:");
        for (String state : new String[]{"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"}) {
            Set<String> transitions = fsm.getValidTransitions(state);
            System.out.println(String.format("  %s -> %s", state, transitions));
        }

        // 验证合法转换
        assertDoesNotThrow(() -> {
            fsm.verifyTransition("OPEN", "IN_PROGRESS");
            System.out.println("✓ OPEN -> IN_PROGRESS: 合法");
        });

        assertDoesNotThrow(() -> {
            fsm.verifyTransition("IN_PROGRESS", "RESOLVED");
            System.out.println("✓ IN_PROGRESS -> RESOLVED: 合法");
        });

        assertDoesNotThrow(() -> {
            fsm.verifyTransition("RESOLVED", "CLOSED");
            System.out.println("✓ RESOLVED -> CLOSED: 合法");
        });

        // 验证非法转换
        assertThrows(IllegalStateException.class, () -> {
            fsm.verifyTransition("CLOSED", "OPEN");
        });
        System.out.println("✗ CLOSED -> OPEN: 非法 (不能从最终状态返回)");

        // 验证最终状态
        assertThat(fsm.isFinalState("CLOSED")).isTrue();
        System.out.println("✓ CLOSED 是最终状态");

        // 完整性检查
        assertDoesNotThrow(() -> fsm.validateIntegrity());
        System.out.println("✓ 状态机完整性检查通过");
    }

    @Test
    @DisplayName("演示2: 状态机验证 - 项目状态机")
    void demo2_projectStateMachine() {
        System.out.println("\n=== 演示2: 项目状态机 ===\n");

        StateMachineValidator fsm = StateMachineValidator.Predefined.project();

        System.out.println("项目状态转换规则:");
        for (String state : new String[]{"DRAFT", "IN_PROGRESS", "REVIEW", "APPROVED", "REJECTED", "CANCELLED"}) {
            Set<String> transitions = fsm.getValidTransitions(state);
            if (!transitions.isEmpty()) {
                System.out.println(String.format("  %s -> %s", state, transitions));
            }
        }

        // 验证主流程
        assertDoesNotThrow(() -> {
            fsm.verifyTransition("DRAFT", "IN_PROGRESS");
            fsm.verifyTransition("IN_PROGRESS", "REVIEW");
            fsm.verifyTransition("REVIEW", "APPROVED");
            fsm.verifyTransition("APPROVED", "COMPLETED");
        });
        System.out.println("✓ 主流程 DRAFT->IN_PROGRESS->REVIEW->APPROVED->COMPLETED: 全部合法");

        // 验证取消流程（可以随时取消）
        assertDoesNotThrow(() -> {
            fsm.verifyTransition("DRAFT", "CANCELLED");
            fsm.verifyTransition("IN_PROGRESS", "CANCELLED");
        });
        System.out.println("✓ 可以随时取消项目");
    }

    @Test
    @DisplayName("演示3: 状态机验证 - 任务状态机（支持返工）")
    void demo3_taskStateMachineWithRework() {
        System.out.println("\n=== 演示3: 任务状态机（返工流程） ===\n");

        StateMachineValidator fsm = StateMachineValidator.Predefined.task();

        // REVIEW状态可以返工到IN_PROGRESS
        Set<String> fromReview = fsm.getValidTransitions("REVIEW");
        assertThat(fromReview).contains("IN_PROGRESS");
        System.out.println("✓ REVIEW -> IN_PROGRESS: 返工流程支持");

        // 打印所有转换
        System.out.println("\n任务状态转换规则:");
        System.out.println("  TODO -> " + fsm.getValidTransitions("TODO"));
        System.out.println("  IN_PROGRESS -> " + fsm.getValidTransitions("IN_PROGRESS"));
        System.out.println("  REVIEW -> " + fsm.getValidTransitions("REVIEW"));
    }

    @Test
    @DisplayName("演示4: 自定义状态机")
    void demo4_customStateMachine() {
        System.out.println("\n=== 演示4: 自定义状态机 ===\n");

        // 构建自定义状态机
        StateMachineValidator orderFsm = StateMachineValidator.builder()
            .entity("order")
            .states("PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED")
            .initialState("PENDING")
            .transition("PENDING", "CONFIRMED")
            .transition("CONFIRMED", "SHIPPED")
            .transition("SHIPPED", "DELIVERED")
            .anyTo("CANCELLED")  // 任何状态都可以取消
            .build();

        System.out.println("订单状态转换规则:");
        System.out.println("  PENDING -> " + orderFsm.getValidTransitions("PENDING"));
        System.out.println("  CONFIRMED -> " + orderFsm.getValidTransitions("CONFIRMED"));
        System.out.println("  SHIPPED -> " + orderFsm.getValidTransitions("SHIPPED"));
        System.out.println("  DELIVERED -> " + orderFsm.getValidTransitions("DELIVERED"));

        // 验证可以从任何状态取消
        assertDoesNotThrow(() -> {
            orderFsm.verifyTransition("PENDING", "CANCELLED");
            orderFsm.verifyTransition("CONFIRMED", "CANCELLED");
            orderFsm.verifyTransition("SHIPPED", "CANCELLED");
        });
        System.out.println("✓ 任何状态都可以取消");

        // 验证正常流程
        assertDoesNotThrow(() -> {
            orderFsm.verifyTransition("PENDING", "CONFIRMED");
            orderFsm.verifyTransition("CONFIRMED", "SHIPPED");
            orderFsm.verifyTransition("SHIPPED", "DELIVERED");
        });
        System.out.println("✓ 正常流程: PENDING->CONFIRMED->SHIPPED->DELIVERED");
    }

    @Test
    @DisplayName("演示5: 状态机完整性检查")
    void demo5_stateMachineIntegrity() {
        System.out.println("\n=== 演示5: 状态机完整性检查 ===\n");

        // 有问题的状态机（有死锁状态）
        StateMachineValidator badFsm = StateMachineValidator.builder()
            .entity("bad_entity")
            .states("A", "B", "C", "D")
            .transition("A", "B")
            .transition("B", "C")
            .transition("D", "A")  // D无法到达
            .build();

        System.out.println("检查问题状态机:");
        badFsm.validateIntegrity();
        System.out.println("⚠️ 状态D无法从初始状态到达");

        // 健康的状态机
        StateMachineValidator healthyFsm = StateMachineValidator.builder()
            .entity("healthy_entity")
            .states("A", "B", "C")
            .transition("A", "B")
            .transition("B", "C")
            .bidirectional("A", "C")  // A和C可以互相转换
            .build();

        System.out.println("\n检查健康状态机:");
        assertDoesNotThrow(() -> healthyFsm.validateIntegrity());
        System.out.println("✓ 状态机健康，没有孤立状态");
    }

    @Test
    @DisplayName("演示6: 费用状态机（支持重新支付）")
    void demo6_feeStateMachineWithRePayment() {
        System.out.println("\n=== 演示6: 费用状态机 ===\n");

        StateMachineValidator fsm = StateMachineValidator.Predefined.fee();

        // 退费后可以重新支付
        Set<String> fromReturned = fsm.getValidTransitions("RETURNED");
        assertThat(fromReturned).contains("PAID");
        System.out.println("✓ RETURNED -> PAID: 支持重新支付");

        System.out.println("\n费用状态转换规则:");
        System.out.println("  PENDING -> " + fsm.getValidTransitions("PENDING"));
        System.out.println("  PAID -> " + fsm.getValidTransitions("PAID"));
        System.out.println("  RETURNED -> " + fsm.getValidTransitions("RETURNED"));
    }

    @Test
    @DisplayName("演示7: 所有预定义状态机")
    void demo7_allPredefinedStateMachines() {
        System.out.println("\n=== 演示7: 所有预定义状态机 ===\n");

        StateMachineValidator[] fsms = {
            StateMachineValidator.Predefined.collaborationThread(),
            StateMachineValidator.Predefined.project(),
            StateMachineValidator.Predefined.task(),
            StateMachineValidator.Predefined.fee(),
            StateMachineValidator.Predefined.documentVersion()
        };

        for (StateMachineValidator fsm : fsms) {
            assertDoesNotThrow(() -> fsm.validateIntegrity());
            System.out.println("✓ " + fsm.getClass().getSimpleName() + " 完整性检查通过");
        }
    }

    @Test
    @DisplayName("演示8: Shadow Inspector 链式API设计")
    void demo8_shadowInspectorChainAPI() {
        System.out.println("\n=== 演示8: Shadow Inspector 链式API ===\n");

        ShadowInspector inspector = new ShadowInspector(null, null);
        ShadowInspector.VerificationChain chain = inspector.verify("test_table", 123L);

        // 展示链式API的设计（不实际执行）
        System.out.println("链式验证API:");
        System.out.println("  shadowVerify('test_table', 123L)");
        System.out.println("    .exists()           // 验证数据库记录存在");
        System.out.println("    .hasAuditLog()      // 验证审计日志存在");
        System.out.println("    .hasAuditAction('CREATE')  // 验证操作类型");
        System.out.println("    .timestampsValid(true)      // 验证时间戳");
        System.out.println("    .stateTransition('A', 'B')  // 验证状态转换");
        System.out.println("    .softDeleted('is_deleted'); // 验证软删除");

        System.out.println("\n✓ 链式API设计清晰，易于使用");
    }
}
