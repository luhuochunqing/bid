# Tasks: 修复 OSS 用户权限扩散导致越权看所有菜单

**Input**: Design documents from `/specs/032-fix-oss-permission-diffusion/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本特性采用 TDD（Constitution III），测试任务包含在各 User Story 阶段内，必须先写测试（Red）再实现（Green）。

**Organization**: Tasks 按用户故事分组，支持独立实现和测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 该任务所属用户故事（如 US1, US2, US3）
- 所有任务描述包含精确文件路径

## Path Conventions

- **后端**: `backend/src/main/java/com/xiyu/bid/`、`backend/src/test/java/com/xiyu/bid/`
- **前端**: `src/`、`src/stores/`、`e2e/`

---

## Phase 1: Setup

**Purpose**: 确认分支状态、同步基线、准备开发环境

- [ ] T001 确认当前分支为 `agent/claude/fix-oss-permission-diffusion`，运行 `./scripts/sync-env.sh .` 同步 main 最新
- [ ] T002 [P] 确认主工作区 `/Users/user/xiyu/worktrees/trae` 开发环境已启动（前端 1323 / 后端 18089），如未启动在主工作区执行 `XIYU_DEV_CONFIRMED=1 npm run dev:all`

**Checkpoint**: 分支就绪，开发环境可用

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 后端 `AuthResponse` DTO 新增 `isOssUser` 字段（前端 US3 的前置依赖，所有 US 共享）

**⚠️ CRITICAL**: 此阶段必须完成，US3（前端 hasPermission）依赖此字段

- [ ] T003 [P] 在 `backend/src/main/java/com/xiyu/bid/dto/AuthResponse.java` 新增 `private boolean isOssUser` 字段，含 getter（如用 Lombok 则添加 `@Data` 或在 record 中新增字段）
- [ ] T004 在 `backend/src/main/java/com/xiyu/bid/service/AuthService.java` 的 `buildAuthResponse` 方法中，根据 `user.getExternalOrgSourceApp() != null && !user.getExternalOrgSourceApp().isBlank()` 填充 `isOssUser` 字段（参考 `UserDetailsServiceImpl.java#L62` 的 `isOssUser` 判断逻辑）
- [ ] T005 [P] 在 `backend/src/test/java/com/xiyu/bid/service/AuthServiceTest.java`（或新建 `AuthResponseTest.java`）新增测试用例：OSS 用户登录响应含 `isOssUser=true`；本地 admin 登录响应含 `isOssUser=false`

**Checkpoint**: AuthResponse 已暴露 isOssUser 字段，前端可读取

---

## Phase 3: User Story 1 - OSS 用户看到的菜单严格等于 OSS 返回的菜单权限 (Priority: P1) 🎯 MVP

**Goal**: OSS 用户（含 OSS 端配 "投标系统管理员" 角色的用户）的 authorities 和 menuPermissions 不再因 admin 扩散而包含 `all`/`system.admin`/其他角色 menuPermissions

**Independent Test**: 用 OSS 用户 03063/06234 登录，断言后端 authorities 不含 `all`/`system.admin`，前端 menuPermissions 不含 `all`，前端只渲染 OSS 返回的菜单

### Tests for User Story 1 (TDD Red 阶段) ⚠️

> **NOTE: 先写这些测试，确保它们 FAIL 再进入实现**

- [ ] T006 [P] [US1] 在 `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java` 新增测试 `ossAdminUser_shouldNotHaveAllPermission`：构造 OSS 用户（`isOssUser=true`，roleCode=admin，OSS menuPermissions 含若干菜单码），断言 authorities 不含 `"all"`
- [ ] T007 [P] [US1] 在 `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java` 新增测试 `ossAdminUser_shouldNotHaveSystemAdminPermission`：同上构造，断言 authorities 不含 `"system.admin"` 和 `"warehouse.manage"`
- [ ] T008 [P] [US1] 在 `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java` 新增测试 `ossAdminUser_authoritiesShouldOnlyContainOssMenuPermissions`：同上构造，断言 authorities 等于 OSS 返回的 menuPermissions + `admin` + `ROLE_ADMIN`（不多不少）
- [ ] T009 [P] [US1] 在 `backend/src/test/java/com/xiyu/bid/admin/service/DataScopeConfigServiceTest.java` 新增测试 `ossAdminUser_menuPermissionsShouldNotContainAll`：构造 OSS admin 用户，断言 `getRoleMenuPermissions` 返回值不含 `"all"`

### Implementation for User Story 1 (TDD Green 阶段)

- [ ] T010 [US1] 修改 `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java` L120 扩散分支：在条件前加 `!isOssUser &&` 守卫，使 OSS 用户不进入 admin 权限扩散逻辑
- [ ] T011 [US1] 修改 `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java` L130-139 catalog 基线权限补充分支：对 OSS 用户跳过 admin seed 合并（或改为 `!isOssUser && ...`），避免 OSS admin 用户合并 admin seed 的 `["all"]`
- [ ] T012 [US1] 修改 `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java` L143 admin fallback 分支：在条件前加 `!isOssUser &&` 守卫，使 OSS 用户不补发 `WAREHOUSE_MANAGE_PERMISSION` 和 `SYSTEM_ADMIN_PERMISSION`
- [ ] T013 [US1] 修改 `backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java` `getRoleMenuPermissions` 方法 L144-149：OSS 用户合并 catalog seed 时过滤掉 `"all"` 权限键（保留其他 CO-438 管理权限点如 `performance.manage`/`warehouse.manage`）

**Checkpoint**: US1 完成，OSS admin 用户后端 authorities 和前端 menuPermissions 不再含 `all`/`system.admin`

---

## Phase 4: User Story 2 - 本地 admin 账号行为完全不变 (Priority: P1)

**Goal**: 本地 admin 账号的 authorities 和 menuPermissions 修复前后完全一致

**Independent Test**: 用本地 `admin` 账号登录，断言 authorities 仍含 `all`/`system.admin`/所有角色 menuPermissions，前端 menuPermissions 仍含 `all`

### Tests for User Story 2 (TDD Red 阶段 — 回归测试) ⚠️

> **NOTE: 这些是回归测试，应在 US1 实现前先跑一遍通过（基线），US1 实现后再跑一遍仍通过（无回归）**

- [ ] T014 [P] [US2] 在 `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java` 新增测试 `localAdminUser_shouldHaveAllPermission_regression`：构造本地 admin 用户（`isOssUser=false`，roleCode=admin，legacyRole=ADMIN），断言 authorities 含 `"all"`、`"system.admin"`、`"warehouse.manage"`
- [ ] T015 [P] [US2] 在 `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java` 新增测试 `localAdminUser_shouldHaveAllRoleMenuPermissions_regression`：同上构造，断言 authorities 含 `RoleProfileCatalog.seedDefinitions()` 中所有角色的 menuPermissions
- [ ] T016 [P] [US2] 在 `backend/src/test/java/com/xiyu/bid/admin/service/DataScopeConfigServiceTest.java` 新增测试 `localAdminUser_menuPermissionsShouldContainAll_regression`：构造本地 admin 用户，断言 `getRoleMenuPermissions` 返回值含 `"all"`

### Implementation for User Story 2

- [ ] T017 [US2] 验证 US1 的 T010/T011/T012/T013 修改未误伤本地 admin：运行 `mvn test -Dtest=UserDetailsServiceImplTest,DataScopeConfigServiceTest` 全绿（无需新代码，只是验证 US1 守卫的 `!isOssUser` 条件正确放行本地 admin）

**Checkpoint**: US2 完成，本地 admin 行为无回归

---

## Phase 5: User Story 3 - 前端 `all` 短路逻辑对 OSS 用户失效 (Priority: P2)

**Goal**: 前端 `hasPermission` 对 OSS 用户即使意外拿到 `all` 也不短路放行（defense-in-depth）

**Independent Test**: 模拟 OSS 用户 authorities 含 `all`，断言前端 `hasPermission` 不短路

### Tests for User Story 3 (TDD Red 阶段) ⚠️

- [ ] T018 [P] [US3] 在 `src/stores/__tests__/user.spec.js`（或对应测试文件）新增测试 `hasPermission_ossUserWithAll_shouldNotShortCircuit`：构造 OSS 用户（`currentUser.isOssUser=true`，`menuPermissions=["all"]`），断言 `hasPermission("some-permission")` 返回 `false`
- [ ] T019 [P] [US3] 在 `src/stores/__tests__/user.spec.js` 新增测试 `hasPermission_localAdminWithAll_shouldShortCircuit_regression`：构造本地 admin（`currentUser.isOssUser=false`，`menuPermissions=["all"]`），断言 `hasPermission("some-permission")` 返回 `true`（回归）

### Implementation for User Story 3 (TDD Green 阶段)

- [ ] T020 [US3] 修改 `src/stores/user.js` 的 `hasPermission` getter（L37-40 附近）：`if (perms.includes('all')) return true` 改为 `if (perms.includes('all') && !state.currentUser?.isOssUser) return true`
- [ ] T021 [US3] 确认前端登录响应处理（`src/stores/user.js` 的 `fetchCurrentUser` 或 `setUser`）正确读取 `AuthResponse.isOssUser` 字段并写入 `state.currentUser.isOssUser`

**Checkpoint**: US3 完成，前端 defense-in-depth 就位

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 跨故事验证、架构测试、文档更新

- [ ] T022 [P] 运行后端架构测试 `cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest` 全绿
- [ ] T023 [P] 运行后端全量测试 `cd backend && mvn test` 全绿（无回归）
- [ ] T024 [P] 运行前端构建 `npm run build` 通过
- [ ] T025 [P] 运行前端单元测试 `npm run test:unit` 全绿
- [ ] T026 运行 `npm run check:front-data-boundaries`、`npm run check:doc-governance`、`npm run check:line-budgets` 通过
- [ ] T027 [P] 更新 `docs/lessons/lessons-learned.md` 新增章节「§45 OSS 用户权限扩散陷阱」（归纳本次根因、修复方案、防止复发的 ArchUnit 守卫建议）
- [ ] T028 [P] 在 `specs/032-fix-oss-permission-diffusion/` 目录下补充 `review-response.md`（如需审核回应）
- [ ] T029 在主工作区 `/Users/user/xiyu/worktrees/trae` 联调验证 quickstart.md 的场景 A（本地 admin 回归）和场景 B（OSS 用户修复）
- [ ] T030 提交 PR，PR 描述包含：根因分析、修复点清单、测试证据、生产验证步骤

**Checkpoint**: 所有验证通过，PR 就绪

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 Phase 1 完成，BLOCKS US3（前端需要 isOssUser 字段）
- **US1 (Phase 3)**: 依赖 Phase 1，可与 Phase 2 并行（US1 只改后端，不依赖 AuthResponse 字段）
- **US2 (Phase 4)**: 依赖 US1 完成（US2 是 US1 的回归验证）
- **US3 (Phase 5)**: 依赖 Phase 2 完成（前端需要 isOssUser 字段）
- **Polish (Phase 6)**: 依赖所有 US 完成

### User Story Dependencies

- **User Story 1 (P1)**: 可在 Phase 1 后开始，与 Phase 2 可并行
- **User Story 2 (P1)**: 依赖 US1 完成（验证 US1 未破坏本地 admin）
- **User Story 3 (P2)**: 依赖 Phase 2 完成（isOssUser 字段），与 US1/US2 可并行

### Within Each User Story

- 测试先写（Red）→ 实现（Green）→ 重构（Refactor）
- 后端文件修改顺序：UserDetailsServiceImpl → DataScopeConfigService
- 前端文件修改顺序：user.js（hasPermission）→ user.js（fetchCurrentUser）

### Parallel Opportunities

- Phase 2 的 T003（AuthResponse 字段）和 T005（测试）可并行
- Phase 3 的 T006-T009 测试任务可全部并行（不同测试方法）
- Phase 4 的 T014-T016 回归测试可全部并行
- Phase 5 的 T018-T019 测试任务可并行
- Phase 6 的 T022-T028 可大部分并行

---

## Parallel Example: User Story 1

```bash
# 并行写所有 US1 测试（Red 阶段）：
Task: "T006 ossAdminUser_shouldNotHaveAllPermission in UserDetailsServiceImplTest.java"
Task: "T007 ossAdminUser_shouldNotHaveSystemAdminPermission in UserDetailsServiceImplTest.java"
Task: "T008 ossAdminUser_authoritiesShouldOnlyContainOssMenuPermissions in UserDetailsServiceImplTest.java"
Task: "T009 ossAdminUser_menuPermissionsShouldNotContainAll in DataScopeConfigServiceTest.java"

# 串行实现（Green 阶段，同一文件需顺序改）：
Task: "T010 L120 扩散分支守卫 in UserDetailsServiceImpl.java"
Task: "T011 L130-139 catalog 基线权限分支守卫 in UserDetailsServiceImpl.java"
Task: "T012 L143 admin fallback 分支守卫 in UserDetailsServiceImpl.java"
Task: "T013 OSS 用户过滤 all in DataScopeConfigService.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup（同步基线）
2. Complete Phase 2: Foundational（AuthResponse 新增 isOssUser）
3. Complete Phase 3: User Story 1（后端止血，OSS 用户不再扩散）
4. **STOP and VALIDATE**: 后端单元测试全绿，OSS admin 用户 authorities 不含 `all`/`system.admin`
5. 此时已可部署止血（前端 US3 可后续补）

### Incremental Delivery

1. Setup + Foundational → AuthResponse 就绪
2. Add User Story 1 → 后端止血 → 可部署（MVP!）
3. Add User Story 2 → 回归验证本地 admin 不变
4. Add User Story 3 → 前端 defense-in-depth
5. Polish → 全量验证 + PR

### Critical Path

```
T001 → T002 → T003 → T004
                 ↓
T006/T007/T008/T009 (并行) → T010 → T011 → T012 → T013 (US1 Green)
                                                        ↓
T014/T015/T016 (并行回归) → T017 (US2 验证)
                                                        ↓
T018/T019 (并行) → T020 → T021 (US3)
                                                        ↓
T022-T030 (Polish)
```

---

## Notes

- [P] tasks = 不同文件，无依赖
- [Story] 标签将任务映射到具体用户故事
- 每个 US 可独立完成和测试
- TDD: 测试必须先 FAIL 再实现
- US1 是 MVP，单独完成即可止血
- 本地 admin 回归（US2）是必须的并行约束，不可跳过
- US3 是 defense-in-depth，可在 US1 部署后补
- 提交策略：US1 一个 commit，US2+US3 一个 commit，Polish 一个 commit（或按文件粒度）
