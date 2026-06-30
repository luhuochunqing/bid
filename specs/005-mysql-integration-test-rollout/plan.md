# Implementation Plan: MySQL Integration Test Rollout for Role Resolution & Tender Commands

**Branch**: `agent/codex/mysql-integration-test-rollout` | **Date**: 2026-06-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-mysql-integration-test-rollout/spec.md`

## Summary

将项目已有的 MySQL 集成测试模式（`AbstractMysqlIntegrationTest` 基类 + Testcontainers + Flyway 全迁移链）从 1 个业务服务样例（`PlatformAccountBorrowServiceMysqlIntegrationTest`）推广到两个高风险核心服务：

1. **EffectiveRoleResolver**（CO-373 治理产物，服务层权限校验的唯一角色码读取入口）——补齐 OSS 用户 `role_id=NULL` 真实 DB 状态、缓存 miss 跨表查询等 Mock 测试无法覆盖的场景
2. **TenderCommandService**（标讯模块核心写服务，承载 CO-261/CO-265/CO-297/CO-333 等治理）——补齐 `linkCrmOpportunity` DB UNIQUE 双层防御、`assignOnCrmLink` 跨表事务一致性、`tryAutoAssign` 失败回滚等

技术路径：复用现有基类（CI Testcontainers / 本地手动容器 fallback），用 `@ActiveProfiles("flyway-mysql")` + `@Import(NoOpPasswordEncryptionTestConfig.class)` 启用真实 Flyway + MySQL 8.0，测试方法不加 `@Transactional`（让 Service 的 `@Transactional` 真实提交/回滚），用 `@BeforeEach` + `JdbcTemplate.update("DELETE ...")` 清理数据。仅新增测试类，不修改 main 代码。

## Technical Context

**Language/Version**: Java 21（`backend/pom.xml`）

**Primary Dependencies**:
- Spring Boot 3.2 + Spring Data JPA + Spring Test（`@SpringBootTest`、`@MockBean`、`JdbcTemplate`）
- JUnit 5 + Mockito（既有 unit test 共存）
- Testcontainers 1.19.3（`mysql`、`junit-jupiter` artifact，已就绪）
- Flyway（全迁移链 `db/migration-mysql/`，B73 baseline + V74~V1112+ 增量）
- Lombok（`@RequiredArgsConstructor`、`@Slf4j`、`builder()`）

**Storage**: MySQL 8.0（`mysql:8.0` 镜像，CI 用 Testcontainers，本地 fallback 到 `localhost:13306/xiyu_bid_verify`）

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers；当前无 maven-failsafe 分阶段，所有测试在 `mvn test` 同 phase 跑

**Target Platform**: 开发者本地（macOS + Docker Desktop / 手动 MySQL 容器）+ CI（GitHub Actions ubuntu-latest）

**Project Type**: 后端 Spring Boot 服务（仅 backend 模块改动，前端无影响）

**Performance Goals**: 新增集成测试增量运行时间 < 60s（不含 Spring 上下文首次启动，多个测试类共享上下文缓存）

**Constraints**:
- 不修改 main 代码（仅新增测试类 + 可能的测试支持配置）
- 不破坏既有 13 个 ArchitectureTest RULE + MaintainabilityArchitectureTest
- 不破坏既有 Mockito unit test（`EffectiveRoleResolverTest`、`TenderCommandServiceTest`、`TenderCommandServiceLinkCrmOpportunityDedupTest` 全部继续通过）
- `ddl-auto=validate`（profile 默认），让 schema 漂移由 `FlywayMysqlContainerTest` 抓，业务集成测试不负责 schema 审计

**Scale/Scope**: 新增 2 个测试类，预计 10-12 个测试方法（EffectiveRoleResolver 5 个 + TenderCommandService 5-7 个），约 400-500 行测试代码

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS | 仅新增测试代码，不改 main 包结构。EffectiveRoleResolver 已是 FP-Java 外壳，集成测试验证其与 RoleCodeCachePort 端口 + 真实 MySQL 的协作；TenderCommandService 已是 Application Service，集成测试验证事务边界 |
| II. Real-API Only | ✅ PASS | 集成测试用真实 MySQL + 真实 Flyway，不引入 Mock 模式。`RoleCodeCachePort` 用 `@TestConfiguration` 提供测试专用实现（内存 Map）替代真实 OSS API 调用——这是测试隔离，不是 Mock 模式回归（被测服务仍是真实 Spring Bean + 真实 DB） |
| III. Test-Driven Development | ✅ PASS | 本次推广本身是补 TDD 缺口：将纯 Mock unit test 升级为 MySQL 集成测试，符合"架构测试验证边界 + E2E 覆盖关键路径 + 集成测试覆盖 DB 行为"分层策略 |
| IV. Split-First & Simplicity | ✅ PASS | 新增测试类单一职责（按被测服务划分），不引入新的 main 类。测试类内部按 `@Nested` 分组（CO-XXX 场景），保持可读性 |
| V. OSS Integration | ✅ PASS | `RoleCodeCachePort` 测试 stub 不调用真实 OSS API，但验证了"OSS 用户（`external_org_source_app='oss'`）缓存未命中时 fail-closed"这一 OSS 集成关键行为。真实 OSS API 调用由 E2E 和手测覆盖 |
| VI. Boring Proven Patterns | ✅ PASS | 完全复用 `PlatformAccountBorrowServiceMysqlIntegrationTest` 已验证的范本（基类继承 + `@BeforeEach` 清理 + `flushAndClear`），不引入新模式 |
| Code Quality Gates | ✅ PASS | 仅新增测试代码，不触发 checkstyle/PMD/SpotBugs 主代码门禁；测试代码无 `@SuppressWarnings` |
| Performance Constraints | ✅ PASS | 不涉及分页/导出/上传 |
| Security & Access Control | ✅ PASS | 不改 `SecurityConfig`、`RoleProfileCatalog`、Controller `@PreAuthorize` |
| Multi-Agent SOP | ✅ PASS | 已跑 `sync-env.sh` 早操、`who-touches.sh` 检测无冲突、在 `agent/codex/mysql-integration-test-rollout` 任务分支上工作、完成后会 push WIP |

**Gate Result**: 全部 11 项原则 PASS，无 Constitution 违规，无需 Complexity Tracking 表。

## Project Structure

### Documentation (this feature)

```text
specs/005-mysql-integration-test-rollout/
├── plan.md              # This file
├── research.md          # Phase 0 output: 技术决策（RoleCodeCachePort stub、@MockBean 选择、数据清理策略）
├── data-model.md        # Phase 1 output: 被测实体与表结构
├── quickstart.md        # Phase 1 output: 本地运行集成测试的步骤
├── contracts/           # Phase 1 output: 被测方法的契约文档
│   ├── effective-role-resolver-contract.md
│   └── tender-command-service-contract.md
├── checklists/
│   └── requirements.md  # specify 阶段已生成
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/java/com/xiyu/bid/
│   │   ├── security/
│   │   │   ├── EffectiveRoleResolver.java          # 被测类 1（不改）
│   │   │   ├── RoleCodeCachePort.java              # 端口接口（不改）
│   │   │   └── domain/EffectiveRolePolicy.java     # 纯核心（不改）
│   │   └── tender/service/TenderCommandService.java # 被测类 2（不改）
│   └── test/
│       ├── java/com/xiyu/bid/
│       │   ├── security/
│       │   │   ├── EffectiveRoleResolverTest.java                          # 既有 Mock unit test（不改）
│       │   │   ├── EffectiveRoleResolverMysqlIntegrationTest.java          # 🆕 新增集成测试
│       │   │   └── EffectiveRoleResolverMysqlIntegrationTestConfig.java    # 🆕 测试专用 RoleCodeCachePort stub
│       │   ├── tender/service/
│       │   │   ├── TenderCommandServiceTest.java                           # 既有 Mock unit test（不改）
│       │   │   ├── TenderCommandServiceLinkCrmOpportunityDedupTest.java    # 既有 Mock unit test（不改）
│       │   │   └── TenderCommandServiceMysqlIntegrationTest.java           # 🆕 新增集成测试
│       │   └── support/
│       │       ├── AbstractMysqlIntegrationTest.java     # 既有基类（不改）
│       │       └── NoOpPasswordEncryptionTestConfig.java # 既有测试配置（不改）
│       └── resources/
│           ├── application-flyway-mysql.yml    # 既有（不改）
│           ├── application-test.yml            # 既有 H2 unit test 配置（不改）
│           └── testcontainers.properties       # 既有（不改）
└── pom.xml   # 既有依赖（不改）
```

**Structure Decision**:
- 测试类放在被测类同包下（`security/`、`tender/service/`），符合 `PlatformAccountBorrowServiceMysqlIntegrationTest` 范本约定
- `support/` 包仅放共享基础设施（基类、`NoOpPasswordEncryptionTestConfig`），不放业务测试
- `EffectiveRoleResolverMysqlIntegrationTestConfig` 放在 `security/` 包（与对应测试类同包，仅该测试类使用，不共享）
- 命名约定：`XxxServiceMysqlIntegrationTest` + `XxxServiceMysqlIntegrationTestConfig`（如有测试配置）

## Complexity Tracking

> 无 Constitution 违规，无需填写此表。
