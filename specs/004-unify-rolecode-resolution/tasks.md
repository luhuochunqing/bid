# Tasks: 统一服务层角色码解析入口

**Feature**: 统一服务层角色码解析入口
**Branch**: `agent/zcode/co373-unify-rolecode`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## 依赖图与执行顺序

```
Phase 1 Setup (无依赖)
    ↓
Phase 2 Foundational: T002→T003→T004→T005 (纯核心 + 外壳 + 收敛)
    ↓
Phase 3 US1 (任务权限): T006→T007  ←─ 依赖 Phase 2
Phase 4 US2 (标书提交): T008→T009  ←─ 依赖 Phase 2
    （Phase 3/4 可并行：不同 Guard 文件）
    ↓
Phase 5 其他落点: T010~T016 (可并行，不同文件)
    ↓
Phase 6 US3+US4 (前端): T017→T018  ←─ 前端独立
    ↓
Phase 7 Polish: T019→T020
```

**MVP 范围**：Phase 1+2+3（纯核心 + resolver + TaskPermissionGuard），即可解锁 CO-373 核心阻断（US1 任务分配）。

---

## Phase 1: Setup

- [x] T001 确认当前分支 `agent/zcode/co373-unify-rolecode` 已从最新 origin/main 检出，工作区干净。运行 `git status` 与 `git log --oneline -1` 确认 spec/plan 提交已落盘。

## Phase 2: Foundational（阻塞性前置，所有 User Story 依赖）

> 纯核心 + 外壳编排 + 既有实现收敛。本阶段完成后，统一入口可用，后续各 Guard/Service 才能接入。

- [x] T002 [P] [TDD] 编写纯核心 `EffectiveRolePolicy` 单元测试 `backend/src/test/java/com/xiyu/bid/security/domain/EffectiveRolePolicyTest.java`，覆盖三条路径：(1) 缓存命中→CACHE_HIT；(2) 非OSS用户→LOCAL_USER；(3) OSS用户缓存未命中→CACHE_MISS_FAIL_CLOSED+null。覆盖空字符串缓存值归一化为未命中。此时尚未实现 `EffectiveRolePolicy`，测试应为 RED。
  - 文件：`backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRolePolicy.java`（待创建）
  - 文件：`backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRoleResult.java`（待创建 record）

- [x] T003 实现 `EffectiveRoleResult` record 与 `EffectiveRolePolicy.decide()` 纯核心，使 T002 测试转 GREEN。纯核心不注入任何 Spring Bean，不依赖 OssPermissionCache。放入 `security/domain` 包（受 FPJavaArchitectureTest 门禁）。
  - 文件：`backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRoleResult.java`
  - 文件：`backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRolePolicy.java`

- [x] T004 [P] [TDD] 编写外壳 `EffectiveRoleResolver` 单元测试 `backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverTest.java`，mock `OssPermissionCache`：验证(1)缓存命中委托纯核心返回CACHE_HIT；(2)非OSS用户返回LOCAL_USER+实体角色码；(3)OSS缓存miss返回null+warn日志。验证空字符串缓存值归一化。测试为 RED。
  - 文件：`backend/src/main/java/com/xiyu/bid/security/EffectiveRoleResolver.java`（待创建）

- [x] T005 实现 `EffectiveRoleResolver`（@Component，注入 OssPermissionCache），`resolve(User)` 读缓存→调 `EffectiveRolePolicy.decide()`→按 source 分级记日志；`resolveRoleCode(User)` 返回 roleCode。使 T004 测试 GREEN。运行 `mvn test -Dtest=EffectiveRolePolicyTest,EffectiveRoleResolverTest,FPJavaArchitectureTest` 全绿。
  - 文件：`backend/src/main/java/com/xiyu/bid/security/EffectiveRoleResolver.java`

## Phase 3: User Story 1 - 投标负责人可分配任务并管理任务看板 (P1)

> 独立测试：以 role_id=NULL 的 OSS 用户调用任务分配接口，应成功而非 403。

- [x] T006 [US1] [TDD] 编写/补充 `TaskPermissionGuard` 测试，验证 OSS 用户（缓存角色 bid-Team）调用 canManageTask 不再被拒；验证本地用户回归不变。测试 mock EffectiveRoleResolver。测试为 RED。
  - 文件：`backend/src/test/java/com/xiyu/bid/task/service/TaskPermissionGuardTest.java`（新增或补充）
  - 文件：`backend/src/main/java/com/xiyu/bid/task/service/TaskPermissionGuard.java`（待改造）

- [x] T007 [US1] 改造 `TaskPermissionGuard`：注入 `EffectiveRoleResolver`，将 line 24/38/60/90 的 `currentUser.getRoleCode()` 改为 `effectiveRoleResolver.resolveRoleCode(currentUser)`。使 T006 测试 GREEN。运行 `mvn test -Dtest=TaskPermissionGuardTest`。
  - 文件：`backend/src/main/java/com/xiyu/bid/task/service/TaskPermissionGuard.java`

## Phase 4: User Story 2 - 投标负责人/辅助人员可提交标书审核与提交投标 (P1)

> 独立测试：以 OSS 用户调用标书审核提交与投标提交接口，应成功而非 403。
> 与 Phase 3 可并行：不同 Guard/Service 文件，无交叉依赖。

- [x] T008 [US2] [TDD] 编写 `ProjectDraftingService` 收敛测试：验证删除私有 `resolveEffectiveRoleCode` 后改用 `EffectiveRoleResolver`，OSS 用户提交标书审核/投标成功。补充既有测试 mock resolver。测试为 RED。
  - 文件：`backend/src/test/java/com/xiyu/bid/project/service/ProjectDraftingServiceTest.java`（既有，需调整 mock）
  - 文件：`backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java`（待改造）

- [x] T009 [US2] 改造 `ProjectDraftingService`：删除私有 `resolveEffectiveRoleCode`（line 222-238），注入 `EffectiveRoleResolver`，`assertCanSubmit`（line 212）改调 `effectiveRoleResolver.resolveRoleCode(currentUser)`。使 T008 GREEN。运行 `mvn test -Dtest=ProjectDraftingServiceTest`。
  - 文件：`backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java`

## Phase 5: 其他 Guard/Service 落点改用 resolver（可并行，不同文件）

> 这些落点直调 `user.getRoleCode()` 参与权限/可见范围判定，需统一改调 resolver。各文件相互独立，可并行改造。

- [x] T010 [P] 改造 `CurrentUserResolver.getCurrentRoleCode()`（line 64）改调 `effectiveRoleResolver.resolveRoleCode(getCurrentUser())`；注入 `EffectiveRoleResolver`。新增 `resolveEffectiveRoleCode(User)` 委托方法。运行 `mvn test -Dtest=CurrentUserResolverTest`（如无则补最小测试）。
  - 文件：`backend/src/main/java/com/xiyu/bid/security/CurrentUserResolver.java`

- [x] T011 [P] 改造 `TenderCommandAccessGuard`（line 26/43）改调 resolver。运行相关测试。
  - 文件：`backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandAccessGuard.java`

- [x] T012 [P] 改造 `ProjectTaskAuthorizationGuard`（line 92）改调 resolver。
  - 文件：`backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectTaskAuthorizationGuard.java`

- [x] T013 [P] 改造 `ProjectAccessScopeService`（line 57/83/122）改调 resolver。
  - 文件：`backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java`

- [x] T014 [P] ~~改造 `BatchAssignmentPolicy`（line 99）、`AssignmentCandidatePolicy`（line 66/68/85）改调 resolver~~ **评估后保持不变**：纯核心类无法注入 Spring bean；`isAdmin` 走实体 roleCode 对非 OSS 用户是安全的 over-deny；`AssignmentCandidatePolicy` 使用候选人 roleCode（非操作者）与本次收敛无关。详见 implementation-notes.md CO-373 §5。
  - 文件：`backend/src/main/java/com/xiyu/bid/batch/core/BatchAssignmentPolicy.java`（未改）
  - 文件：`backend/src/main/java/com/xiyu/bid/user/core/AssignmentCandidatePolicy.java`（未改）

- [x] T015 [P] 改造 `SettingsService`（line 65）、`PlatformAccountService`（line 127）、`RoleProfileService`（line 172）、`ProjectTaskWorkflowService`（line 71）、`ProjectDocumentWorkflowService`（line 104）改调 resolver。
  - 文件：`backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java`
  - 文件：`backend/src/main/java/com/xiyu/bid/platform/service/PlatformAccountService.java`
  - 文件：`backend/src/main/java/com/xiyu/bid/service/RoleProfileService.java`
  - 文件：`backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectTaskWorkflowService.java`
  - 文件：`backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowService.java`

- [x] T016 [P] ~~收敛既有 4 处：`DataScopeConfigService`、`UserDetailsServiceImpl`、`AuthService` 改用 `EffectiveRoleResolver`~~ **评估完成-不改动（用户确认方向C）**：`DataScopeConfigService.getRoleCode()` 语义更收紧（非 admin 非 OSS 用户也 fail-closed，防 DB roleCode 越权），收敛需先收紧 `EffectiveRolePolicy` 会动 7 个已改 Guard 引入回归风险。`UserDetailsServiceImpl`/`AuthService` 为 auth-sync 路径（非服务层权限决策），符合 R6 保留分类。详见 implementation-notes.md CO-373 §4 + T016 评估结论。
  - 文件：`backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java`（未改，保持独立收紧实现）
  - 文件：`backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java`（未改，auth-sync 路径）
  - 文件：`backend/src/main/java/com/xiyu/bid/service/AuthService.java`（未改，auth-sync 路径）

## Phase 6: User Story 3 + 4 - 前端控件可见性与回显兜底 (P2)

> 前端独立于后端，可并行。US3 由后端角色码修正自然恢复（前端拿到正确 roleCode），US4 是前端回显逻辑修复。

- [x] T017 [US4] 修复 `useInitiationStageActions.js` 的 `biddingAssistantName` 回显：参照同文件 `biddingLeaderName` 的兜底逻辑，当 `data.biddingAssistantId` 存在但 `data.biddingAssistantName` 为空时，补查姓名或显示占位文本，不直接显示"未分配"。先读 `InitiationStage.vue:161-169` 与 `useInitiationStageActions.js:175-220` 确认兜底模式。
  - 文件：`src/views/Project/stages/useInitiationStageActions.js`
  - 文件：`src/views/Project/stages/InitiationStage.vue`（回显显示处）

- [x] T018 [US3] 验证前端角色码来源：确认登录/会话接口返回的 roleCode 经后端修正后为正确值（bid-Team/bid-projectLeader），标书审核人搜索框的 v-if 条件可正常通过。如前端有独立的 roleCode 判断逻辑需同步对齐。运行 `npm run build` + `npm run check:line-budgets`。
  - 文件：`src/views/Project/stages/DraftingStage.vue`（或含审核人搜索框的组件，待确认）

## Phase 7: Polish & Cross-Cutting（收尾）

- [x] T019 运行后端全量架构与回归测试：`mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest` + 受影响模块测试。确认无回归。搜索验证 `getRoleCode()` 在权限判定路径的直调落点降为 0（Assembler/Mapper/TraceFilter 除外，见 research R6）。

- [x] T020 运行 `scripts/preflight-self-review.sh`，将自审清单粘贴到 PR description，逐项确认 ⬜→✅。运行 `npm run check:doc-governance`。提交 PR，PR body 关联 Linear CO-373 与本 spec。

## 实施策略

1. **MVP 优先**：先完成 Phase 1+2+3（T001-T007），即可解锁 CO-373 最核心阻断（US1 任务分配），形成可演示增量。
2. **并行机会**：
   - Phase 3（TaskPermissionGuard）与 Phase 4（ProjectDraftingService）不同文件，可并行。
   - Phase 5 各落点文件相互独立，可并行改造。
   - Phase 6 前端与后端独立，可并行。
3. **每任务原子提交**：每个 T 完成后提交，commit message 引用 CO-373。
4. **TDD 纪律**：T002/T004/T006/T008 先写测试（RED），再实现（GREEN），再重构（Phase 7）。

## 格式验证

- 所有任务含 checkbox、ID、（如适用）[P]/[US] 标签、文件路径 ✅
- 每个 User Story 有独立测试标准 ✅
- 依赖关系明确（Phase 2 阻塞后续） ✅
