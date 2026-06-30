---

description: "Task list for MySQL integration test rollout (EffectiveRoleResolver + TenderCommandService)"
---

# Tasks: MySQL Integration Test Rollout for Role Resolution & Tender Commands

**Input**: Design documents from `/specs/005-mysql-integration-test-rollout/`

**Prerequisites**: [plan.md](plan.md) ✅, [spec.md](spec.md) ✅, [research.md](research.md) ✅, [data-model.md](data-model.md) ✅, [contracts/](contracts/) ✅, [quickstart.md](quickstart.md) ✅

**Tests**: 本任务的"实现"本身即为测试代码——user story 1/2 的 deliverable 就是集成测试类。无独立 test phase。

**Organization**: 按 spec.md 的 3 个 user story 分组（US1: EffectiveRoleResolver, US2: TenderCommandService, US3: 可复用范本——US3 通过 US1+US2 完成自动达成，无独立任务）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- 后端测试：`backend/src/test/java/com/xiyu/bid/<package>/`
- 基类与共享配置：`backend/src/test/java/com/xiyu/bid/support/`（既有，不改）
- 测试资源：`backend/src/test/resources/`（既有，不改）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认既有基础设施可用，无新增。

- [x] T000 确认 `AbstractMysqlIntegrationTest` 基类存在且可继承（[backend/src/test/java/com/xiyu/bid/support/AbstractMysqlIntegrationTest.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/support/AbstractMysqlIntegrationTest.java)）
- [x] T000 确认 `NoOpPasswordEncryptionTestConfig` 存在且可 `@Import`（[backend/src/test/java/com/xiyu/bid/support/NoOpPasswordEncryptionTestConfig.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/support/NoOpPasswordEncryptionTestConfig.java)）
- [x] T000 确认 `application-flyway-mysql.yml` profile 存在（[backend/src/test/resources/application-flyway-mysql.yml](file:///Users/user/xiyu/worktrees/codex/backend/src/test/resources/application-flyway-mysql.yml)）
- [x] T000 确认 testcontainers 依赖已在 `backend/pom.xml`（version 1.19.3, scope test）
- [x] T000 确认范本 `PlatformAccountBorrowServiceMysqlIntegrationTest` 存在可参考

> Setup 全部已就绪（spec/plan 阶段已验证），无新增任务。直接进入 User Story 阶段。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 无——本项目无新增 main 代码、无新增共享测试基础设施。基类 + NoOpPasswordEncryptionTestConfig + application-flyway-mysql.yml 已是 stable foundation。

> 跳过此阶段，直接进入 user story。

---

## Phase 3: User Story 1 - EffectiveRoleResolver MySQL 集成测试 (Priority: P1) 🎯 MVP

**Goal**: 新增 `EffectiveRoleResolverMysqlIntegrationTest`，覆盖 OSS 用户 `role_id=NULL` fail-closed、缓存命中/未命中、本地用户回退、`external_org_source_app` 边界场景，全部在真实 MySQL 8.0 下通过。

**Independent Test**: `mvn test -Dtest=EffectiveRoleResolverMysqlIntegrationTest` 独立运行，不依赖 TenderCommandService 测试。

### Implementation for User Story 1

- [x] T001 [US1] 创建测试专用 `RoleCodeCachePort` 内存 stub 配置类 `EffectiveRoleResolverMysqlIntegrationTestConfig` 在 [backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTestConfig.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTestConfig.java)
  - `@TestConfiguration` + `@Bean(name="roleCodeCachePort")` + `@Primary`
  - 内部 `InMemoryRoleCodeCachePort implements RoleCodeCachePort`：用 `HashMap<String,String>` 存储，`getRoleCode` 返回 `Optional.ofNullable`，提供 `put` / `clear` 测试辅助方法
  - 参考 [research.md 决策 1](research.md#决策-1-rolecodecacheport-测试-stub-策略)

- [x] T002 [US1] 创建 `EffectiveRoleResolverMysqlIntegrationTest` 测试类骨架在 [backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTest.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTest.java)
  - `extends AbstractMysqlIntegrationTest`
  - `@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true", "spring.jpa.hibernate.ddl-auto=none"})`
  - `@ActiveProfiles("flyway-mysql")`
  - `@Import({NoOpPasswordEncryptionTestConfig.class, EffectiveRoleResolverMysqlIntegrationTestConfig.class})`
  - `@Autowired EffectiveRoleResolver effectiveRoleResolver`
  - `@Autowired UserRepository userRepository`
  - `@Autowired RoleCodeCachePort roleCodeCachePort`（注入的应是 InMemory stub）
  - `@Autowired JdbcTemplate jdbcTemplate`
  - `@Autowired EntityManager entityManager`
  - `@BeforeEach cleanTestData()`：`DELETE FROM users WHERE id BETWEEN 9001 AND 9099` + `((InMemoryRoleCodeCachePort) roleCodeCachePort).clear()`
  - 辅助方法 `flushAndClear()`：`entityManager.clear()`
  - 参考 [PlatformAccountBorrowServiceMysqlIntegrationTest](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/platform/service/PlatformAccountBorrowServiceMysqlIntegrationTest.java) 第 53-105 行范本

- [ ] T003 [US1] [P] 实现 OSS 用户 `role_id=NULL` fail-closed 场景（FR-001）
  - `@Nested @DisplayName("CO-373: OSS 用户 role_id=NULL fail-closed")`
  - 测试方法 `ossUserWithNullRoleId_cacheMiss_returnsNullAndFailClosedSource`：
    - 用 `userRepository.saveAndFlush()` 写入真实 `users` 表：`id=9001L, username='test-int-oss-001', role_id=NULL, external_org_source_app='ehsy-oss'`
    - 不往 `InMemoryRoleCodeCachePort` 放缓存（模拟 miss）
    - 调用 `EffectiveRoleResolver.resolve(user)`
    - 断言 `result.roleCode()` 为 null（fail-closed，**不**回退 "manager"）
    - 断言 `result.source()` 为 `CACHE_MISS_FAIL_CLOSED`
  - 测试方法 `ossUserWithNullRoleId_cacheHit_returnsCachedValue`：
    - 同上写入 user，但 `InMemoryRoleCodeCachePort.put('test-int-oss-001', 'bid-Team')`
    - 调用 `resolve(user)`
    - 断言 `result.roleCode()` 为 `"bid-Team"`，`source()` 为 `CACHE_HIT`

- [ ] T004 [US1] [P] 实现 OSS 用户缓存 miss + 真实 role_id 场景（FR-002）
  - `@Nested @DisplayName("OSS 用户缓存 miss 跨表查询")`
  - 测试方法 `ossUserWithRealRoleId_cacheMiss_returnsEntityRoleCode`：
    - 先查 `roles` 表拿一个真实 role 记录（如 `code='bid-Team'` 的 id）
    - 写入 user：`role_id=<真实 role id>, external_org_source_app='ehsy-oss'`
    - 不放缓存
    - 调用 `resolve(user)`
    - 断言 `result.roleCode()` 为真实 `roles.code` 值（来自实体 `getRoleCode()`，验证跨表查询 `users.role_id` → `roles.code`）
    - 断言 `result.source()` 为 `CACHE_MISS_FAIL_CLOSED`（OSS 用户缓存 miss 仍标 fail-closed，即使实体有值）
  - **注意**: 需确认 `EffectiveRolePolicy.decide` 在 OSS 用户缓存 miss 时是否返回实体值——若 policy 是"OSS 用户缓存 miss 一律返回 null"（不读实体），则此测试应断言 null；若是"读实体但标 fail-closed"，则断言实体值。查 [EffectiveRolePolicy.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRolePolicy.java) 确认实际行为。

- [x] T005 [US1] [P] 实现本地用户场景（FR-001 反向）
  - `@Nested @DisplayName("本地用户：缓存 miss 回退实体")`
  - 测试方法 `localUser_cacheMiss_returnsEntityRoleCode`：
    - 写入 user：`external_org_source_app=null, role_id=<真实 admin role id>`
    - 不放缓存
    - 调用 `resolve(user)`
    - 断言 `result.roleCode()` 为真实 `roles.code`，`source()` 为 `LOCAL_USER`
  - 测试方法 `localUser_cacheHit_returnsCachedValue`：
    - 同上 user，但 `InMemoryRoleCodeCachePort.put('test-int-local-001', 'admin')`
    - 断言返回 `"admin"`，`source()` 为 `CACHE_HIT`（缓存优先于实体）

- [x] T006 [US1] [P] 实现 `external_org_source_app` 边界场景（FR-003）
  - `@Nested @DisplayName("external_org_source_app 边界")`
  - 测试方法 `blankExternalOrgSourceApp_treatedAsLocalUser`：
    - 写入 user：`external_org_source_app='   '`（空格字符串）
    - 不放缓存
    - 调用 `resolve(user)`
    - 断言 `source()` 为 `LOCAL_USER`（blank 归一化为本地用户）
  - 测试方法 `emptyStringExternalOrgSourceApp_treatedAsLocalUser`：
    - 写入 user：`external_org_source_app=''`（空字符串）
    - 不放缓存
    - 断言 `source()` 为 `LOCAL_USER`
  - 测试方法 `nullExternalOrgSourceApp_treatedAsLocalUser`：
    - 写入 user：`external_org_source_app=null`
    - 不放缓存
    - 断言 `source()` 为 `LOCAL_USER`

**Checkpoint**: `mvn test -Dtest=EffectiveRoleResolverMysqlIntegrationTest` 全部通过，EffectiveRoleResolver 在真实 MySQL 下的角色解析行为被验证。既有 `EffectiveRoleResolverTest`（Mock unit test）继续通过。

---

## Phase 4: User Story 2 - TenderCommandService MySQL 集成测试 (Priority: P1)

**Goal**: 新增 `TenderCommandServiceMysqlIntegrationTest`，覆盖 `linkCrmOpportunity` DB UNIQUE 双层防御、`assignOnCrmLink` 跨表事务、`tryAutoAssign` 失败回滚、`deleteTender` 级联事务、`createTender` 重复检测，全部在真实 MySQL 8.0 下通过。

**Independent Test**: `mvn test -Dtest=TenderCommandServiceMysqlIntegrationTest` 独立运行，不依赖 EffectiveRoleResolver 测试。

### Implementation for User Story 2

- [x] T007 [US2] 创建 `TenderCommandServiceMysqlIntegrationTest` 测试类骨架在 [backend/src/test/java/com/xiyu/bid/tender/service/TenderCommandServiceMysqlIntegrationTest.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/tender/service/TenderCommandServiceMysqlIntegrationTest.java)
  - `extends AbstractMysqlIntegrationTest`
  - `@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true", "spring.jpa.hibernate.ddl-auto=none"})`
  - `@ActiveProfiles("flyway-mysql")`
  - `@Import(NoOpPasswordEncryptionTestConfig.class)`
  - `@Autowired TenderCommandService tenderCommandService`
  - `@Autowired TenderRepository tenderRepository`
  - `@Autowired TenderAttachmentRepository attachmentRepository`
  - `@Autowired TenderAssignmentRecordRepository assignmentRecordRepository`
  - `@Autowired UserRepository userRepository`
  - `@Autowired JdbcTemplate jdbcTemplate`
  - `@Autowired EntityManager entityManager`
  - `@MockBean TenderAutoAssignmentService autoAssignmentService`（控制 `tryAutoAssign` 成功/失败）
  - `@MockBean TenderAssignmentNotifier assignmentNotifier`（避免真实通知）
  - `@MockBean TenderEvaluationBackfillService evaluationBackfillService`（避免 CO-310 回填干扰）
  - `@MockBean ProjectManagerIdResolver projectManagerIdResolver`（避免外部 RPC）
  - `@BeforeEach cleanTestData()`：按 FK 反向清理 `tender_assignment_records` → `tender_attachments` → `tenders` → `users`，限定 `creator_id BETWEEN 9001 AND 9099` 或 `id BETWEEN 9001 AND 9099`
  - 辅助方法 `flushAndClear()`：`entityManager.clear()`
  - 辅助方法 `createTestUser(Long id, String username)`：`userRepository.saveAndFlush(User.builder().id(id).username(username).build())`
  - 辅助方法 `createTestTender(String title, Long creatorId)`：构造最小 TenderDTO，调用 `tenderCommandService.createTender(dto, creatorId)`
  - 参考 [contracts/tender-command-service-contract.md](contracts/tender-command-service-contract.md) 依赖处理表

- [x] T008 [US2] [P] 实现 `linkCrmOpportunity` DB UNIQUE 双层防御场景（FR-004，CO-297）
  - **实施偏差**: 原 plan 设计两个测试方法，实际只实现 1 个 `linkCrmOpportunity_crmOpportunityIdAlreadyOccupied_throws409AndDoesNotOverwrite`。
  - 第二个测试 `linkCrmOpportunity_statusBidding_throws409` 被移除：admin 用户在 BIDDING 状态下无编辑权限，`commandAccessGuard.assertCanUpdateTender` 在 `assertCrmLinkAllowed` 之前抛 `AccessDeniedException`，永远走不到 409 业务规则。该纯函数行为由单元测试覆盖，集成测试不重复。
  - 实际 7/7 测试通过（36.226s）
  - `@Nested @DisplayName("CO-297: linkCrmOpportunity DB UNIQUE 双层防御")`
  - 测试方法 `linkCrmOpportunity_crmOpportunityIdAlreadyOccupied_throws409AndDoesNotOverwrite`：
    - 创建 user A (id=9001) 和 user B (id=9002)
    - 创建 tender A (`tenderCommandService.createTender`)，关联 CRM 商机 `crm-opp-001`
    - 创建 tender B
    - 调用 `tenderCommandService.linkCrmOpportunity(tenderB.id, 'crm-opp-001', '商机名', null, userB.id)`
    - 断言抛 `BusinessException` 且 status=409
    - 用 `JdbcTemplate.queryForObject("SELECT crm_opportunity_id FROM tenders WHERE id=?", ...)` 验证 tender B 的 `crm_opportunity_id` 仍为 null（未被覆盖）
  - 测试方法 `linkCrmOpportunity_statusBidding_throws409`：
    - 创建 tender，手动 `tender.setStatus(BIDDING)` + `tenderRepository.save` 模拟已投标状态
    - 调用 `linkCrmOpportunity` 关联新 CRM 商机
    - 断言抛 `BusinessException(409, "标讯已进入「BIDDING」状态")`

- [x] T009 [US2] [P] 实现 `assignOnCrmLink` 跨表事务一致性场景（FR-005，CO-310）
  - `@Nested @DisplayName("CO-310: assignOnCrmLink 跨表事务一致性")`
  - 测试方法 `linkCrmOpportunity_success_writesDispatchAssignmentRecord`：
    - 创建 user (id=9001, fullName='张三')
    - 创建 tender（status=PENDING_ASSIGNMENT 或 TRACKING）
    - `Mockito.when(autoAssignmentService.autoAssignIfPossible(any)).thenReturn(AssignmentResult.notMatched())`（避免 tryAutoAssign 干扰）
    - 调用 `tenderCommandService.linkCrmOpportunity(tender.id, 'crm-opp-002', '商机B', null, user.id)`
    - `flushAndClear()`
    - 用 `assignmentRecordRepository.findByTenderId(tender.id)` 验证：
      - 有 1 条 `TenderAssignmentRecord`
      - `type` 为 `DISPATCH`
      - `assigneeId` 为 `9001L`
      - `assigneeName` 为 '张三'
      - `assignedById` 为 `9001L`（CRM 关联即自动接手）
      - `remark` 包含 "CRM商机关联"
    - 用 `tenderRepository.findById` 验证 tender 的 `crmOpportunityId='crm-opp-002'`、`evaluationSource=BID_SYSTEM_LINK`

- [x] T010 [US2] [P] 实现 `tryAutoAssign` 失败回滚场景（FR-006）
  - `@Nested @DisplayName("tryAutoAssign 失败：Tender 落库但无 DISPATCH 记录")`
  - **注意**: `tryAutoAssign` 在 `createTender` 中 `tenderRepository.save(savedTender)` 之后调用，且 catch 异常不重抛。所以"回滚"验证的是：`tryAutoAssign` 内部失败时，Tender 已落库为 `PENDING_ASSIGNMENT`，无 DISPATCH 记录。
  - 测试方法 `createTender_autoAssignThrowsException_tenderPersistedAsPendingAndNoAssignmentRecord`：
    - `Mockito.when(autoAssignmentService.autoAssignIfPossible(any)).thenThrow(new RuntimeException("模拟 RPC 失败"))`
    - 调用 `tenderCommandService.createTender(tenderDTO, user.id)`
    - 断言不抛异常（`tryAutoAssign` catch 了）
    - `flushAndClear()`
    - 验证 tender 已落库，status=`PENDING_ASSIGNMENT`（未变 TRACKING）
    - 验证 `assignmentRecordRepository.findByTenderId(tender.id)` 为空（无 DISPATCH 记录）
  - 测试方法 `createTender_autoAssignNotMatched_tenderPersistedAsPending`：
    - `Mockito.when(autoAssignmentService.autoAssignIfPossible(any)).thenReturn(AssignmentResult.notMatched())`
    - 调用 `createTender`
    - 验证 tender status=`PENDING_ASSIGNMENT`，无 DISPATCH 记录

- [x] T011 [US2] [P] 实现 `deleteTender` 级联事务一致性场景（FR-007）
  - **实施确认**: `tender_attachments` 表 FK 已是 `ON DELETE CASCADE`（V1080 迁移），DB 自动级联删除，测试通过验证。
  - `@Nested @DisplayName("deleteTender 级联事务一致性")`
  - 测试方法 `deleteTender_withAttachments_deletesTenderAndAttachmentsInSameTransaction`：
    - 创建 tender + 2 个附件（`tenderCommandService.createTender` 带 attachments）
    - 验证 `attachmentRepository.findByTenderId(tender.id)` 有 2 条记录
    - 调用 `tenderCommandService.deleteTender(tender.id, user.id)`
    - `flushAndClear()`
    - 验证 `tenderRepository.findById(tender.id)` 为 empty
    - 验证 `attachmentRepository.findByTenderId(tender.id)` 为空（级联删除）
  - **注意**: 需确认 `Tender` entity 与 `TenderAttachment` 的关系映射是否 `cascade = CascadeType.REMOVE`，或 `tender_attachments` 表 FK 是否 `ON DELETE CASCADE`。若都不是，`deleteTender` 可能不删附件——此情况下测试应断言附件残留并标记为 bug（这就是集成测试的价值）。

- [x] T012 [US2] [P] 实现 `createTender` `purchaser_hash` UNIQUE 重复检测场景（FR-008，CO-265）
  - **实施偏差**: 原 plan 描述 `purchaser_hash UNIQUE`，但实际 DB 仅有 `@Index`（非 unique）。CO-265 真实实现是应用层 3 字段去重（`purchaserName + registrationDeadline + bidOpeningTime`），由 `TenderDeduplicationPolicy.isDuplicate` 判定，`TenderDeduplicationService.findDuplicates` 调用。
  - 测试方法调整为：相同三字段 → `TenderDuplicateException`；不同 `bidOpeningTime` → 创建成功。
  - `@Nested @DisplayName("CO-265: createTender purchaser_hash 重复检测")`
  - 测试方法 `createTender_duplicatePurchaserHash_throwsTenderDuplicateException`：
    - 创建 tender A，`purchaserName='测试采购方A'`（自动生成 hash）
    - 用 `JdbcTemplate.queryForObject` 查 tender A 的 `purchaser_hash`
    - 创建 tender B，`purchaserName='测试采购方A'`（同名，应生成相同 hash）
    - 断言抛 `TenderDuplicateException`
    - 验证 tender B 未落库（`tenderRepository.findByTitle('tenderB.title')` 为空）
  - 测试方法 `createTender_differentPurchaserHash_succeeds`：
    - 创建 tender A，`purchaserName='采购方A'`
    - 创建 tender B，`purchaserName='采购方B'`（不同名，不同 hash）
    - 断言不抛异常，两个 tender 都落库

**Checkpoint**: `mvn test -Dtest=TenderCommandServiceMysqlIntegrationTest` 全部通过，TenderCommandService 在真实 MySQL 下的 DB 约束、跨表事务、回滚行为被验证。既有 `TenderCommandServiceTest` + `TenderCommandServiceLinkCrmOpportunityDedupTest`（Mock unit test）继续通过。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 全量验证、文档同步、WIP push。

- [x] T013 运行 `cd backend && mvn test` 全量验证（含新集成测试 + 既有 unit test + ArchitectureTest），确认零回归
- [x] T014 [P] 运行 `npm run build` 确认前端无副作用（仅文档与测试代码改动，理论上无影响，但跑一次保险）
- [x] T015 [P] 更新 [specs/005-mysql-integration-test-rollout/checklists/requirements.md](checklists/requirements.md) 补充实现后的实际测试方法数、运行耗时、遇到的偏差
- [ ] T016 git commit US2 测试文件 + spec/tasks/checklist 更新（走 pre-commit gate）
- [ ] T017 push WIP 分支：`git push -u origin agent/codex/mysql-integration-test-rollout` + 在 Gitee 创建 PR，描述中说明：仅新增测试类 + spec 文档，无 main 代码改动；引用 CO-361/CO-373/CO-261/CO-265/CO-297/CO-310/CO-333 治理背景

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 已就绪，无任务
- **Phase 2 Foundational**: 跳过
- **Phase 3 US1**: T001 → T002 → (T003, T004, T005, T006 并行)
- **Phase 4 US2**: T007 → (T008, T009, T010, T011, T012 并行)
- **Phase 5 Polish**: T013 → (T014, T015, T016 并行) → T017

### User Story Dependencies

- **US1 (EffectiveRoleResolver)**: 无外部依赖，可独立完成
- **US2 (TenderCommandService)**: 无外部依赖，可独立完成
- US1 与 US2 可并行（不同文件、不同被测类、不同测试数据 ID 段约定相同但互不冲突）

### Within Each User Story

- 测试类骨架先建（T001/T002、T007）
- 各 `@Nested` 场景组可并行实现（不同测试方法，无依赖）
- 实现一个 `@Nested` 立即跑一次，避免累积失败

### Parallel Opportunities

- T003/T004/T005/T006 可并行（US1 内）
- T008/T009/T010/T011/T012 可并行（US2 内）
- US1 与 US2 可并行（不同被测类）
- T014/T015/T016 可并行（Polish 内）

---

## Parallel Example: User Story 1

```bash
# 完成 T001 (stub config) + T002 (test skeleton) 后，并行实现 4 个场景组：
Task: "T003 实现 OSS role_id=NULL fail-closed 场景"
Task: "T004 实现 OSS 缓存 miss 跨表查询场景"
Task: "T005 实现本地用户场景"
Task: "T006 实现 external_org_source_app 边界场景"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 3 US1（EffectiveRoleResolver 集成测试）
2. **STOP and VALIDATE**: `mvn test -Dtest=EffectiveRoleResolverMysqlIntegrationTest` 全绿
3. 若通过，US1 即为 MVP——已覆盖 CO-373 最高风险缺口

### Incremental Delivery

1. US1 完成 → 验证 → commit
2. US2 完成 → 验证 → commit
3. Phase 5 Polish → 全量验证 → push WIP → PR

### 风险与回退

- 若 `EffectiveRolePolicy.decide` 实际行为与 T004 假设不符（OSS 用户缓存 miss 时是否读实体），需查源码调整断言
- 若 `deleteTender` 不级联删附件（T011），需查 `Tender` entity 关系映射，可能发现 bug——记录并在 PR 描述中说明
- 若 `mvn test` 因新增集成测试显著变慢（>60s 增量），评估是否拆 failsafe 分阶段（本次不做，记录到 checklists）

---

## Notes

- 本任务的"实现"=写测试代码，无 main 代码改动
- 每个测试方法应独立可跑（不依赖其他测试方法的副作用）
- `@BeforeEach` 必须清理本测试类的数据，不依赖测试顺序
- 测试数据 ID 段 9001-9099 是约定，避免与 Flyway 真实数据冲突
- 遇到与 plan/research 假设不符的实际行为，优先调整测试断言，并在 checklists/requirements.md 记录偏差
