# Production Risk Review 报告 — PR !2178

> **审计日期**：2026-07-21
> **审计员**：codex agent
> **审计框架**：Production Risk Review v2.0（9 阶段）
> **审计对象**：PR !2178（分支 `agent/codex/fix-datascope-oss-role-inconsistency`）
> **PR 描述的修复目标**：消灭覃超颖（OSS username=09118，bid-SystemAdmin）访问 https://winbid.ehsy.com/bidding/60 报 403 的 bug
> **最终裁决**：❌ FAIL — 禁止上线
> **关联 PR**：!2178（已驳回）
> **后续修复 PR**：!2179（分支 `agent/codex/fix-bid-systemadmin-403-real-root-cause`）

---

## 第一阶段：改动理解

### PR !2178 改动范围

- **commits**：`cefe2cf45`（fix）+ `f0e017ba5`（docs）
- **files changed**：8 files, 417 insertions, 62 deletions
- **修改文件清单**：
  - `backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRoleResult.java`（新增 `OSS_ADMIN_REJECTED` Source 枚举值）
  - `backend/src/main/java/com/xiyu/bid/security/domain/EffectiveRolePolicy.java`（纯核心层添加 OSS admin 拦截分支）
  - `backend/src/main/java/com/xiyu/bid/security/EffectiveRoleResolver.java`（外壳层添加 warn 日志）
  - `backend/src/main/java/com/xiyu/bid/admin/service/RoleProfileAccessRuleResolver.java`（新建，从 DataScopeConfigService 拆分）
  - `backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java`（getAccessProfile 改走 EffectiveRoleResolver）
  - `backend/src/test/java/com/xiyu/bid/admin/service/DataScopeConfigServiceTest.java`（+2 覃超颖 case 回归测试）
  - `backend/src/test/java/com/xiyu/bid/security/domain/EffectiveRolePolicyTest.java`（+4 OSS_ADMIN_REJECTED 测试）
  - `docs/lessons/lessons-learned.md`（追加 §78）

### 改动理解表

| 项目 | 内容 |
| --- | --- |
| 修改目的 | 消灭覃超颖（OSS username=09118，bid-SystemAdmin）访问 /bidding/60 报 403 的 bug |
| 修改模块 | security（EffectiveRolePolicy / EffectiveRoleResolver / EffectiveRoleResult）、admin/service（DataScopeConfigService + 新拆分 RoleProfileAccessRuleResolver） |
| 修改流程 | OSS 缓存 admin 时纯核心层 fail-closed 拦截 + DataScopeConfigService 改走 EffectiveRoleResolver |
| 修改数据库 | 无 |
| 修改缓存 | 不直接修改 Redis key，仅修改读取侧解析逻辑 |
| 修改接口 | 无（方法签名兼容） |
| 修改配置 | 无 |
| 修改权限 | 间接影响 OSS 用户缓存 admin 时的角色码解析路径 |
| 修改第三方 | 无 |

### 调用链（覃超颖访问 /bidding/60）

```
Browser → Nginx → Spring Security Filter Chain
  → @PreAuthorize hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')  ← 关键检查点 1
  → TenderController.getTenderById(60)
  → TenderQueryService.getTenderById
  → TenderProjectAccessGuard.assertCanAccessTender(tender)
    → if linkedProjects nonEmpty: projectAccessScopeService.assertCurrentUserCanAccessProject
        → if hasAdminAccess(authentication): return   ← 关键检查点 2（hasAdminAccess 检查 ROLE_ADMIN authority）
    → if linkedProjects empty: resolveDataScope(user)
        → dataScopeConfigService.getAccessProfile(user)  ← PR !2178 修改点
        → if dataScope != "all": isSelfVisibleTender → false → throw AccessDeniedException
  → ExceptionMessageSanitizer → "权限不足，无法访问该资源"
```

---

## 第二阶段：影响分析（Dependency Expansion）

### EffectiveRoleResolver 调用方（已通过 grep 确认）

production 调用方：
- `JwtAuthenticationFilter.java`（line 37, 49, 64）— JWT 认证过滤器
- `CurrentUserResolver.java`（line 32）— 当前用户解析
- `TraceFilter.java`（line 89, 99）— MDC trace 填充
- `SettingsService.java`（line 29, 37, 45, 69）— 设置服务
- `RoleProfileService.java`（line 38, 174）— 角色配置服务
- `ProjectAccessScopeService.java`（line 56, 155, 264）— 项目访问范围服务
- `ProjectDraftingService.java`（line 65）— 项目起草服务
- `WorkbenchProjectTodoQueryService.java`（line 66）— 工作台待办
- `WorkbenchResourcePendingQueryService.java`（line 54）— 工作台资源待办
- `SentryConfig.java`（line 54, 63）— Sentry 配置
- `DataScopeConfigService.java`（本次新增）

### getAccessProfile 调用方

- `TenderProjectAccessGuard.resolveDataScope`（line 172）— 覃超颖 bug 的抛 403 路径
- `DataScopeAspect`（line 62）— 数据范围切面
- `ProjectAccessScopeService`（line 67/132/278）— 项目访问范围

### UserDetailsServiceImpl 颁发 authority 路径（PR !2178 未修改）

- `authoritiesFor(user)` → `resolveRoleSource(user)` → 直接读 OSS 缓存（**不走 EffectiveRoleResolver**）
- 颁发的 authority 由 roleCode 决定：
  - `admin` → `{"admin", "ROLE_ADMIN"}`
  - `bid-SystemAdmin` → `{"bid-SystemAdmin", "ROLE_BID_SYSTEMADMIN"}`
- 关键代码位置：`UserDetailsServiceImpl.java:122-151` `resolveRoleSource`

### hasAdminAccess 短路逻辑（PR !2178 未修改）

- `ProjectAccessScopeService.hasAdminAccess(Authentication)`：检查 Authentication 中是否含 `ROLE_ADMIN` authority
- 关键代码位置：`ProjectAccessScopeService.java:184-186` `if (hasAdminAccess(authentication)) { return; }`
- **不检查 user.isOssUser()**：OSS 缓存 admin 的用户会绕过 dataScope 检查

---

## 第三阶段：Bug 推演（3 条故障链）

### 故障链 1（核心）：覃超颖 bid-SystemAdmin 角色访问 /bidding/60

**前提**：覃超颖重新登录，OSS 端正确下发 roleCode=bid-SystemAdmin（清缓存后预期场景）

```
覃超颖重新登录 → OSS 端正确下发 roleCode=bid-SystemAdmin
  → UserDetailsServiceImpl.ossAuthorities 颁发 authority
    → LoginRoleWhitelist.isAllowed("bid-SystemAdmin") = true
    → applyRoleCodeAuthorities(authorities, "bid-SystemAdmin", skipLegacyCompat=true)
    → authorities = {"bid-SystemAdmin", "ROLE_BID_SYSTEMADMIN"}
  → @PreAuthorize hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')
    → 检查 ROLE_ADMIN/ROLE_MANAGER/ROLE_BID_TEAMLEADER/ROLE_BIDADMIN/ROLE_BID_PROJECTLEADER/ROLE_BID_TEAM
    → ROLE_BID_SYSTEMADMIN 不在列表
    → 拒绝 → 403
```

**证实证据**：
- [TenderController.java:86](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java#L86) `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")` — 列表不含 BID_SYSTEMADMIN

**结论**：PR !2178 完全不能修复此场景。EffectiveRolePolicy 改动只影响 EffectiveRoleResolver 返回值，不影响 UserDetailsServiceImpl 颁发的 authority。Spring Security @PreAuthorize 在 Controller 入口就拒绝了，根本走不到 EffectiveRoleResolver。

### 故障链 2：覃超颖 OSS 缓存仍为 admin（OSS 端未修正）+ tender 60 已关联 project

**前提**：OSS 端未修正，覃超颖重新登录后缓存仍被写成 admin；tender 60 已关联 project

```
覃超颖登录 → OSS 端仍误配 admin → OSS 缓存 admin
  → UserDetailsServiceImpl 颁发 authority
    → applyRoleCodeAuthorities(authorities, "admin", skipLegacyCompat=true)
    → authorities = {"admin", "ROLE_ADMIN"}
  → @PreAuthorize hasAnyRole('ADMIN',...) 通过（ADMIN 在列表）
  → TenderProjectAccessGuard.assertCanAccessTender
    → linkedProjects(tender) nonEmpty
    → assertCurrentUserCanAccessProject(projectId)
      → hasAdminAccess(authentication) = true（因为有 ROLE_ADMIN）
      → return; (短路，不抛 403)
  → 通过 → 覃超颖能访问（绕过 dataScope 检查）
```

**证实证据**：
- [ProjectAccessScopeService.java:184-186](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java#L184-L186) `if (hasAdminAccess(authentication)) { return; }`
- hasAdminAccess 不检查 user.isOssUser()

**结论**：PR !2178 不能修复此场景（hasAdminAccess 短路），但也不能引入新 bug（修复前后行为一致 — 都通过）。**但是这违反了 PR 的设计目标"防止 OSS admin 越权"**，因为 hasAdminAccess 短路让 OSS admin 用户绕过了 dataScope 检查。

### 故障链 3：覃超颖 OSS 缓存 admin + tender 60 未关联 project（lessons-learned §78 描述的场景）

**前提**：OSS 缓存仍为 admin；tender 60 未关联 project

```
覃超颖登录 → OSS 缓存 admin → 颁发 ROLE_ADMIN
  → @PreAuthorize 通过
  → TenderProjectAccessGuard.assertCanAccessTender
    → linkedProjects(tender) empty
    → resolveDataScope(user)
      → dataScopeConfigService.getAccessProfile(user)
        → 修复前：读 DB roleProfile (bid-otherDept) → dataScope=self
        → 修复后：走 EffectiveRoleResolver → OSS admin → fail-closed null → roleRule=self → dataScope=self
      → 两者返回 dataScope=self（一致）
    → "all" != "self" → 进入 isSelfVisibleTender 检查
    → 覃超颖不是 tender 60 的 creator/biddingPerson/projectManager，也不是最新分配记录的 assignee
    → 返回 false
    → throw AccessDeniedException("权限不足，无法访问该标讯")
  → 403
```

**证实证据**：
- [TenderProjectAccessGuard.java:47-55](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/service/TenderProjectAccessGuard.java#L47-L55) 未关联项目走 dataScope 分支
- [TenderProjectAccessGuard.java:172](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/service/TenderProjectAccessGuard.java#L172) `dataScopeConfigService.getAccessProfile(user)`
- [RoleProfileAccessRuleResolver.java:51-55](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/admin/service/RoleProfileAccessRuleResolver.java#L51-L55) roleCode=null 返回 self 兜底

**结论**：PR !2178 **完全不能消灭此 403**。修复前后 dataScope 都是 self，isSelfVisibleTender 检查结果一致，都抛 403。lessons-learned §78 描述的"修复后 TenderProjectAccessGuard 会通过 EffectiveRoleResolver → fail-closed null → 保守 self 兜底，确保不会越权访问"是错误的——这里要的不是"防止越权"，是"应该让覃超颖能访问"。

---

## 第四阶段：分类检查（固定 Checklist）

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 空值 | PASS | EffectiveRolePolicy.java:53-58 显式判空 `cachedRoleCode != null && cachedRoleCode.isPresent()`；RoleProfileAccessRuleResolver.java:52 roleCode=null 返回 self 兜底 |
| 集合 | PASS | 无集合遍历新增；RoleProfileAccessRuleResolver 用 Collections.emptyList() 不可变 |
| 并发 | PASS | EffectiveRolePolicy 是 final 类 + 静态方法，无状态；RoleProfileAccessRuleResolver 字段 final |
| 数据库 | PASS | 无 SQL 改动；DataScopeConfigService 仍是 @Transactional(readOnly=true) |
| Redis | PASS | 不直接读写 Redis；通过 RoleCodeCachePort 抽象端口 |
| MQ/异步 | N/A | 无 MQ 改动 |
| API 兼容 | PASS | DataScopeConfigService 公共方法签名不变；构造器加参数但 Spring 注入兼容 |
| JSON | N/A | 无序列化改动 |
| 时间 | N/A | 无时间处理 |
| IO | N/A | 无 IO 操作 |
| 前端 | N/A | 无前端改动 |
| 存量数据 | PASS | 无 schema 变更 |
| **测试与验证证据** | **FAIL** | EffectiveRolePolicyTest +4 case / DataScopeConfigServiceTest +2 case 都是基于错误根因假设的回归测试；**缺少最关键的端到端验证：覃超颖重新登录后访问 /bidding/60 是否能通过** |

---

## 第五阶段：业务回归

### 标讯详情访问链路（覃超颖 bid-SystemAdmin）

| 环节 | 修复前 | 修复后 | 是否消灭 bug |
| --- | --- | --- | --- |
| @PreAuthorize hasAnyRole | 不含 BID_SYSTEMADMIN → 403 | 不含 BID_SYSTEMADMIN → 403 | ❌ 未消灭 |
| hasAdminAccess 短路（OSS admin + 关联 project） | 通过 | 通过 | ❌ 无差异 |
| dataScope（OSS admin + 未关联 project） | self | self | ❌ 无差异 |
| isSelfVisibleTender | false → 403 | false → 403 | ❌ 未消灭 |

**核心业务链路完全未修复**。

### 通知接收人过滤链路（canAccessProject）

| 环节 | 修复前 | 修复后 |
| --- | --- | --- |
| canAccessProjectInternal 第 274 行 admin 短路 | OSS admin → true | OSS admin → null → 跳过短路 |
| getAllowedProjectIds 第 64 行 admin 短路 | 返回 List.of() | 跳过 → 收集用户项目 |

此路径有行为变化，但与覃超颖 403 bug 无关。

---

## 第六阶段：极端场景

### 场景 1：Redis 挂掉
- EffectiveRoleResolver 调用 RoleCodeCachePort.getRoleCode → 抛异常或返回 empty
- OSS 用户 cache miss → CACHE_MISS_FAIL_CLOSED → null
- 与修复前行为一致（PR 不改变此路径）

### 场景 2：高并发
- EffectiveRolePolicy 是无状态纯核心，并发安全
- DataScopeConfigService 仍是 @Transactional(readOnly=true)

### 场景 3：覃超颖重复登录
- 修复后 OSS 缓存 admin 仍会被 fail-closed 拦截
- 但 @PreAuthorize 仍用 UserDetailsServiceImpl 颁发的 ROLE_ADMIN
- 形成新的"通过 @PreAuthorize 但 dataScope=self"的不一致状态（如果 tender 未关联 project 仍 403）

---

## 第七阶段：隐藏 Bug 排查

### 隐藏 Bug 1：UserDetailsServiceImpl 与 EffectiveRoleResolver 数据源仍不一致
- **证据**：[UserDetailsServiceImpl.java:122-151](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java#L122-L151) `resolveRoleSource` 直接读 OSS 缓存，不走 EffectiveRoleResolver
- **后果**：OSS 缓存 admin 时，UserDetailsServiceImpl 颁发 ROLE_ADMIN（绕过 @PreAuthorize），但 EffectiveRoleResolver 返回 null（fail-closed）。两条路径数据源不一致，违反 lessons-learned §78 "角色码解析必须走统一入口"原则
- **PR !2178 未修复此问题**，反而引入新的不一致

### 隐藏 Bug 2：hasAdminAccess 短路让 OSS admin 绕过 dataScope
- **证据**：[ProjectAccessScopeService.java:184-186](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java#L184-L186)
- **后果**：OSS 缓存 admin → UserDetailsServiceImpl 颁发 ROLE_ADMIN → hasAdminAccess 短路 → 越权访问任意已关联 project 的 tender
- **PR !2178 完全未触及此路径**

### 隐藏 Bug 3：TenderController @PreAuthorize 缺 BID_SYSTEMADMIN
- **证据**：[TenderController.java:54,86,98,108,118,126,139](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java#L86)
- **后果**：所有 bid-SystemAdmin 角色用户（包括正确的 OSS 配置）都被 @PreAuthorize 拦截 403
- **PR !2178 完全未触及此路径**

### 隐藏 Bug 4：lessons-learned §78 根因分析错误
- **证据**：lessons-learned.md §78 "TenderProjectAccessGuard.checkAccess 抛出 '权限不足，无法访问该标讯'（DataScopeConfigService.getAccessProfile 读 DB roleProfile 返回 bid-otherDept/self，无标讯 60 访问权限）"
- **实际**：代码中 TenderProjectAccessGuard 没有名为 `checkAccess` 的方法，正确方法是 `assertCanAccessTender`。而且如果 tender 关联了 project，会走 `assertCurrentUserCanAccessProject` → `hasAdminAccess` 短路，不会调用 `getAccessProfile`。只有 tender **未关联 project** 时才走 `resolveDataScope` → `getAccessProfile`
- **后果**：基于错误的根因分析设计修复方案，导致修复无效

---

## 部署与回滚评估

| 项 | 结论 | 证据 |
| --- | --- | --- |
| 回滚可行性 | PASS | 无 DB schema 变更；回滚代码即可 |
| 部署顺序 | PASS | 仅后端改动，无前端依赖 |
| 配置变更清单 | PASS | 无新增环境变量 |
| 静态资源路径 | N/A | 无前端改动 |
| 客户环境差异 | PASS | 无 Flyway 改动 |

---

## 发现的问题

- **[P0]** [TenderController.java:86](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java#L86) — `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")` 不含 `BID_SYSTEMADMIN`，导致 bid-SystemAdmin 角色用户被 Spring Security 在 Controller 入口直接拒绝。这是覃超颖 403 bug 的真正根因之一，PR !2178 未触及。修复建议：在所有相关 @PreAuthorize 列表中加入 `BID_SYSTEMADMIN`（与 RoleProfileCatalog.GLOBAL_ACCESS_ROLES 对齐）
- **[P0]** [UserDetailsServiceImpl.java:122-151](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java#L122-L151) — `resolveRoleSource` 直接读 OSS 缓存，不走 EffectiveRoleResolver。导致 OSS 缓存 admin 时仍颁发 ROLE_ADMIN，绕过 PR !2178 的 fail-closed 拦截。修复建议：UserDetailsServiceImpl 改走 EffectiveRoleResolver，或在颁发 ROLE_ADMIN 前先调用 EffectiveRolePolicy.decide 校验
- **[P0]** [ProjectAccessScopeService.java:184-186](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java#L184-L186) — `hasAdminAccess` 仅检查 Spring Security authorities，不检查 user.isOssUser()。导致 OSS 缓存 admin 的用户绕过 dataScope 检查越权访问。修复建议：hasAdminAccess 增加 `&& !user.isOssUser()` 条件，或改用 EffectiveRoleResolver 判定
- **[P1]** lessons-learned.md §78 根因分析错误。lessons §78 描述"TenderProjectAccessGuard.checkAccess 抛出"，但代码中没有 checkAccess 方法。实际 403 路径是 @PreAuthorize（故障链 1）或 resolveDataScope（故障链 3）。修复建议：在 §78 修正根因描述，补充真正的 3 条故障链
- **[P1]** PR !2178 测试覆盖不足以证明 bug 消灭。EffectiveRolePolicyTest +4 case 只覆盖纯核心决策，未覆盖端到端业务路径（覃超颖登录 → 访问 /bidding/60 → 200）。修复建议：补 TenderProjectAccessGuardTest 覆盖 OSS 缓存 admin + 未关联 project 场景，断言修复后行为（应能让 bid-SystemAdmin 角色访问）
- **[P2]** [DataScopeConfigService.java:125-149](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java#L125-L149) — getAccessProfile 现在调用 `effectiveRoleResolver.resolveRoleCode(user)`，但 `getAllowedProjectIds`（ProjectAccessScopeService.java:63）也调用一次，同一请求重复读 Redis 缓存。可优化为请求级缓存

---

## 最终裁决

# ❌ FAIL — 禁止上线

**一句话理由**：PR !2178 的修复逻辑基于错误的根因分析，**完全无法消灭覃超颖的 403 bug**——真正的根因是 `TenderController @PreAuthorize` 不含 `BID_SYSTEMADMIN` 角色码（故障链 1），以及 `UserDetailsServiceImpl` 仍直接读 OSS 缓存颁发 `ROLE_ADMIN`（故障链 2/3），PR !2178 都未触及。

### 关键证据
1. **故障链 1（最致命）**：覃超颖 OSS 缓存正确为 bid-SystemAdmin 时，UserDetailsServiceImpl 颁发 `ROLE_BID_SYSTEMADMIN`，但 [TenderController.java:86](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java#L86) 的 `@PreAuthorize hasAnyRole` 列表不含 `BID_SYSTEMADMIN`，直接 403。PR !2178 改的 EffectiveRoleResolver 在 Controller 入口被拒后根本不会执行。
2. **故障链 3**：即使 OSS 缓存仍为 admin，tender 60 未关联 project 时，[TenderProjectAccessGuard.java:47-55](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/service/TenderProjectAccessGuard.java#L47-L55) 走 `resolveDataScope` → `getAccessProfile`，修复前 DB roleProfile=bid-otherDept/self，修复后 EffectiveRoleResolver fail-closed null → roleRule=self，**dataScope 修复前后都是 self，行为完全一致，403 未消灭**。
3. **故障链 2**：如果 tender 60 关联了 project，[ProjectAccessScopeService.java:184-186](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java#L184-L186) 的 `hasAdminAccess` 检查 Spring Security authorities 中的 `ROLE_ADMIN` 短路返回，PR !2178 改的 EffectiveRoleResolver 根本不会被调用，OSS admin 用户仍然越权。

---

## 修复路径建议（按优先级，第二次修正）

> **第一次修正的错误**：审计报告原本建议"P0 立即：修正 OSS 端配置，把覃超颖的 admin 改回 bid-SystemAdmin（运营操作，无需代码）"——这是基于错误认知的判断。
>
> **用户指正后的正确认知**：OSS 端没有属于我们投标系统的 admin 角色，admin 是我们系统独有的本地超级管理员账户。OSS 是多系统共用的角色管理平台，返回的 sysRoleList 中混合了多系统角色。覃超颖在 OSS 端的配置本来就是正确的 bid-SystemAdmin，无需运营操作。

1. **P0 代码（核心根因）**：`RoleProfileCatalog` 区分"本地角色"与"OSS 角色"——新增 `OSS_ELIGIBLE_CODES` 集合（7 个 bid-* 角色码，不含 admin），`canonicalCode` 在 OSS 解析路径中只识别这 7 个角色码。或新增 `canonicalOssCode(String)` 方法，对 admin 返回 null。这样 OSS 返回的 sysRoleList 中其他系统的 admin 会被跳过，继续找下一个 bid-* 角色码（如 bid-SystemAdmin）。
2. **P0 代码**：在 [TenderController.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java) 所有 `@PreAuthorize hasAnyRole` 列表加入 `BID_SYSTEMADMIN`（与 RoleProfileCatalog.GLOBAL_ACCESS_ROLES 对齐）
3. **P0 代码**：改造 [UserDetailsServiceImpl](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java) 走 EffectiveRoleResolver，或在颁发 `ROLE_ADMIN` 前调用 `EffectiveRolePolicy.decide` 校验，统一数据源
4. **P1 代码**：改造 [ProjectAccessScopeService.hasAdminAccess](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java#L208-L216) 增加 `!user.isOssUser()` 条件，防止 OSS admin 越权
5. **P1 文档**：修正 lessons-learned.md §78 根因分析（已完成第二次修正）

---

## 上线后验证清单（不适用 — FAIL）

N/A

---

## 未审计声明

全部阶段已执行。本次审计基于 PR !2178 完整 diff（2 commits, 8 files, 417 insertions, 62 deletions）+ 8 个关键文件全文阅读（EffectiveRolePolicy / EffectiveRoleResolver / EffectiveRoleResult / RoleProfileAccessRuleResolver / DataScopeConfigService / UserDetailsServiceImpl / ProjectAccessScopeService / TenderProjectAccessGuard）+ TenderController @PreAuthorize 全量扫描。

---

## 审计方法学说明

### 本次审计遵循的核心原则

1. **默认存在 Bug**：不假设 PR 作者正确，从"PR 修不了 bug"的角度反向推演
2. **不停留在修改代码本身**：扩展分析 UserDetailsServiceImpl（颁发 authority）、TenderController（@PreAuthorize）、ProjectAccessScopeService（hasAdminAccess 短路）、TenderProjectAccessGuard（403 抛出点）等所有依赖路径
3. **任何"没有问题"都必须提供代码证据**（文件:行号）
4. **模拟真实生产环境**：覆盖 OSS 配置正确/错误、tender 关联/未关联 project 等多场景
5. **只有当所有风险都被证伪后，才能给出 PASS；否则一律 FAIL**

### 关键审计动作

- ✅ Grep 全代码库找 `EffectiveRoleResolver` / `getAccessProfile` / `EffectiveRolePolicy.decide` / `isOssUser` 全部调用方
- ✅ 完整阅读 EffectiveRolePolicy.java（71 行）、EffectiveRoleResolver.java（109 行）确认纯核心逻辑
- ✅ 阅读 UserDetailsServiceImpl.java 确认 OSS 缓存 → authority 路径（未被 PR 修改）
- ✅ 阅读 ProjectAccessScopeService.java 确认 hasAdminAccess 短路逻辑（未被 PR 修改）
- ✅ 阅读 TenderProjectAccessGuard.java 确认 403 抛出路径
- ✅ Grep `@PreAuthorize` 全 TenderController 确认 hasAnyRole 列表不含 BID_SYSTEMADMIN
- ✅ 阅读 RoleProfileCatalog.java 确认 GLOBAL_ACCESS_ROLES 定义
- ✅ 3 条故障链推演到根因，每条给出代码证据

### 历史价值

本报告作为 PR !2178 的"禁止上线"证据归档，用于：
1. 团队日后审计为何 PR !2178 被驳回
2. 后续类似 bug 排查时参考"3 条故障链"分析方法
3. Production Risk Review v2.0 框架的实际应用案例
4. 提醒"基于错误根因分析的修复方案必然失败"的教训

---

## 第二次修正声明（2026-07-21，用户指正后）

### 第一次审计的错误认知

第一次审计报告认为："P0 立即：修正 OSS 端配置，把覃超颖的 admin 改回 bid-SystemAdmin（运营操作，无需代码）"——这是基于错误认知的判断。

### 用户指正后的正确认知

**OSS 端没有属于我们投标系统的 admin 角色**——admin 是我们系统独有的本地超级管理员账户，与 OSS 无关。

OSS 是多系统共用的角色管理平台（承载 Home/CRM/SCM/投标等多个子系统），返回的 `sysRoleList` 中混合了多系统角色。其中属于我们投标系统的只有 7 个角色码（`/bidAdmin`、`bid-TeamLeader`、`bid-SystemAdmin`、`bid-Team`、`bid-projectLeader`、`bid-administration`、`bid-otherDept`）。

如果 OSS 返回的 `sysRoleList` 中包含 `admin`，那是**其他系统**（如 OSS 平台本身、Home 系统、CRM 系统）的 admin，**不是我们投标系统的 admin**，不应该被识别为我们系统的 admin 写入缓存。

### 真正的核心根因（第二次修正）

**代码缺陷**：`RoleProfileCatalog.DEFINITIONS` 中注册了 `admin`（`ADMIN_CODE = "admin"`，line 19, 108），导致 `canonicalCode("admin")` 返回 "admin"（line 232-240）。当 OSS 返回的多系统 `sysRoleList` 中包含其他系统的 admin 角色时，`OssRoleResolver.resolveRoleCodeFromJobList`（line 75-100）会错误地把其他系统的 admin 识别为我们系统的 admin，写入 Redis 缓存。

### 覃超颖 case 的实际场景

1. 覃超颖在 OSS 端的配置是正确的 `bid-SystemAdmin`（投标系统管理员）
2. OSS 返回的 sysRoleList 中同时包含：
   - 其他系统的 admin 角色（如 OSS 平台本身的 admin、Home 系统的 admin 等）
   - 我们系统的 bid-SystemAdmin 角色
3. 我们的 `OssRoleResolver` 按顺序遍历 sysRoleList，如果 admin 排在 bid-SystemAdmin 前面，就会错误地选中 admin
4. 错误的 admin 角色码被写入 Redis 缓存（`oss:perm:09118`）
5. `UserDetailsServiceImpl` 读缓存得到 admin，颁发 `ROLE_ADMIN`
6. 后续访问走 `@PreAuthorize` + `hasAdminAccess` 短路，绕过数据权限检查

### 无需运营操作

覃超颖在 OSS 端的配置本来就是正确的 `bid-SystemAdmin`，**不需要修改 OSS 端配置**。问题是我们的代码错误地把其他系统的 admin 识别为我们系统的 admin。

### 真正的修复方向

在 OSS 角色解析路径中排除 `admin`——admin 是本地独有的超级管理员，OSS 返回的 admin 一定是其他系统的 admin。具体实现方案：

**方案 A**：`RoleProfileCatalog` 新增 `OSS_ELIGIBLE_CODES` 集合（7 个 bid-* 角色码，不含 admin），`canonicalCode` 在 OSS 解析路径中只识别这 7 个角色码。

**方案 B**：`RoleProfileCatalog` 新增 `canonicalOssCode(String)` 方法，对 admin 返回 null。

**方案 C**：`OssRoleResolver.resolveRoleCodeFromJobList` 显式跳过 `admin` 角色码——OSS 返回的 admin 一定是其他系统的 admin。

### 相关代码位置

- `RoleProfileCatalog.java:19` — `ADMIN_CODE = "admin"` 常量定义
- `RoleProfileCatalog.java:108` — `DEFINITIONS.put(ADMIN_CODE, ...)` 注册 admin 到 DEFINITIONS
- `RoleProfileCatalog.java:232-240` — `canonicalCode("admin")` 返回 "admin"（错误！admin 不应在 OSS 识别路径中）
- `OssRoleResolver.java:75-100` — `resolveRoleCodeFromJobList` 遍历 sysRoleList，调用 `mapOssRoleCodeToInternal`
- `JobRoleLookupResolver.java:156-165` — `mapOssRoleCodeToInternal` 调用 `RoleProfileCatalog.canonicalCode`
- `OssLoginFlowService.java:196-231` — `cacheOssPermissions` 写入 Redis 缓存
- `UserDetailsServiceImpl.java:122-151` — `resolveRoleSource` 读 OSS 缓存颁发 authority
