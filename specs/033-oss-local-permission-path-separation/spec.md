# Feature Specification: OSS 与本地用户权限代码路径分离

**Feature Branch**: `agent/claude/oss-local-permission-path-separation`

**Created**: 2026-07-10  **Updated**: 2026-07-10 (v2 — 整合方案评估反馈)

**Status**: Draft (post-evaluation, ready for clarify)

**Input**: 系统存在两套人员权限体系（OSS 登录鉴权 + 事件库 DB 缓存选人），设计上声明分离，但代码实现层共用 `UserDetailsServiceImpl` / `DataScopeConfigService` / `User.getRoleCode()`，导致 OSS 用户走到为本地用户写的代码路径时反复踩坑（CO-361 → CO-373 → spec 032 → CO-551 → bid-Team 菜单泄漏 → 标讯 403，10+ 轮修复未根治）。期望：通过代码路径分离或强约束门禁，让"新增按角色判断分支"不再必然产生 OSS/本地不一致场景。

**根因分析**：[docs/lessons/root-cause-analysis-oss-local-permission-dual-track.md](../../docs/lessons/root-cause-analysis-oss-local-permission-dual-track.md)

---

## 推荐策略（v2 新增 — 评估反馈整合）

> [!TIP]
> **推荐组合策略：B（立即）→ A（中期）+ 借鉴 C 的一点**
>
> 1. **立即**：实施方案 B 的**测试部分**（FR-B003/B004）—— 每个 OSS 角色"权限不扩散"测试 + 选人接口 roleCode 来源测试。这些测试在方案 A 落地后仍有效，不浪费。
> 2. **中期**：实施方案 A 的**核心拆分** —— Delegate 模式（见 §架构设计 A）拆分 `UserDetailsServiceImpl`，加 ArchUnit 硬约束。
> 3. **借鉴 C**：admin 的 `"all"` 显式 seed 改为**计算生成**（`allPermissionsUnion()` 方法，flatMap 取并集 + 系统级权限），降低手动列举遗漏风险；**不**删除前端 `"all"` 短路 —— 只在后端保证 OSS 用户拿不到 `"all"`。

**理由**：
- 方案 A 单独实施需要重构注入链路 + ArchUnit 包定义，3-5 天，期间无止血
- 方案 B 单独实施是软约束，最终仍需 A 根治
- 组合策略先用 B 的测试建立"安全网"，再做 A 的物理拆分，风险与改动量可控

---

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

### User Story 3 - 选人接口角色信息来源明确 (Priority: P2, **不阻塞 US-1/2**)

> [!NOTE]
> **US-3 定位说明（v2 新增）**：US-3 是**数据展示**问题（选人 DTO 中的 roleCode 来源），与 US-1/2 的**鉴权**问题（authorities 扩散）是不同的问题域。
>
> - US-1/2 阻塞 P1，可在方案 A/B 范围内独立交付
> - US-3 不阻塞 US-1/2，可作为独立 sub-task 推进，建议在 P1 完成后再做
> - US-3 涉及 `AssignmentCandidatePolicy` 这个 Pure Core 的改造，需谨慎（见 §迁移计划）

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
- **并发登录场景（v2 新增）**：OSS 用户 A 已登录使用系统时，OSS 端管理员修改了 A 的菜单权限，A 的下一次 API 请求仍使用旧 JWT token（authorities 不变）。**显式假设**：权限热更新不在本次范围，"下次登录生效"是可接受行为。如需即时失效，需引入 token 黑名单或缩短 JWT TTL，属独立课题。
- **`definitionForCode(null)` fallback 到 admin（v2 新增）**：`RoleProfileCatalog.definitionForCode()` 在 roleCode 为 null 或未注册时 fallback 到 admin 定义（[L193-198](../../backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java#L193-L198)）。方案 A 的 OSS 路径 **MUST NOT** 调用 `definitionForCode()`，避免这个 fallback 在 OSS 路径上意外触发权限扩散。FR-A004 已明确禁止 OSS 路径依赖 `RoleProfileCatalog`。

---

## 架构设计 A（v2 新增 — Delegate 模式）

### 问题：Spring Security 的单一 `UserDetailsService` 契约

`SecurityConfig.authenticationProvider()` 通过 `DaoAuthenticationProvider` 注入了**唯一一个** `UserDetailsService`（[SecurityConfig.java:205-206](../../backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java#L205-L206)）。方案 A 不能简单地新增一个 `OssUserDetailsService` Bean —— Spring Security 不会自动分发。

### 推荐：Delegate 模式（CompositeUserDetailsService）

**不**新增多个 `UserDetailsService` Bean，而是新增一个外壳 `CompositeUserDetailsService` 作为唯一 `UserDetailsService`，根据用户类型分发到 OSS 或本地 delegate。

```
                  ┌──────────────────────────────────┐
                  │  SecurityConfig                  │
                  │  DaoAuthenticationProvider       │
                  │   └─ setUserDetailsService(...)   │
                  └────────────┬─────────────────────┘
                               │
                               ▼
        ┌──────────────────────────────────────────────────┐
        │  CompositeUserDetailsService                    │
        │  (implements UserDetailsService)                │
        │                                                  │
        │  + loadUserByUsername(username)                  │
        │      1. user = userRepository.findByUsername()  │
        │      2. if (user.isOssUser())                   │
        │             return ossDelegate.load(user)       │
        │         else                                    │
        │             return localDelegate.load(user)     │
        └────────────┬─────────────────────┬──────────────┘
                     │                     │
                     ▼                     ▼
   ┌─────────────────────────┐  ┌────────────────────────────┐
   │ OssUserDetailsDelegate  │  │ LocalUserDetailsDelegate   │
   │ (..oss.. 包)             │  │ (auth 包，原逻辑迁移)      │
   │                          │  │                            │
   │ - 不调 RoleProfileCatalog│  │ - 调 RoleProfileCatalog    │
   │ - 不调 admin 扩散        │  │ - 执行 admin 扩散          │
   │ - 仅从 OssPermissionCache│  │ - 从 DB RoleProfile 读取   │
   │   读取 authorities        │  │                            │
   └─────────────────────────┘  └────────────────────────────┘
```

### 伪代码

```java
// CompositeUserDetailsService.java（auth 包，唯一 @Service UserDetailsService）
@Service
@RequiredArgsConstructor
public class CompositeUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final OssUserDetailsDelegate ossDelegate;
    private final LocalUserDetailsDelegate localDelegate;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return user.isOssUser()
                ? ossDelegate.build(user)
                : localDelegate.build(user);
    }
}

// OssUserDetailsDelegate.java（..oss.. 包，物理隔离）
@Component
public class OssUserDetailsDelegate {
    private final OssPermissionCache ossPermissionCache;

    public UserDetails build(User user) {
        CacheEntry entry = ossPermissionCache.getEntry(user.getUsername())
                .orElseThrow(() -> new BadCredentialsException("OSS cache miss: " + user.getUsername()));
        // 不调 RoleProfileCatalog、不合并 seed、不执行 admin 扩散
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add(entry.roleCode());
        authorities.add("ROLE_" + RoleProfileCatalog.toAuthorityName(entry.roleCode()));
        authorities.addAll(RoleProfileAdminPermissionFilter.filter(entry.menuPermissions()));
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities.stream().map(SimpleGrantedAuthority::new).toList())
                .disabled(!user.getEnabled())
                .build();
    }
}

// LocalUserDetailsDelegate.java（auth 包，承载原 UserDetailsServiceImpl 的所有逻辑）
@Component
public class LocalUserDetailsDelegate {
    // 原 authoritiesFor / addLegacyRoleAuthority / addMenuPermissionAuthorities /
    // addCatalogFallbackAuthorities / addAdminFallbackAuthorities 全部迁移过来
    // 行为完全不变（含 admin 扩散逻辑）
    public UserDetails build(User user) { ... }
}
```

### `OssAuthResponseBuilder` 与 `DataScopeConfigService` 的关系（v2 修正）

**不新建 `OssAuthResponseBuilder`**，避免与 `DataScopeConfigService` 现有的 `resolveRoleSource()` 形成两套 OSS 权限解析逻辑。

**复用现有分流**：`DataScopeConfigService.getRoleMenuPermissions()` / `getRoleCode()` / `getRoleName()` 三个方法已通过 `resolveRoleSource()` 做了 OSS/本地分流（[L204-L213](../../backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java#L204-L213)）。方案 A 只需：
1. `OssUserDetailsDelegate` **复用** `DataScopeConfigService.resolveRoleSource()` 的缓存/兜底逻辑（不重新实现）
2. 或者在 `DataScopeConfigService` 中新增一个 `getOssAuthorities(User user)` 方法，专门给 `OssUserDetailsDelegate` 调用

**关键约束**：OSS 路径 MUST NOT 调用 `DataScopeConfigService` 的本地 admin 分支（`isLocalSystemAccount()` 为 true 的分支），由 ArchUnit 规则强制。

### `..oss..` 包边界定义（v2 新增 — Open Question 3 回答）

**现状**：仓库不存在统一的 `..oss..` 包。OSS 相关类分散在：
- `com.xiyu.bid.crm.application.OssPermissionCache`
- `com.xiyu.bid.crm.application.OssRoleResolver` / `JobRoleLookupResolver`
- `com.xiyu.bid.integration.organization.*`（OSS API 客户端）
- `com.xiyu.bid.auth.UserDetailsServiceImpl`（OSS 与本地共用）
- `com.xiyu.bid.admin.service.DataScopeConfigService`（OSS 与本地共用）

**方案 A 范围内的包边界**（不重组所有 OSS 类，只新增 OSS 用户构建相关）：

```
com.xiyu.bid.oss.auth/                  ← 新增包
    OssUserDetailsDelegate.java         ← 新增
    OssAuthoritiesBuilder.java           ← 新增（纯函数，从 CacheEntry 构建 authorities）
    (未来可迁入 OssRoleResolver / OssPermissionCache)
```

**ArchUnit 规则**：
```java
@ArchTest
static final ArchRule oss_package_must_not_depend_on_local_admin_branches =
    noClasses().that().resideInAPackage("..oss..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..auth.LocalUserDetailsDelegate..",
            "..admin.service.DataScopeConfigService$LocalSystemAccountBranch..");
// RoleProfileCatalog.seedDefinitions() 是静态方法，ArchUnit 检测方法调用需用 import 检测：
@ArchTest
static final ArchRule oss_package_must_not_call_seedDefinitions =
    noClasses().that().resideInAPackage("..oss..")
        .should().callMethod(RoleProfileCatalog.class, "seedDefinitions");
```

**不在本次范围**：`OssPermissionCache` / `OssRoleResolver` / OSS API 客户端的包重组（改动过大，留给第二层治理）。

---

## Requirements *(mandatory)*

### Functional Requirements

#### 方案 A：代码路径分离（推荐根治）

- **FR-A001**: 新增 `CompositeUserDetailsService`（auth 包，实现 `UserDetailsService`），作为 Spring Security 的**唯一** `UserDetailsService` Bean。根据 `user.isOssUser()` 分发到 OSS 或本地 delegate。
- **FR-A002**: 新增 `OssUserDetailsDelegate`（`..oss..` 包），独立负责 OSS 用户的 `UserDetails` 构建。**复用** `DataScopeConfigService.resolveRoleSource()` 的缓存/兜底逻辑，不重新实现 OSS 权限解析。
- **FR-A003**: 新增 `LocalUserDetailsDelegate`（auth 包），承载原 `UserDetailsServiceImpl` 的所有逻辑（含 admin 扩散），行为完全不变。
- **FR-A004**: `OssUserDetailsDelegate` 构建 authorities 时，MUST 且 ONLY 包含：OSS `getUserPermission` 返回的菜单 codes 映射出的内部权限键 + 自身 `ROLE_<CODE>` authority。
- **FR-A005**: `OssUserDetailsDelegate` MUST NOT 调用 `RoleProfileCatalog.seedDefinitions()`、MUST NOT 调用 `definitionForCode()`（避免 null/未注册 fallback 到 admin）、MUST NOT 执行 admin 扩散逻辑。
- **FR-A006**: 本地用户继续走 `LocalUserDetailsDelegate`，行为完全不变（含 admin 扩散逻辑、`seedDefinitions()` 合并、`system.admin`/`warehouse.manage` 补发）。
- **FR-A007**: 新增 ArchUnit 规则：`..oss..` 包下的类 MUST NOT 调用 `RoleProfileCatalog.seedDefinitions()` 或 `definitionForCode()`，MUST NOT 依赖 `LocalUserDetailsDelegate`。
- **FR-A008** *(原 FR-A007，重编号并修正为分步走)*: `User.getRoleCode()` 的 `"manager"` fallback 删除分两步走：
  1. **Phase 1（本 spec 范围）**：审计并迁移全部 13 处直调点到 `EffectiveRoleResolver` 或 `DbRoleSnapshotResolver`（详见 §迁移计划）。
  2. **Phase 2（独立 spec）**：确认无遗漏后，删除 `"manager"` fallback 改抛 `IllegalStateException`。
- **FR-A009** *(原 FR-A008)*: 选人接口（`UserSearchService`、`AssignmentCandidatePolicy`）MUST 通过 `DbRoleSnapshotResolver` 获取角色码，禁止直调 `user.getRoleCode()`。

#### 方案 B：强约束门禁（最小代价）

- **FR-B001**: 扩展 `scripts/check-rolecode-direct-calls.mjs`，禁止任何 `user.getRoleCode()` 直调（含 `UserSearchService`、`AssignmentCandidatePolicy`），豁免白名单仅限已记录场景。
- **FR-B002**: 新增 ArchUnit 规则：`UserDetailsServiceImpl`（或 `CompositeUserDetailsService`）中所有 `roleCode.equals("admin")` 分支必须前置 `!isOssUser` 守卫（通过 AST 或正则匹配检测）。
- **FR-B003**: 新增测试：每个 OSS 角色（bid-Team / bid-TeamLeader / bid-projectLeader / bid-administration / bid-otherDept / admin）必须有一个"权限不扩散"测试用例，断言 OSS 用户 authorities 不包含 `all`、不包含其他角色的 `menuPermissions`。
- **FR-B004**: 新增测试：选人接口返回的 `roleCode` 必须与 DB `role_profile` 一致，不读 OSS 缓存。

#### 方案 C：消除 "all" 短路 + admin 扩散（中间态，借鉴用于方案 A）

- **FR-C001** *(修正：admin seed 改为计算生成)*: 本地 admin 的 `seedDefinitions()` 中的 `List.of("all")` 改为**计算生成** `allPermissionsUnion()`：
  ```java
  // RoleProfileCatalog.java
  private static List<String> allPermissionsUnion() {
      return Stream.concat(
          seedDefinitions().stream()
              .filter(d -> !d.code().equals(ADMIN_CODE))
              .flatMap(d -> d.menuPermissions().stream()),
          Stream.of("all", SYSTEM_ADMIN_PERMISSION, WAREHOUSE_MANAGE_PERMISSION)
      ).distinct().toList();
  }
  ```
  降低每次新增角色/权限时手动同步 admin seed 的遗漏风险。
- **FR-C002**: 删除前端 `hasPermission` 的 "all" 短路逻辑（**评估后改为：不删除**，只在后端保证 OSS 用户拿不到 `"all"`，前端短路保留作为本地 admin 的最后一公里优化）。
- **FR-C003**: ~~本地 admin 通过显式 seed 拿到全权限~~ 改为：admin seed 由 `allPermissionsUnion()` 计算生成。
- **FR-C004**: 测试：本地 admin 仍能访问所有菜单（通过 `allPermissionsUnion()`），OSS 用户行为与 spec 032 一致。

### Key Entities *(include if feature involves data)*

- **User**: 用户实体，包含 `username`、`roleCode`、`isOssUser` 标识。方案 A Phase 2 会修改 `getRoleCode()` 的 fallback 行为。
- **RoleProfileCatalog**: 角色配置目录。方案 A 中 OSS 路径不再依赖它（包括 `definitionForCode()`）；方案 C 中 admin seed 改为 `allPermissionsUnion()` 计算生成。
- **UserDetailsServiceImpl**: Spring Security `UserDetails` 构建服务。方案 A 中**重构为 `CompositeUserDetailsServiceImpl`**（Delegate 模式），不删除原类，而是将其逻辑迁移到 `LocalUserDetailsDelegate`。
- **DataScopeConfigService**: 数据范围配置服务。方案 A 中 OSS delegate **复用**其 `resolveRoleSource()` 逻辑，不重新实现。
- **OssPermissionCache**: OSS 用户权限缓存。所有方案都不修改其结构。
- **EffectiveRoleResolver**: 角色码解析统一入口。所有方案都不修改其行为。
- **DbRoleSnapshotResolver**: 选人业务用的 DB 快照入口。方案 A 强制选人接口走它；方案 B 通过门禁强制。
- **UserSearchService / AssignmentCandidatePolicy**: 选人接口。方案 A/B 都要求走 `DbRoleSnapshotResolver`，禁止直调 `user.getRoleCode()`。

---

## 迁移计划（v2 新增）

### Phase 1：FR-A008 直调点迁移（方案 A 前置条件）

全仓审计 `user.getRoleCode()` 直调点共 **13 处**，按迁移难度分三组：

| 组 | 文件 | 行号 | 当前 SAFE 豁免理由 | 迁移目标 |
|----|------|------|-------------------|---------|
| **G1 — 选人接口**（本 spec 范围） | `UserSearchService.java` | L48, L85 | 无（未加 SAFE 注释） | `DbRoleSnapshotResolver.resolveRoleCode(u)` |
| | `AssignmentCandidatePolicy.java` | L73, L76, L94 | "纯核心无 Resolver 引用" | **重构签名**：caller 传入已解析的 `List<AssignmentCandidate>`（已解析字段），纯核心不再接收 `List<User>` |
| **G2 — 兼容性 fallback**（本 spec 范围，兼容保留） | `AuthResponse.java` | L51, L58, L65, L81 | "登录响应 fallback 构造器" | 保留 SAFE 豁免（DTO 兼容层，不参与鉴权） |
| | `DataScopeConfigService.java` | L161, L196, L251 | "本地 admin 兜底" | 保留 SAFE 豁免（已通过 `isLocalSystemAccount()` 隔离） |
| **G3 — 诊断/展示**（本 spec 范围，兼容保留） | `DashboardAnalyticsDrillDownContentAssemblerService.java` | L92, L210 | "分析下钻面板展示字段" | 保留 SAFE 豁免（前端展示，不参与鉴权） |
| | `EffectiveRoleResolver.java` | L65 | "policy 决策" | 保留 SAFE 豁免（已是统一入口本身） |
| | `DbRoleSnapshotResolver.java` | L40 | "本类是封装入口" | 保留 SAFE 豁免（封装入口） |
| | `CurrentUserResolver.java` | L111 | 注释中 | 保留 SAFE 豁免（已在事务内解析） |

**G1 迁移影响**：
- `UserSearchService`：Spring Bean，可直接注入 `DbRoleSnapshotResolver`
- `AssignmentCandidatePolicy`：**Pure Core**（无 Spring 依赖），不能直接注入。需要重构为 caller 传入已解析字段的 DTO，是中等改动。建议在 US-3 独立 sub-task 中处理。

### Phase 2：删除 `"manager"` fallback（独立 spec）

Phase 1 完成且确认无遗漏后，在独立 spec 中删除 `User.getRoleCode()` 的 `"manager"` fallback，改抛 `IllegalStateException`。

---

## 回滚方案（v2 新增）

### 触发条件

- 方案 A 落地后，本地 admin 登录失败或权限异常
- OSS 用户登录失败率上升（超过基线 1%）
- ArchUnit 规则误拦合法代码

### 回滚步骤

1. **Git 层**：`git revert` 方案 A 的 commit（保留 `CompositeUserDetailsServiceImpl` + 两个 delegate 类的删除）
2. **Bean 层**：恢复 `UserDetailsServiceImpl` 作为唯一 `UserDetailsService` Bean（删除 `CompositeUserDetailsServiceImpl` 的 `@Service` 注解）
3. **测试层**：方案 B 的测试（FR-B003/B004）**保留**，不回滚 —— 这些测试在回滚后仍能验证 spec 032 的止血逻辑
4. **门禁层**：ArchUnit 规则可保留（回滚后 OSS 类不存在，规则不会误触发）；`check-rolecode-direct-calls.mjs` 扩展保留

### 回滚验证

- `mvn test -Dtest=ArchitectureTest` 通过（无 OSS 类，ArchUnit 规则不触发）
- 本地 admin 登录 + 菜单渲染回归测试通过
- OSS 用户（03063/06234）登录 + 菜单权限测试通过（spec 032 止血逻辑仍生效）

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: OSS 用户登录后，前端只渲染 OSS 返回的菜单 codes 对应的菜单项（**引用** spec 032 的 SC-001，不重复定义；本 spec 的验证点是"代码路径分离后 SC-001 仍通过"）。
- **SC-002**: 本地 `admin` 账号登录后，行为与修复前完全一致（回归测试验证）。
- **SC-003**: 选人接口返回的 `roleCode` 与 DB `role_profile` 一致，不读 OSS 缓存（单元测试断言）。
- **SC-004**: ArchUnit 测试覆盖 OSS/本地代码路径分离（方案 A）或 admin 分支守卫（方案 B）。
- **SC-005**: `user.getRoleCode()` 直调检测扩展覆盖选人接口，pre-push gate 拦截新增违规。
- **SC-006**: 每个 OSS 角色有"权限不扩散"测试用例，CI 强制执行。
- **SC-007**: 未来新增"按角色判断"分支时，要么对 OSS 用户不触发（方案 A 代码路径分离），要么有显式守卫（方案 B/C）。

---

## Assumptions

- 现有 `OssPermissionCache` 的 `isOssUser` 判断逻辑是准确的，本次不修改判断逻辑。
- 现有 `OssLoginFlowService` 的 OSS 4 步 API 调用链路是稳定的，本次不修改。
- 现有 `EffectiveRoleResolver` 的角色码解析强约束（CO-373 治理）行为不变。
- `RoleProfileCatalog.seedDefinitions()` 的 `menuPermissions` 是本地角色权限配置的单一真相源，方案 A 中 OSS 路径不再依赖它；方案 C 中 admin seed 改为 `allPermissionsUnion()` 计算生成。
- 177 处 `@PreAuthorize` hasAnyRole 迁移（specs/024）独立推进，本次不混入。
- `JobRoleLookupResolver` 的 "投标系统管理员" → `admin` 映射在方案 A 范围内不动（第二层治理范围）。
- **并发登录场景**：OSS 端权限变更后，已登录 OSS 用户需重新登录才生效（JWT 不热更新），本次不处理 token 黑名单。

## Next Step（v2 修正 — 原 Assumption #6 移至此处）

- 方案选择（A/B/C 或组合策略）需经过 speckit-clarify 阶段与团队确认。**推荐组合策略：B（立即）→ A（中期）+ 借鉴 C 的 admin seed 计算生成**（见 §推荐策略）。
- US-3 是否与 US-1/2 同批交付，需在 clarify 阶段确认（建议拆为独立 sub-task）。
- `AssignmentCandidatePolicy` 的 Pure Core 重构方案（caller 传入已解析字段）需在 plan 阶段细化。

## Open Questions（v2 — 部分已回答）

1. ~~**方案选择**~~ → **已回答**：推荐组合策略 B → A + 借鉴 C（见 §推荐策略）。需团队在 clarify 阶段确认。
2. ~~**`User.getRoleCode()` fallback 删除范围**~~ → **已回答**：分两步走，Phase 1 迁移 13 处直调点（详见 §迁移计划），Phase 2 删除 fallback（独立 spec）。
3. ~~**ArchUnit 规则的精确边界**~~ → **已回答**：新增 `com.xiyu.bid.oss.auth` 包，只迁入 OSS 用户构建相关类；ArchUnit 检测方法调用（`seedDefinitions()` / `definitionForCode()`）+ 包依赖（详见 §`..oss..` 包边界定义）。
4. ~~**方案 C 的 admin 显式 seed**~~ → **已回答**：admin seed 改为 `allPermissionsUnion()` 计算生成（flatMap 取并集 + 系统级权限），不手动列举。
5. **(v2 新增)** `AssignmentCandidatePolicy` 的 Pure Core 重构是否在本 spec 范围？还是拆为独立 spec？（建议拆出，US-3 作为独立 sub-task）

---

## Related Specs

- `specs/032-fix-oss-permission-diffusion/` — 第一层止血（isOssUser 守卫 + 前端 hasPermission 守卫），本 spec 是其根治方向。
- `specs/024-preauthorize-unification/` — 177 处 @PreAuthorize hasAnyRole 双轨制技术债，独立推进。
- `specs/004-unify-rolecode-resolution/` — CO-373 角色码解析统一入口，本 spec 在其基础上进一步分离代码路径。
