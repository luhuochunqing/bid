# Phase 0 Research: 防御性 Collection 与优雅降级治理

**Date**: 2026-07-03
**Status**: Complete

## Research Questions

### Q1: ArchUnit 如何扫描 `Collectors.toMap` 2 参数版本？

**Decision**: 使用 ArchUnit 的 `JavaMethodCall` API 扫描 `java.util.stream.Collectors.toMap` 方法调用，检查参数数量。2 参数版本（`toMap(Function, Function)`）命中即失败，3 参数版本（`toMap(Function, Function, BinaryOperator)`）和 4 参数版本（`toMap(Function, Function, BinaryOperator, Supplier)`）通过。

**Rationale**: ArchUnit 原生支持方法调用扫描，可以精确区分方法重载。相比正则扫描（可能误匹配字符串、注释），ArchUnit 基于 AST 解析更准确。

**Alternatives considered**:
- 正则扫描 `Collectors.toMap(` + 计数逗号——误报率高，会匹配注释和字符串
- PMD/Checkstyle 自定义规则——配置复杂，ArchUnit 已是项目标准工具

**Implementation pattern**:
```java
@ArchTest
static final ArchRule toMapMustHaveMergeFunction = JavaMethodCall.of(
    Collectors.class, "toMap")
    .should(haveParametersCountGreaterThan(2))
    .because("Collectors.toMap without merge function throws IllegalStateException on duplicate keys");
```

### Q2: pre-push gate 脚本如何实现？

**Decision**: 用 Node.js (`.mjs`) 实现，扫描 `backend/src/main/java/` 下所有 `.java` 文件的 `Collectors.toMap(` 调用，AST 纀别解析参数数量。2 参数版本（无 merge function）命中即拒绝。维护豁免清单文件（JSON），清单内文件:行号不报错。

**Rationale**: 与现有 `check-rolecode-direct-calls.mjs` 同技术栈，复用扫描模式。Node.js 启动快（<100ms），扫描全仓 Java 文件 <2s。豁免清单 JSON 格式便于维护。

**Alternatives considered**:
- Shell + grep——无法准确计数参数（跨行调用、嵌套 lambda）
- Java 程序——启动慢（JVM 冷启动 >2s），不适合 pre-push gate
- 复用 ArchUnit——ArchUnit 需要编译，pre-push gate 阶段可能未编译

**豁免清单格式**:
```json
{
  "exemptions": [
    {"file": "com/xiyu/bid/xxx/SomeClass.java", "line": 42, "reason": "key is PK with unique constraint"}
  ]
}
```

### Q3: 装饰性 enrichment 方法如何识别？

**Decision**: 按方法名模式 + 返回值用途识别：
- 方法名含 `enrich`、`fetchXxxNames`、`fetchXxxMap`、`buildXxxMap`
- 返回值用于补充显示字段（name resolution、display field），非业务决策
- 调用方在列表/详情查询流程中（非写入流程）

**Rationale**: 自动化识别太复杂（需要语义分析），按命名约定识别是实用折中。PR review 时人工确认。

**Alternatives considered**:
- 注解标记（`@DecorativeEnrichment`）——需要修改所有现有方法，过度工程化
- 全部 try-catch——性能影响且掩盖真正应该抛的异常

### Q4: 5xx handler 诊断标准如何验证？

**Decision**: ArchitectureTest 扫描 `@ExceptionHandler` 注解方法，验证方法体内包含 `log.error`（而非 `log.warn`）和 `Sentry.captureException` 调用。对于无法静态验证的 Payload 打印，通过代码 review 确认。

**Rationale**: ArchUnit 可以扫描方法调用，`log.error` vs `log.warn` 和 `Sentry.captureException` 都是明确的方法调用，可静态验证。Payload 打印涉及 `getRequestPayload` 调用，也可扫描。

**Alternatives considered**:
- 只靠 code review——不可靠，容易遗漏
- 运行时验证（AOP 检查）——过度工程化，性能影响

### Q5: 31 处 toMap 修复的优先级排序？

**Decision**: 按风险排序，分 4 批修复：
1. **Batch 1（P0，5 处）**：key 是外键/非主键字段，对应一对多关系，最易触发——ProjectQueryService (4处)、DocumentSectionTreeService (2处)、JpaWorkflowFormAdminStore (2处)
2. **Batch 2（P1，10 处）**：key 是主键但来自 join 查询/批量查询，可能因数据源变化触发——TaskBoardService、ExpenseLedgerApplicationService 等
3. **Batch 3（P2，13 处）**：key 是主键且来自单表查询，重复概率低但仍需修复
4. **Batch 4（P3，3 处）**：key 来自内存结构，形式上有隐患

**Rationale**: 优先修复最可能崩溃的点，降低生产风险。每批修复后跑全量测试，确保不引入回归。

**Alternatives considered**:
- 一次性全修——风险高，难以定位回归
- 只修 Batch 1——不彻底，债务遗留

## Summary

所有研究问题已解决，无 NEEDS CLARIFICATION 残留。技术方案确定：
- ArchUnit 守卫扫描 `JavaMethodCall`
- pre-push gate 用 Node.js `.mjs` + 豁免清单 JSON
- enrichment 识别按命名约定
- handler 诊断用 ArchUnit 扫描方法调用
- 31 处修复分 4 批，按风险排序
