# Feature Specification: OSS 与本地用户权限代码路径分离

**Feature Branch**: `agent/claude/oss-local-permission-path-separation`

**Created**: 2026-07-10

**Status**: Draft

**Input**: 系统存在两套人员权限体系（OSS 登录鉴权 + 事件库 DB 缓存选人），设计上声明分离，但代码实现层共用 `UserDetailsServiceImpl` / `DataScopeConfigService` / `User.getRoleCode()`，导致 OSS 用户走到为本地用户写的代码路径时反复踩坑（CO-361 → CO-373 → spec 032 → CO-551 → bid-Team 菜单泄漏 → 标讯 403，10+ 轮修复未根治）。期望：通过代码路径分离或强约束门禁，让"新增按角色判断分支"不再必然产生 OSS/本地不一致场景。

**根因分析**：`docs/lessons/root-cause-analysis-oss-local-permission-dual-track.md`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - OSS 用户 authorities 严格等于 OSS 返回值（方案 A 路径分离）(Priority: P1) 🎯 MVP

OSS 用户登录后，`UserDetails.authorities` 必须且仅包含 OSS `getUserPermission` 返回的菜单 codes 映射出的内部权限键 + 自身 `ROLE_<CODE>` authority。任何为本地 admin 设计的"扩散逻辑"（`all` 展开、`system.admin` 补发、catalog seed 合并）都不应作用于 OSS 用户。

**Why this priority**: 这是 spec 032 三层防御之后的根治方向。spec 032 通过 `isOssUser` 守卫止血，但每新增一个"按角色判断"分支仍可能产生新的交叉感染点。方案 A 从代码路径层面彻底隔离 OSS 用户与本地用户，让"扩散逻辑"在 OSS 路径上物理不可达。

**Independent Test**: 用一个 OSS 用户登录（OSS 端配置任意角色），断言其 `Authentication.authorities` 只包含 OSS 返回的菜单权限码 + 自身角色 authority，不包含 `all`、不包含其他角色的 `menuPermissions`、不包含 `system.admin`（除非 OSS 端通过菜单 1010 显式授权）。

**Acceptance Scenarios**:

1. **Given** OSS 用户（OSS sysRoleList 配 "投标系统管理员"）登录，OSS `getUserPermission` 返回 35 个菜单 codes，**When** 系统构建该用户的 `UserDetails` authorities，**Then** authorities 只包含这 35 个菜单 codes 映射出的内部权限键 + 自身角色 authority，不包含 `all`，不包含其他角色的 `menuPermissions`。
2. **Given** OSS 用户登录后访问任意需要权限键 P 的接口，**When** 后端 `@PreAuthorize("hasAuthority('P')")` 校验，**Then** 返回 200 当且仅当 P 在 OSS 返回的菜单 codes 映射集合中。
3. **Given** 新增一个"按角色判断"的业务分支（如 `if (roleCode.equals("admin"))`），**When** OSS 用户走到该分支，**Then** 该分支要么对 OSS 用户不触发（因为代码路径分离），要么有显式 `isOssUser` 守卫。

---

### User Story 2 - 本地 admin 账号行为完全不变 (Priority: P1)

本地系统管理员账号（`admin`，由 V57 迁移 + `DefaultAdminInitializer` 创建）的权限和菜单行为必须完全不变。本地 admin 仍应看到所有菜单、持有 `all` + `system.admin` + 所有角色的 `menuPermissions`，作为系统超级管理员的兜底语义。

**Why this priority**: 修复不能破坏本地 admin 的现有体验。本地 admin 是系统超级管理员，需要看所有菜单做管理操作。

**Independent Test**: 用本地 `admin` 账号登录，断言其 `Authentication.authorities` 包含 `all`、`system.admin`、所有角色的 `menuPermissions`，与修复前完全一致。

**Acceptance Scenarios**:

1. **Given** 本地 `admin` 账号登录（非 OSS 用户），**When** 系统构建该用户的 `UserDetails` authorities，**Then** authorities 仍包含 `all` + `system.admin` + 所有角色的 `menuPermissions`，与修复前完全一致。
2. **Given** 本地 `admin` 账号登录，**When** 前端 `hasPermission` 渲染菜单，**Then** 仍渲染系统所有菜单（行为不变）。

---

### User Story 3 - 选人接口角色信息来源明确 (Priority: P2)

选人接口（`/api/users/search`、`/api/users/assignable-candidates`）返回的候选人角色信息必须明确标注数据来源（DB role_profile 快照），且不与 OSS 缓存的角色混淆。OSS 用户作为候选人被列出时，其角色信息应来自 DB role_profile（组织架构同步写入），而不是 OSS 缓存。

**Why this priority**: 选人接口直调 `user.getRoleCode()` 是 CO-373 的根因之一。虽然 `DbRoleSnapshotResolver` 已存在，但 `UserSearchService` 和 `AssignmentCandidatePolicy` 绕过它直调实体方法。需要强制选人接口走 `DbRoleSnapshotResolver` 或等价入口。

**Independent Test**: 调用 `/api/users/search`，断言返回的每个候选人 `roleCode` 与 DB `role_profile` 表的值一致，不读 OSS 缓存。

**Acceptance Scenarios**:

1. **Given** OSS 用户 X（DB role_profile=bid-Team，OSS 缓存 roleCode=admin），**When** 其他用户调用 `/api/users/search` 查到 X，**Then** 返回的 `roleCode` 是 `bid-Team`（来自 DB），不是 `admin`（来自 OSS 缓存）。
2. **Given** 选人接口返回候选人列表，**When** 调用方根据 `roleCode` 做权限过滤，**Then** 过滤基于 DB 快照，不基于 OSS 缓存。

---

### User Story 4 - ArchUnit 强制代码路径分离 (Priority: P2)

新增 ArchUnit 规则，强制 OSS 用户相关类不得依赖本地 `RoleProfileCatalog` / `DataScopeConfigService` 的本地分支。新增 `user.getRoleCode()` 直调检测，扩展 `scripts/check-rolecode-direct-calls.mjs` 覆盖 `UserSearchService`、`AssignmentCandidatePolicy` 等选人接口。

**Why this priority**: CO-373 的 `@Deprecated` + pre-push 拦截器是软约束，仍可通过 `// SAFE:` 注释豁免。ArchUnit 是硬约束，CI 强制执行，能彻底阻断新增违规。

**Independent Test**: 新增一个 OSS 用户相关类依赖 `RoleProfileCatalog`，断言 `ArchitectureTest` 失败。

**Acceptance Scenarios**:

1. **Given** 新增一个 OSS 用户相关类（位于 `..oss..` 包），**When** 该类依赖 `RoleProfileCatalog` 或 `DataScopeConfigService`，**Then** `ArchitectureTest` 失败。
2. **Given** 新增代码直调 `user.getRoleCode()`（无 `// SAFE:` 豁免），**When** pre-push gate 运行，**Then** 拦截并要求迁移到 `EffectiveRoleResolver` 或 `DbRoleSnapshotResolver`。

---

### Edge Cases

- **OSS 用户 cache miss 时 fail-closed 行为不变**：`EffectiveRoleResolver` 对 OSS 用户 cache miss 已有 fail-closed 逻辑（禁止 DB fallback），本次修复不动这条防线。
- **`ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 防线不动**：bid-Team/bid-otherDept/bid-administration 的 legacy role 兼容跳过逻辑不动（specs/024 FR-007 要求保留）。
- **`LoginRoleWhitelist` 登录白名单行为不变**：OSS 用户角色必须在白名单内才能登录，本次不放宽白名单。
- **`JobRoleLookupResolver` 映射**：方案 A 范围内不动（第二层治理范围），方案 B/C 可触及。
- **177 处 `@PreAuthorize` hasAnyRole 迁移不在本次范围**：specs/024 独立推进，本次不混入。
- **`permissionStale` 字段**：根因分析中发现此字段在 project_memory 中有记录但代码未实现，本次不实现（属于独立的 OSS 同步失败提示课题）。

## Requirements *(mandatory)*

### Functional Requirements

#### 方案 A：代码路径分离（推荐根治）

- **FR-A001**: 新增 `OssUserDetailsService`（或等价服务），独立负责 OSS 用户的 `UserDetails` 构建，不调用 `UserDetailsServiceImpl` 的 admin 扩散逻辑。
- **FR-A002**: 新增 `OssAuthResponseBuilder`（或等价服务），独立负责 OSS 用户的 `AuthResponse` 构建，不调用 `DataScopeConfigService` 的本地 admin 分支。
- **FR-A003**: `OssUserDetailsService` 构建 authorities 时，MUST 且 ONLY 包含：OSS `getUserPermission` 返回的菜单 codes 映射出的内部权限键 + 自身 `ROLE_<CODE>` authority。
- **FR-A004**: `OssUserDetailsService` MUST NOT 调用 `RoleProfileCatalog.seedDefinitions()`、MUST NOT 合并 catalog seed、MUST NOT 执行 admin 扩散逻辑。
- **FR-A005**: 本地用户继续走现有 `UserDetailsServiceImpl`，行为完全不变（含 admin 扩散逻辑）。
- **FR-A006**: 新增 ArchUnit 规则：`..oss..` 包下的类 MUST NOT 依赖 `RoleProfileCatalog` 或 `DataScopeConfigService` 的本地 admin 分支。
- **FR-A007**: 删除 `User.getRoleCode()` 的 `"manager"` fallback，改为抛 `IllegalStateException`（fail-closed），强制调用方走 `EffectiveRoleResolver` 或 `DbRoleSnapshotResolver`。
- **FR-A008**: 选人接口（`UserSearchService`、`AssignmentCandidatePolicy`）MUST 通过 `DbRoleSnapshotResolver` 获取角色码，禁止直调 `user.getRoleCode()`。

#### 方案 B：强约束门禁（最小代价）

- **FR-B001**: 扩展 `scripts/check-rolecode-direct-calls.mjs`，禁止任何 `user.getRoleCode()` 直调（含 `UserSearchService`、`AssignmentCandidatePolicy`），豁免白名单仅限已记录场景。
- **FR-B002**: 新增 ArchUnit 规则：`UserDetailsServiceImpl` 中所有 `roleCode.equals("admin")` 分支必须前置 `!isOssUser` 守卫（通过 AST 或正则匹配检测）。
- **FR-B003**: 新增测试：每个 OSS 角色（bid-Team / bid-TeamLeader / bid-projectLeader / bid-administration / bid-otherDept / admin）必须有一个"权限不扩散"测试用例，断言 OSS 用户 authorities 不包含 `all`、不包含其他角色的 `menuPermissions`。
- **FR-B004**: 新增测试：选人接口返回的 `roleCode` 必须与 DB `role_profile` 一致，不读 OSS 缓存。

#### 方案 C：消除 "all" 短路 + admin 扩散（中间态）

- **FR-C001**: 删除 `UserDetailsServiceImpl` 中 admin 扩散逻辑（`menuPermissions.contains("all")` 触发的 seed 全量展开）。
- **FR-C002**: 删除前端 `hasPermission` 的 "all" 短路逻辑。
- **FR-C003**: 本地 admin 通过显式 seed 拿到全权限（在 `RoleProfileCatalog` 中为 admin 角色显式列出所有权限键，不靠扩散）。
- **FR-C004**: `DataScopeConfigService` 合并 catalog seed 时，对所有用户（含本地 admin）统一过滤 "all"。
- **FR-C005**: 测试：本地 admin 仍能访问所有菜单（通过显式 seed），OSS 用户行为与 spec 032 一致。

### Key Entities *(include if feature involves data)*

- **User**: 用户实体，包含 `username`、`roleCode`、`isOssUser` 标识。方案 A 会修改 `getRoleCode()` 的 fallback 行为。
- **RoleProfileCatalog**: 角色配置目录。方案 A 中 OSS 路径不再依赖它；方案 C 中需为 admin 显式列出所有权限键。
- **UserDetailsServiceImpl**: Spring Security `UserDetails` 构建服务。方案 A 中拆分为 `OssUserDetailsService` + 现有 `UserDetailsServiceImpl`；方案 B 中加 ArchUnit 守卫；方案 C 中删除扩散逻辑。
- **DataScopeConfigService**: 数据范围配置服务。方案 A 中 OSS 路径不再依赖它；方案 C 中统一过滤 "all"。
- **OssPermissionCache**: OSS 用户权限缓存。所有方案都不修改其结构。
- **EffectiveRoleResolver**: 角色码解析统一入口。所有方案都不修改其行为。
- **DbRoleSnapshotResolver**: 选人业务用的 DB 快照入口。方案 A 强制选人接口走它；方案 B 通过门禁强制。
- **UserSearchService / AssignmentCandidatePolicy**: 选人接口。方案 A/B 都要求走 `DbRoleSnapshotResolver`，禁止直调 `user.getRoleCode()`。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: OSS 用户登录后，前端只渲染 OSS 返回的菜单 codes 对应的菜单项（生产环境验证，复用 spec 032 的 SC-001）。
- **SC-002**: 本地 `admin` 账号登录后，行为与修复前完全一致（回归测试验证）。
- **SC-003**: 选人接口返回的 `roleCode` 与 DB `role_profile` 一致，不读 OSS 缓存（单元测试断言）。
- **SC-004**: ArchUnit 测试覆盖 OSS/本地代码路径分离（方案 A）或 admin 分支守卫（方案 B）。
- **SC-005**: `user.getRoleCode()` 直调检测扩展覆盖选人接口，pre-push gate 拦截新增违规。
- **SC-006**: 每个OSS角色有"权限不扩散"测试用例，CI 强制执行。
- **SC-007**: 未来新增"按角色判断"分支时，要么对 OSS 用户不触发（方案 A 代码路径分离），要么有显式守卫（方案 B/C）。

## Assumptions

- 现有 `OssPermissionCache` 的 `isOssUser` 判断逻辑是准确的，本次不修改判断逻辑。
- 现有 `OssLoginFlowService` 的 OSS 4 步 API 调用链路是稳定的，本次不修改。
- 现有 `EffectiveRoleResolver` 的角色码解析强约束（CO-373 治理）行为不变。
- `RoleProfileCatalog.seedDefinitions()` 的 `menuPermissions` 是本地角色权限配置的单一真相源，方案 A 中 OSS 路径不再依赖它；方案 C 中需为 admin 显式列出所有权限键。
- 177 处 `@PreAuthorize` hasAnyRole 迁移（specs/024）独立推进，本次不混入。
- `JobRoleLookupResolver` 的 "投标系统管理员" → `admin` 映射在方案 A 范围内不动（第二层治理范围）。
- 方案选择（A/B/C）需经过 speckit-clarify 阶段与团队确认，本 spec 同时列出三个方案的 FR 供讨论。

## Open Questions

1. **方案选择**：A（根治但改动大）/ B（最小代价但软约束）/ C（中间态但可能影响本地 admin）？建议经过 clarify 阶段与团队讨论后确定。
2. **`User.getRoleCode()` fallback 删除范围**：方案 A 提出删除 `"manager"` fallback 改抛异常，是否影响历史调用方？需在 clarify 阶段盘点全仓调用点。
3. **ArchUnit 规则的精确边界**：方案 A 的 `..oss..` 包定义需要明确（是按现有包结构还是新增包？）。
4. **方案 C 的 admin 显式 seed**：admin 角色需要显式列出所有权限键，是否与 `RoleProfileCatalog` 现有结构兼容？需在 plan 阶段设计。

## Related Specs

- `specs/032-fix-oss-permission-diffusion/` — 第一层止血（isOssUser 守卫 + 前端 hasPermission 守卫），本 spec 是其根治方向。
- `specs/024-preauthorize-unification/` — 177 处 @PreAuthorize hasAnyRole 双轨制技术债，独立推进。
- `specs/004-unify-rolecode-resolution/` — CO-373 角色码解析统一入口，本 spec 在其基础上进一步分离代码路径。
