# Feature Specification: 修复 OSS 用户权限扩散导致越权看所有菜单

**Feature Branch**: `agent/claude/fix-oss-permission-diffusion`

**Created**: 2026-07-08

**Status**: Draft

**Input**: 用户 03063（韩辉，跨部门协同人员）和 06234（郑蓉蓉，投标管理部高级投标经理）登录后能看到系统所有菜单。根因是 OSS 用户在登录链路中被映射为内部 `admin` roleCode，触发 `UserDetailsServiceImpl` 的 admin 权限扩散逻辑，把所有角色的 `menuPermissions` + `all` + `system.admin` 等系统级权限键全部加入 authorities，导致 OSS 用户看到的菜单远超 OSS 实际返回的菜单权限。期望：OSS 用户看到的菜单严格等于 OSS 返回的菜单权限，不多不少。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - OSS 用户看到的菜单严格等于 OSS 返回的菜单权限 (Priority: P1) 🎯 MVP

OSS 用户（无论 OSS 端配置什么角色）登录系统后，看到的菜单必须且仅由 OSS `getUserPermission` 接口返回的菜单 codes 决定。系统不应在 OSS 返回的菜单权限之外，因为内部 roleCode 映射（如 "投标系统管理员" → `admin`）而叠加任何额外的菜单权限扩散。

**Why this priority**: 这是当前生产环境正在发生的越权故障（2026-07-08 工号 03063/06234 用户报错），OSS 用户看到所有菜单违反权限最小化原则，是安全问题。第一层最小修复的目标是立即止血：让 OSS 用户回到"OSS 权限为准"的设计意图。

**Independent Test**: 用一个 OSS 用户登录（OSS 端配置 "投标系统管理员" 角色），断言其 `Authentication.authorities` 只包含 OSS 返回的菜单权限码 + 自身 `ROLE_<CODE>` authority，不包含 `all`、不包含其他角色的 `menuPermissions`、不包含 `system.admin`。

**Acceptance Scenarios**:

1. **Given** OSS 用户 03063（跨部门协同人员，OSS sysRoleList 配 "投标系统管理员"）登录，OSS `getUserPermission` 返回 35 个菜单 codes，**When** 系统构建该用户的 `UserDetails` authorities，**Then** authorities 只包含这 35 个菜单 codes 映射出的内部权限键 + 自身角色 authority，不包含 `all`，不包含其他角色的 `menuPermissions`。
2. **Given** OSS 用户 06234（投标管理部高级投标经理，OSS sysRoleList 配 "投标系统管理员"）登录，**When** 系统构建该用户的 `UserDetails` authorities，**Then** authorities 不包含 `system.admin` 权限键（该权限键仅系统超级管理员应持有）。
3. **Given** OSS 用户 03063 登录后访问前端菜单，**When** 前端 `hasPermission` 渲染菜单，**Then** 只渲染 OSS 返回的 35 个菜单 codes 对应的菜单项，不渲染系统所有菜单。
4. **Given** OSS 用户 03063 登录后访问任意需要 `system.admin` 权限键的接口，**When** 后端 `@PreAuthorize("hasAuthority('system.admin')")` 校验，**Then** 返回 403（因为 OSS 用户不再持有 `system.admin`）。

---

### User Story 2 - 本地 admin 账号行为完全不变 (Priority: P1)

本地系统管理员账号（`admin`，由 V57 迁移 + `DefaultAdminInitializer` 创建）的权限和菜单行为必须完全不变。本地 admin 仍应看到所有菜单、持有 `all` + `system.admin` + 所有角色的 `menuPermissions`，作为系统超级管理员的兜底语义。

**Why this priority**: 修复不能破坏本地 admin 的现有体验。本地 admin 是系统超级管理员，需要看所有菜单做管理操作。第一层修复必须精准隔离"OSS 用户的权限扩散"，不影响本地 admin。

**Independent Test**: 用本地 `admin` 账号登录，断言其 `Authentication.authorities` 包含 `all`、`system.admin`、所有角色的 `menuPermissions`，与修复前完全一致。

**Acceptance Scenarios**:

1. **Given** 本地 `admin` 账号登录（非 OSS 用户），**When** 系统构建该用户的 `UserDetails` authorities，**Then** authorities 仍包含 `all` + `system.admin` + 所有角色的 `menuPermissions`，与修复前完全一致。
2. **Given** 本地 `admin` 账号登录，**When** 前端 `hasPermission` 渲染菜单，**Then** 仍渲染系统所有菜单（行为不变）。
3. **Given** 本地 `admin` 账号登录，**When** 访问需要 `system.admin` 权限键的接口，**Then** 返回 200（行为不变）。

---

### User Story 3 - 前端 `all` 短路逻辑对 OSS 用户失效 (Priority: P2)

前端 `hasPermission(perms)` 当前对任何用户只要 `perms.includes('all')` 就全放行。OSS 用户在 User Story 1 修复后不再持有 `all`，但作为防御性兜底，前端 `all` 短路逻辑应仅对本地 admin 生效，对 OSS 用户即使意外拿到 `all` 也应失效。

**Why this priority**: 防御性兜底。User Story 1 已在后端阻断 OSS 用户拿到 `all`，但前端是最后一道防线。如果未来有其他路径意外给 OSS 用户注入 `all`，前端应能兜底。优先级 P2 因为后端已止血，前端是 defense-in-depth。

**Independent Test**: 模拟一个 OSS 用户 authorities 包含 `all`，断言前端 `hasPermission` 对该用户不短路放行（仍按实际权限键校验）。

**Acceptance Scenarios**:

1. **Given** OSS 用户登录后 authorities 意外包含 `all`（假设后端有未发现的漏洞），**When** 前端 `hasPermission(['some-permission'])` 校验，**Then** 不因 `all` 短路放行，仍按 `some-permission` 是否在 authorities 中校验。
2. **Given** 本地 `admin` 账号登录（authorities 包含 `all`），**When** 前端 `hasPermission(['some-permission'])` 校验，**Then** 仍短路放行（行为不变）。

---

### Edge Cases

- **OSS 用户 cache miss 时 fail-closed 行为不变**：`UserDetailsServiceImpl` 对 OSS 用户 cache miss 已有 fail-closed 逻辑（禁止 DB fallback），本次修复不动这条防线。
- **`ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 防线不动**：`RoleProfileCatalog.shouldSkipLegacyRoleCompat` 对 bid-Team/bid-otherDept/bid-administration 的 legacy role 兼容跳过逻辑不动（specs/024 FR-007 要求保留）。
- **`LoginRoleWhitelist` 登录白名单行为不变**：OSS 用户必须角色在白名单内才能登录，本次不放宽白名单。
- **`JobRoleLookupResolver` 的 "投标系统管理员" → `admin` 映射本次不动**：该映射是第二层中期治理的范围（涉及 03595/06234/11484 等真实业务管理员的权限变化评估），第一层只阻断扩散逻辑，不消除映射源头。
- **177 处 `@PreAuthorize` hasAnyRole 迁移不在本次范围**：specs/024 独立推进，本次不混入。
- **OSS 用户数据权限（dataScope）行为不变**：本次只修菜单权限扩散，不修数据权限。OSS 用户的 dataScope 仍由 roleCode 决定（admin/bidAdmin/bid-TeamLeader=all，其余=self）。
- **`EffectiveRoleResolver` 行为不变**：角色码解析强约束（CO-373 治理）不走 `User.getRoleCode()`，本次不修改 `EffectiveRoleResolver`。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `UserDetailsServiceImpl` 构建 authorities 时，对 `isOssUser=true` 的用户，MUST NOT 执行 admin 权限扩散逻辑（即 MUST NOT 把 `RoleProfileCatalog.seedDefinitions()` 中所有角色的 `menuPermissions` 加入 OSS 用户 authorities）。
- **FR-002**: `UserDetailsServiceImpl` 构建 authorities 时，对 `isOssUser=true` 的用户，MUST NOT 补发 `WAREHOUSE_MANAGE_PERMISSION` 和 `SYSTEM_ADMIN_PERMISSION` 权限键（这两个权限键仅本地 admin 应持有）。
- **FR-003**: `UserDetailsServiceImpl` 构建 authorities 时，对 `isOssUser=false` 的本地账号，admin 权限扩散逻辑和系统级权限键补发 MUST 保持与修复前完全一致（本地 admin 仍看所有菜单）。
- **FR-004**: OSS 用户的 authorities MUST 且 ONLY 包含：OSS `getUserPermission` 返回的菜单 codes 映射出的内部权限键 + 自身 `ROLE_<CODE>` authority（由 `RoleProfileCatalog.toAuthorityName` 转换）。
- **FR-005**: 前端 `hasPermission` 函数对 OSS 用户（可通过 `user.isOssUser` 或等价标识识别）MUST NOT 因 `perms.includes('all')` 短路放行；对本地 admin MUST 保持短路放行行为不变。
- **FR-006**: 修复 MUST NOT 影响 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 防线（bid-Team/bid-otherDept/bid-administration 的 legacy role 兼容跳过逻辑不动）。
- **FR-007**: 修复 MUST NOT 影响 `LoginRoleWhitelist` 登录白名单（OSS 用户角色必须在白名单内才能登录）。
- **FR-008**: 修复 MUST NOT 影响 `EffectiveRoleResolver` 的角色码解析行为（CO-373 治理强约束）。
- **FR-009**: 修复 MUST NOT 影响 `JobRoleLookupResolver` 的 "投标系统管理员" → `admin` 映射（该映射是第二层治理范围，本次只阻断扩散）。
- **FR-010**: 修复 MUST NOT 引入新的角色码或新的 `RoleProfileCatalog.SeedDefinition`（第一层最小修复不改角色模型）。

### Key Entities *(include if feature involves data)*

- **User**: 用户实体，包含 `username`、`roleCode`、`isOssUser` 标识（通过 OSS 同步状态判断）。
- **RoleProfileCatalog**: 角色配置目录，定义 7 个标准角色及其 `menuPermissions`。本次不修改其结构。
- **UserDetailsServiceImpl**: Spring Security `UserDetails` 构建服务，是本次修复的核心落点。负责根据用户类型（OSS / 本地）构建 authorities。
- **OssPermissionCache**: OSS 用户权限缓存，存储 OSS 实时抓取的 roleCode + menuPermissions。本次不修改其结构。
- **OssRoleResolver**: OSS 角色/权限解析器，把 OSS sysRoleList 解析为内部 roleCode。本次不修改其逻辑（`JobRoleLookupResolver` 映射不动）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: OSS 用户 03063 登录后，前端只渲染 OSS 返回的 35 个菜单 codes 对应的菜单项，不渲染系统所有菜单（生产环境验证）。
- **SC-002**: OSS 用户 06234 登录后，访问需要 `system.admin` 权限键的接口返回 403（生产环境验证）。
- **SC-003**: 本地 `admin` 账号登录后，前端仍渲染系统所有菜单，行为与修复前完全一致（回归测试验证）。
- **SC-004**: OSS 用户 authorities 不包含 `all`、不包含 `system.admin`、不包含其他角色的 `menuPermissions`（单元测试断言）。
- **SC-005**: 现有 `UserDetailsServiceImpl` 相关单元测试与集成测试全部通过，无回归。
- **SC-006**: 生产环境部署后，03063/06234 用户重新登录后菜单权限恢复正常（只看 OSS 配置的菜单）。

## Assumptions

- 现有 `UserDetailsServiceImpl` 的 `isOssUser` 判断逻辑（基于 OSS 缓存命中或用户来源标识）是准确的，本次不修改判断逻辑。
- 现有 `OssPermissionCache` 缓存的 `menuPermissions` 是 OSS 实时返回的菜单权限码，本次不修改缓存逻辑。
- 本地 `admin` 账号（由 V57 迁移 + `DefaultAdminInitializer` 创建）不是 OSS 用户（`isOssUser=false`），本次修复不影响其权限。
- 前端能通过某种方式识别 OSS 用户（如 `user.isOssUser` 字段或等价标识），用于 `hasPermission` 的 `all` 短路逻辑守卫。若无现成标识，需在后端 `UserDetails` 响应中暴露。
- `RoleProfileCatalog.seedDefinitions()` 的 `menuPermissions` 是内部角色权限配置的单一真相源，本次不修改其内容。
- 本次修复是第一层最小止血，不消除 `JobRoleLookupResolver` 的 "投标系统管理员" → `admin` 映射源头（第二层治理范围）。
- 本次不迁移 177 处 `@PreAuthorize` hasAnyRole（specs/024 独立推进）。
