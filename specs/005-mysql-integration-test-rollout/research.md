# Phase 0 Research: MySQL Integration Test Rollout

**Feature**: 005-mysql-integration-test-rollout
**Date**: 2026-06-30
**Status**: Complete（spec 无 NEEDS CLARIFICATION，本文件记录实现层面的关键技术决策）

## 研究目标

spec.md 已通过 16 项质量校验、无 [NEEDS CLARIFICATION] 标记。但 plan 阶段仍有 4 个实现层面的技术决策需要研究确认，避免实现时返工：

1. `RoleCodeCachePort` 在集成测试中如何 stub（避免真实 OSS API 调用）
2. `TenderCommandService` 15 个依赖在集成测试中如何构造（哪些 `@Autowired`、哪些 `@MockBean`）
3. 数据清理策略：`JdbcTemplate.DELETE` vs `TRUNCATE` vs `@Sql` 脚本
4. 测试数据库命名与隔离（避免与既有测试类串数据）

## 决策 1: RoleCodeCachePort 测试 stub 策略

**Decision**: 用 `@TestConfiguration` + `@Primary` 提供内存 Map 实现的 `RoleCodeCachePort` Bean，覆盖真实 `OssPermissionCache` 适配器。

**Rationale**:
- `RoleCodeCachePort` 是 security 包定义的端口接口（[RoleCodeCachePort.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/security/RoleCodeCachePort.java)），仅一个方法 `Optional<String> getRoleCode(String username)`
- 真实适配器 `crm.application.OssPermissionCache` 依赖 OSS Gateway API，集成测试不能调用外部服务（Constitution V 要求真实 API，但集成测试的"真实"指真实 MySQL + 真实 Flyway，不是真实 OSS Gateway）
- 用 `@Primary` 覆盖是 Spring 标准做法，范本 `NoOpPasswordEncryptionTestConfig` 已验证此模式（用 `@Primary` 替换 `PasswordEncryptionUtil`）
- 内存 Map 实现简单（< 30 行），测试方法通过 `cache.put(username, roleCode)` 注入预设数据

**Alternatives considered**:
- ❌ `@MockBean RoleCodeCachePort`：会创建 Spring 代理的 Mock，但 `@MockBean` 对接口的 stub 语法（`when().thenReturn()`）在测试方法间共享上下文时容易残留状态，不如内存 Map 直观
- ❌ 真实 `OssPermissionCache` + 启动 OSS Gateway mock server：过度工程化，违反 Constitution VI（Boring Proven Patterns），OSS API 调用应由 E2E 覆盖
- ❌ 在 `application-flyway-mysql.yml` 用 `spring.autoconfigure.exclude` 排除 `OssPermissionCache`：会影响其他集成测试，破坏隔离性

**实现位置**: `backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTestConfig.java`

```java
@TestConfiguration
public class EffectiveRoleResolverMysqlIntegrationTestConfig {
    @Bean(name = "roleCodeCachePort")
    @Primary
    public RoleCodeCachePort inMemoryRoleCodeCachePort() {
        return new InMemoryRoleCodeCachePort();
    }

    static class InMemoryRoleCodeCachePort implements RoleCodeCachePort {
        private final Map<String, String> cache = new HashMap<>();
        @Override
        public Optional<String> getRoleCode(String username) {
            return Optional.ofNullable(cache.get(username));
        }
        public void put(String username, String roleCode) { cache.put(username, roleCode); }
        public void clear() { cache.clear(); }
    }
}
```

## 决策 2: TenderCommandService 依赖构造策略

**Decision**: `TenderCommandService` 自身用 `@Autowired` 注入真实 Spring Bean（被测对象），其 15 个依赖按"测试关注点"分类处理。

**Rationale**: 15 个依赖不能全部 `@MockBean`（那样又退化为 Mock 测试），也不能全部真实注入（部分依赖如 `TenderCrmOccupancyChecker` 可能需要复杂外部状态）。按测试场景分层：

| 依赖 | 处理方式 | 理由 |
|---|---|---|
| `tenderRepository` | `@Autowired` 真实 | 测试核心：验证 DB 落库 |
| `attachmentRepository` | `@Autowired` 真实 | 测试 `deleteTender` 级联附件 |
| `assignmentRecordRepository` | `@Autowired` 真实 | 测试 `assignOnCrmLink` 跨表写入 |
| `userRepository` | `@Autowired` 真实 | 测试 `assignOnCrmLink` 查 User |
| `projectRepository` | `@Autowired` 真实 | 部分 RPC 路径需要 |
| `tenderMapper` | `@Autowired` 真实 | 测试 DTO↔Entity 真实转换 |
| `tenderDeduplicationService` | `@Autowired` 真实 | 测试 CO-265 重复检测 |
| `tenderAuditService` | `@Autowired` 真实 | 测试审计日志写入 |
| `accessGuard` / `commandAccessGuard` | `@Autowired` 真实 | 测试权限守卫真实行为 |
| `crmOccupancyChecker` | `@Autowired` 真实 | 测试 CO-297 DB UNIQUE 双层防御（核心） |
| `evaluationBackfillService` | `@MockBean` | CO-310 评估表回填非本测试关注点，避免触发复杂回填逻辑 |
| `projectManagerIdResolver` | `@MockBean` | 外部集成（ProjectManagerIdResolver 解析 RPC），返回 null 即可 |
| `autoAssignmentService` | `@MockBean` | `tryAutoAssign` 失败场景用 Mock 抛异常；成功场景用 Mock 返回 `AssignmentResult.matched(...)` |
| `eventPublisher` | `@Autowired` 真实 | Spring 默认同步事件发布器，验证事件发布 |
| `assignmentNotifier` | `@MockBean` | 通知发送是非测试关注点，避免触发真实通知 |

**Alternatives considered**:
- ❌ 全部 `@MockBean`：退化为 Mock 测试，无法验证 DB round-trip（违反本任务目标）
- ❌ 全部 `@Autowired` 真实：`autoAssignmentService` 真实启动会触发 RPC 调用，过度耦合；`evaluationBackfillService` 真实启动可能改 DB 评估表数据，干扰断言
- ✅ 选择性 `@MockBean`（4 个外部/通知依赖）：平衡真实性与隔离性，符合 `PlatformAccountBorrowServiceMysqlIntegrationTest` 范本

## 决策 3: 数据清理策略

**Decision**: 用 `@BeforeEach` + `JdbcTemplate.update("DELETE FROM ... WHERE ...")` 清理，**不**用 `@Transactional`、**不**用 `TRUNCATE`、**不**用 `@Sql`。

**Rationale**:
- 范本 `PlatformAccountBorrowServiceMysqlIntegrationTest` 第 80-86 行已验证此模式：先删子表（FK 依赖），再删父表，用 `WHERE` 限定测试数据范围（避免清全表）
- `@Transactional` 会让 Service 的 `@Transactional` 失效（测试事务包裹服务事务，永不真实提交），无法验证 DB round-trip —— 违反本任务核心目标
- `TRUNCATE` 会重置自增 ID，可能干扰其他测试类（虽然每个测试类独立 Spring 上下文，但共享同一 MySQL 实例）
- `@Sql` 脚本适合初始化固定数据，但本测试需要动态构造不同场景，`@BeforeEach` + builder 模式更灵活

**清理顺序约定**（按 FK 依赖反向）:
1. `tender_assignment_records`（子表，引用 tender + user）
2. `tender_attachments`（子表，引用 tender）
3. `tenders`（父表）
4. `users`（被 `assignOnCrmLink` 引用，测试构造的 User）

**清理范围限定**：用 `WHERE id IN (...)` 或 `WHERE username LIKE 'test-int-%'` 限定，避免误删其他测试数据。

## 决策 4: 测试数据库命名与隔离

**Decision**: 复用基类 `AbstractMysqlIntegrationTest` 的数据库（CI: `xiyu_bid_test`，本地: `xiyu_bid_verify`），**不**为每个测试类创建独立数据库。

**Rationale**:
- 基类 [AbstractMysqlIntegrationTest.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/support/AbstractMysqlIntegrationTest.java) 第 54-65 行已设计为共享数据库：CI 用 `xiyu_bid_test`，本地 fallback 到 `xiyu_bid_verify`
- Standalone 模式（如 `FlywayMysqlContainerTest` 用 `xiyu_bid_v1102_test`）适合一次性契约测试，业务服务集成测试应共享上下文以加速启动
- 数据隔离靠 `@BeforeEach` 清理 + 测试数据用 `test-int-` 前缀（与范本一致），不靠数据库物理隔离
- 多个测试类共享 Spring 上下文缓存（`@SpringBootTest` 默认缓存），减少重复启动开销

**Alternatives considered**:
- ❌ 每个测试类独立数据库：Flyway 全迁移链约 1100+ 脚本，每个测试类跑一遍迁移链耗时 30+ 秒，两个类就多 1 分钟，违反 SC-005（增量 < 60s）
- ❌ 用 H2 内存库：违反 Constitution II（Real-API Only），H2 与 MySQL SQL 方言差异会掩盖真实行为

## 总结

4 个技术决策均已确认，无需 [NEEDS CLARIFICATION]。所有决策都遵循 Constitution VI（Boring Proven Patterns），完全基于 `PlatformAccountBorrowServiceMysqlIntegrationTest` 已验证的范本，不引入新模式。可进入 Phase 1 设计。
