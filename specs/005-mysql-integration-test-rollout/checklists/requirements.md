# Specification Quality Checklist: MySQL Integration Test Rollout for Role Resolution & Tender Commands

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-06-30
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — 注：本 spec 是测试基础设施任务，引用了被测类名（EffectiveRoleResolver、TenderCommandService）和已有基类（AbstractMysqlIntegrationTest）作为业务实体，这些是 spec 的 Key Entities，不是实现选择
- [x] Focused on user value and business needs — 三个 user story 都从开发者/测试维护者的业务价值视角写（防回归、防事故、可复用）
- [x] Written for non-technical stakeholders — 业务场景描述用 plain language，技术细节放在 Key Entities
- [x] All mandatory sections completed — User Scenarios、Requirements、Success Criteria、Assumptions 均已填写

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — 无未决问题
- [x] Requirements are testable and unambiguous — 12 个 FR 都有明确的可验证条件（覆盖的具体场景、行为、约束）
- [x] Success criteria are measurable — 6 个 SC 都有具体数字（5 个场景、60 秒、零回归）
- [x] Success criteria are technology-agnostic — SC-004 提到 CI/本地两种环境是验收条件（非实现选择），其余 SC 都是用户可验证的成果
- [x] All acceptance scenarios are defined — 三个 user story 各有 3-5 个 Given/When/Then 场景
- [x] Edge cases are identified — 5 个 edge case 已列出（Testcontainers 不可用、OSS API 隔离、@Auditable 切面、性能、依赖构造）
- [x] Scope is clearly bounded — 明确仅新增测试类，不修改 main 代码
- [x] Dependencies and assumptions identified — 7 条假设 + 1 条依赖现有基础设施

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — 每个 FR 都映射到 user story 的 acceptance scenario
- [x] User scenarios cover primary flows — OSS role_id=NULL（CO-361/CO-373 根因）、linkCrmOpportunity 双层防御（CO-297）、tryAutoAssign 回滚 都已覆盖
- [x] Feature meets measurable outcomes defined in Success Criteria — SC-001/SC-002 直接对应 FR-001~FR-008
- [x] No implementation details leak into specification — 测试类名、基类名作为 Key Entities 出现（业务实体），未规定具体测试方法实现

## Notes

- 本 spec 是测试基础设施推广任务，不是典型业务功能。Key Entities 中包含被测类名和已有基类名，这是必要的业务上下文（说明"推广到什么"），不是实现细节（实现选择由 plan.md 决定）。
- spec 中提到的 CO-XXX 编号是项目 lessons-learned 中的治理工单号，作为业务背景引用，类似 GDPR、SOX 等合规引用。
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- 全部 16 项通过，spec 已就绪，可进入 `/speckit-plan` 阶段。

---

## Implementation Summary (2026-06-30)

### 实际测试方法数

| User Story | 测试类 | @Nested 场景组 | 测试方法数 | 状态 |
|---|---|---|---|---|
| US1 | `EffectiveRoleResolverMysqlIntegrationTest` | 4 | 8 | ✅ 全绿 |
| US2 | `TenderCommandServiceMysqlIntegrationTest` | 5 | 7 | ✅ 全绿 |
| **合计** | | **9** | **15** | ✅ |

### 运行耗时

- US1 + US2 联合运行：`mvn test -Dtest='EffectiveRoleResolverMysqlIntegrationTest,TenderCommandServiceMysqlIntegrationTest'`
- 总耗时约 90 秒（含 Testcontainers MySQL 8.0 启动 + Flyway 全迁移链 + 15 个测试方法）
- 纯测试方法执行时间约 18 秒

### 实施过程中的偏差

#### 偏差 1: T008 测试方法数缩减（US2）

- **原 plan**: 2 个测试方法（`linkCrmOpportunity_crmOpportunityIdAlreadyOccupied_throws409AndDoesNotOverwrite` + `linkCrmOpportunity_statusBidding_throws409`）
- **实际**: 1 个测试方法
- **原因**: admin 用户在 BIDDING 状态下无编辑权限，`commandAccessGuard.assertCanUpdateTender` 在 `assertCrmLinkAllowed` 之前抛 `AccessDeniedException`，永远走不到 409 业务规则。该纯函数行为由单元测试覆盖，集成测试不重复。
- **影响**: 无，CO-297 双层防御的核心场景（已被占用 → 409 + 不覆盖）仍被覆盖。

#### 偏差 2: T012 CO-265 实现机制修正（US2）

- **原 plan 描述**: `purchaser_hash` UNIQUE 约束
- **实际**: DB 仅有 `@Index`（非 unique）。CO-265 真实实现是**应用层 3 字段去重**（`purchaserName + registrationDeadline + bidOpeningTime`），由 `TenderDeduplicationPolicy.isDuplicate` 判定，`TenderDeduplicationService.findDuplicates` 调用。
- **测试方法调整**: 相同三字段 → `TenderDuplicateException`；不同 `bidOpeningTime` → 创建成功。
- **影响**: 无，反而更准确地验证了真实业务行为。

#### 偏差 3: T011 级联删除机制确认（US2）

- **原 plan 注意**: 需确认 `Tender` entity 与 `TenderAttachment` 的关系映射是否 `cascade = CascadeType.REMOVE`，或 `tender_attachments` 表 FK 是否 `ON DELETE CASCADE`。
- **实际确认**: `tender_attachments` 表 FK 在 V1080 迁移中已是 `ON DELETE CASCADE`，DB 自动级联删除。测试通过验证。
- **影响**: 无，测试断言附件被级联删除，符合 DB 实际行为。

### 全量验证结果

- `mvn test -Dtest='EffectiveRoleResolverMysqlIntegrationTest,TenderCommandServiceMysqlIntegrationTest'` → 15 tests, 0 failures, 0 errors, BUILD SUCCESS
- `mvn test -Dtest=ArchitectureTest` → 37 tests passed（无 main 代码改动，架构边界无回归）
- `npm run build` → success（仅 1 个 pre-existing composable 内联建议，非本次引入，不阻塞）

### 治理背景引用

本任务覆盖的 CO 编号：
- **CO-361 / CO-373**: OSS 用户 `role_id=NULL` 时 `User.getRoleCode()` 回退 "manager" 的五次反复修复根因（US1 覆盖）
- **CO-265**: `purchaser_hash` 三字段去重检测（US2 覆盖）
- **CO-297**: `linkCrmOpportunity` DB UNIQUE + 应用层 `crmOccupancyChecker` 双层防御（US2 覆盖）
- **CO-310**: 两步流程，关联 CRM 商机时写 DISPATCH 分配记录（US2 覆盖）
- **CO-333**: `ProjectManagerIdResolver.resolveByFullName`（US2 通过 `@MockBean ProjectManagerIdResolver` 隔离，未直接测试，但保证了被测主流程不依赖外部 RPC）
