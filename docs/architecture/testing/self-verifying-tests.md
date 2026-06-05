# 自验证测试基础设施

> 创建时间: 2026-03-04
> 位置: `src/test/java/com/xiyu/bid/testing/`

---

## 概述

自验证测试提供**跨层验证**能力，确保：
- **API响应层** ↔ **数据库层** ↔ **审计日志层** 三层一致

这解决了传统E2E测试的盲区：UI看起来正常，但数据不一致的隐藏bug。

---

## 组件

### 1. ShadowInspector (`ShadowInspector.java`)

跨层验证工具，提供链式API：

```java
shadowVerify("collaboration_threads", threadId)
    .exists()                    // 验证数据库记录存在
    .hasAuditLog()               // 验证审计日志存在
    .hasAuditAction("CREATE")    // 验证特定操作记录
    .timestampsValid(true)       // 验证时间戳一致性
    .stateTransition("OPEN", "CLOSED")  // 验证状态转换
    .softDeleted("is_deleted");  // 验证软删除
```

### 2. StateMachineValidator (`StateMachineValidator.java`)

状态机验证器，定义和验证状态转换：

```java
// 使用预定义状态机
StateMachineValidator fsm = StateMachineValidator.Predefined.collaborationThread();
fsm.verifyTransition("OPEN", "IN_PROGRESS");  // 验证转换合法性

// 自定义状态机
StateMachineValidator custom = StateMachineValidator.builder()
    .entity("my_entity")
    .states("A", "B", "C")
    .transition("A", "B")
    .transition("B", "C")
    .build();
```

### 3. SelfVerifyingTest (`SelfVerifyingTest.java`)

测试基类，继承即可获得自验证能力：

```java
@SpringBootTest
class MyTest extends SelfVerifyingTest {
    @Test
    void test() {
        // 执行操作
        Long id = service.create(...);

        // 自动跨层验证
        shadowVerify("my_table", id)
            .exists()
            .hasAuditLog();
    }
}
```

---

## 预定义状态机

| 状态机 | 状态 | 转换 |
|--------|------|------|
| `collaborationThread()` | OPEN → IN_PROGRESS → RESOLVED → CLOSED | 任意状态→CLOSED |
| `project()` | DRAFT → IN_PROGRESS → REVIEW → APPROVED/REJECTED → COMPLETED | →CANCELLED |
| `task()` | TODO → IN_PROGRESS → REVIEW → DONE | REVIEW→IN_PROGRESS (返工) |
| `fee()` | PENDING → PAID → RETURNED | →CANCELLED, RETURNED→PAID (重付) |
| `documentVersion()` | DRAFT → PUBLISHED → ARCHIVED | ARCHIVED→DRAFT (恢复) |

---

## 验证层

### Layer 1: 数据库验证
- 记录存在性
- 字段值正确性
- 时间戳一致性

### Layer 2: 审计日志验证
- 操作被记录
- 操作类型正确
- 实体关联正确

### Layer 3: 状态机验证
- 状态转换合法
- 最终状态正确
- 无循环依赖

---

## 使用示例

```java
@Test
@DisplayName("创建线程 - 三层一致性验证")
void createThread_shouldPersistAndAudit() {
    // When
    CollaborationThreadDTO result = service.createThread(request);

    // Then - 跨层验证
    shadowVerify("collaboration_threads", result.getId())
        .exists()
        .hasAuditLog()
        .hasAuditAction("CREATE")
        .timestampsValid(false);

    // 状态机验证
    stateMachine.verifyTransition("OPEN", result.getStatus().name());
}
```

---

## 测试结果

| 测试类 | 状态 | 覆盖 |
|--------|------|------|
| ShadowInspectorTest | ✅ 12/12 通过 | 状态机核心逻辑 |
| CollaborationSelfVerifyingTest | ⚠️ 需要数据库配置 | 集成测试 |

---

## 下一步

- [ ] 修复测试数据库配置（H2兼容性）
- [ ] 为其他模块添加状态机定义
- [ ] 集成到CI/CD流程
- [ ] 添加Gremlin随机游走测试

---

*文档版本: 1.0*
