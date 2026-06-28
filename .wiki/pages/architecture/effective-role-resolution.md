---
title: 有效角色解析（Effective Role Resolution）规范
space: engineering
category: spec
tags: [CO-373, 角色解析, EffectiveRoleResolver, DataScopeConfigService, FP-Java, fail-closed, OSS cache]
sources:
backlinks:
  - _index
  - architecture
  - lessons-learned/CO-361-five-rounds-no-fix
  - data-permission-hardening
created: 2026-06-28
updated: 2026-06-28
health_checked: 2026-06-28
---

# 有效角色解析（Effective Role Resolution）规范

> **目的**：固化"角色码从哪读、何时用什么入口、fail-closed 语义"为可复用的工程规范。
> 服务层任何涉及"判断当前用户是什么角色"的代码都必须遵循本规范。
> 不遵循 = 触碰 CO-361 / CO-373 五次反复修复的根因。

---

## 一、问题背景

OSS 同步用户（`external_org_source_app` 非空、`role_id=NULL` → `roleProfile=null`）走 `User.getRoleCode()` 实体方法时，fallback 返回字符串 `"manager"`（来自 `User.role` 枚举的 `MANAGER` 名字段）。

**业务影响**：所有"角色判断"代码会把 OSS 用户当成 manager，可能：
- 误授予 manager 权限（越权）
- 误拒绝 manager 才能用的功能（拒绝服务）
- 误走入"按角色过滤"逻辑（看不见任务 / 看不见项目 / 看不见菜单）

**真实触发案例**：CO-361 五次反复修复——
- `TaskService.getTasksByProjectId` 拿到 `"manager"` → `TaskVisibilityPolicy.canViewAllProjectTasks(...)` 返回 false → 走 `findByProjectIdAndAssigneeId` → 投标专员看不到项目全部任务
- `TaskBoardService.getBoardItems` 同样问题 → 独立看板走 `shouldQueryByProjectScope("manager")` 恰好绕开 → 表现成"两个入口不一致"
- 其他 25+ 处直调都会引爆同类问题

---

## 二、角色码来源（三层）

```
┌──────────────────────────────────────────────────────────────┐
│ Layer 1: OSS 权限缓存 (OssPermissionCache, Redis)            │
│   - TTL 8h（对齐 JWT 有效期）                                │
│   - Key: oss:perm:{username}:roleCode                        │
│   - 权威源（OSS 同步用户以此为准）                            │
└──────────────────────────────────────────────────────────────┘
                          ↑ 优先读
┌──────────────────────────────────────────────────────────────┐
│ Layer 2: 本地 DB RoleProfile.roleCode                        │
│   - 仅 admin 本地系统账户使用                                │
│   - admin 不走 OSS 认证，OSS cache 永远为空                  │
│   - DB roleCode 是 admin 的真实角色源                        │
└──────────────────────────────────────────────────────────────┘
                          ↑ fallback（仅 admin 本地账户）
┌──────────────────────────────────────────────────────────────┐
│ Layer 3: 实体 fallback "manager"                             │
│   - 仅当 roleProfile=null 且 role=null 时返回                │
│   - 几乎必然是 OSS 用户（roleProfile=null 因为 role_id=NULL）│
│   - 【雷区】不要让业务判断走这一层                           │
└──────────────────────────────────────────────────────────────┘
```

---

## 三、两个统一入口（必须使用）

### 3.1 `EffectiveRoleResolver.resolveRoleCode(user)` — **服务层首选**

**位置**：`com.xiyu.bid.security.EffectiveRoleResolver`

**行为**：
1. 读 OSS 缓存（`OssPermissionCache.getRoleCode(username)`）
2. 缓存命中 → 返回缓存值（**OSS 用户的真实角色码**）
3. 缓存未命中 + admin 本地账户 → 返回 `user.getRoleCode()`（DB 直读）
4. 缓存未命中 + OSS 用户 → 返回 `null`（**fail-closed**，要求重新登录）

**典型用法**：
```java
@Service
@RequiredArgsConstructor
public class TaskPermissionGuard {
    private final EffectiveRoleResolver effectiveRoleResolver;
    
    public void assertCanEditTask(User actor, Task task) {
        String roleCode = effectiveRoleResolver.resolveRoleCode(actor);
        if (roleCode == null) {
            throw new AccessDeniedException("缓存失效，请重新登录");
        }
        // ... 用 roleCode 做权限判断 ...
    }
}
```

### 3.2 `DataScopeConfigService.getRoleCode(user)` — **数据范围配置场景**

**位置**：`com.xiyu.bid.admin.service.DataScopeConfigService`

**行为**：同 EffectiveRoleResolver（同一套 OSS cache → DB fallback → null fail-closed 逻辑）。

**典型用法**：与 `getAccessProfile(user)` 配套使用，做"我能看哪些部门 / 哪些项目"的判断。

```java
String roleCode = dataScopeConfigService.getRoleCode(user);
DataScopeAccessProfile profile = dataScopeConfigService.getAccessProfile(user);
if ("all".equals(profile.getDataScope())) { /* 全局访问 */ }
```

**为什么不直接用 EffectiveRoleResolver**：DataScopeConfigService 配套返回 `dataScope` / `explicitProjectIds` / `allowedDepartmentCodes`，组合调用更方便。

---

## 四、何时用什么

| 场景 | 用什么入口 | 为什么 |
|---|---|---|
| 业务权限判定（Guard / @PreAuthorize / 业务 if 判断） | `EffectiveRoleResolver.resolveRoleCode(user)` | 单点决策、fail-closed、OSS-cache-aware |
| 数据范围配置（我能看哪些项目/部门） | `DataScopeConfigService.getAccessProfile(user)` + `getRoleCode(user)` | 同时拿 dataScope 字段 |
| 登录响应构造 | `AuthResponse.from(... dataScopeConfigService.getRoleCode(user))` | 登录主路径必须显式传入解析后的 roleCode |
| 展示字段（DTO / 报表 / 日志） | `user.getRoleCode()` 直接调用 + `// SAFE:` 注释 | 不参与业务判断，仅展示 |
| MDC 日志上下文 | `user.getRoleCode()` 直接调用 + `// SAFE:` 注释 | 日志字段，与业务判断解耦 |
| admin 本地账户判定 | `user.getRoleCode()` 直接调用 + `// SAFE:` 注释 | admin 不走 OSS，必须读 DB |
| OSS 同步写入 | `user.getRoleCode()` 直接调用 + `// SAFE:` 注释 | 同步时 DB 是目标，不是判定源 |
| 审计日志落库 | `user.getRoleCode()` 直接调用 + `// SAFE:` 注释 | 落"操作时点"快照，非当下值 |

---

## 五、Fail-Closed 语义（必须记住）

**OSS 缓存未命中 → 返回 null → 调用方必须显式处理**：

```java
String roleCode = effectiveRoleResolver.resolveRoleCode(user);
if (roleCode == null) {
    // 选择 1: 抛 401 让用户重新登录
    throw new AuthenticationCredentialsNotFoundException("权限缓存失效");
    // 选择 2: 降级为"只看自己"（保守权限）
    return findByAssigneeId(user.getId());
    // 选择 3: 抛 403 拒绝（最严格）
    throw new AccessDeniedException("权限不可用");
}
```

**绝不要**把 null 当 "manager" / "staff" / "anonymous" 任何一个值！CO-373 的根因正是这种"fallback 成某个值"。

---

## 六、三个反模式（绝对不要写）

### 反模式 1：直调 `user.getRoleCode()` 做权限判断

```java
// ❌ 错误
if ("admin".equals(user.getRoleCode())) {
    allowAdminAction();
}

// ✅ 正确
String roleCode = effectiveRoleResolver.resolveRoleCode(user);
if (RoleProfileCatalog.ADMIN_CODE.equalsIgnoreCase(roleCode)) {
    allowAdminAction();
}
```

### 反模式 2：直调 `user.getRoleCode()` 后做 null 检查降级

```java
// ❌ 错误
String roleCode = user.getRoleCode();
if (roleCode == null) roleCode = "staff"; // fallback = 雷区
if (roleCode.equals("staff")) ...

// ✅ 正确
String roleCode = effectiveRoleResolver.resolveRoleCode(user);
if (roleCode == null) {
    // 显式 fail-closed，不偷偷 fallback
    throw new AccessDeniedException("权限不可用");
}
```

### 反模式 3：在 if 里嵌套角色字符串

```java
// ❌ 错误（硬编码角色码字符串）
if (user.getRoleCode().startsWith("bid-")) { ... }

// ✅ 正确（用 RoleProfileCatalog 常量）
if (RoleProfileCatalog.BID_PREFIX_ROLES.contains(roleCode)) { ... }
```

---

## 七、pre-push 检查（自动拦截）

`scripts/check-rolecode-direct-calls.mjs` 已挂到 `pre-push-gate.sh` 第 9.5 节 + `package.json` "check:rolecode-direct-calls"。

**规则**：
1. 扫 `backend/src/main/java/**/*.java`
2. 检测 `user.getRoleCode()` / `currentUser.getRoleCode()` 等 User 类型 receiver
3. 命中且不在豁免白名单 → pre-push gate fail
4. 豁免方式：
   - **首选**：迁移到 `EffectiveRoleResolver.resolveRoleCode(user)` 或 `DataScopeConfigService.getRoleCode(user)`
   - **次选**：调用点上方注释 `// SAFE: <理由>` 或 `// DEPRECATED: <理由>`（仅限已记录豁免场景）
5. 文件白名单（仅 3 个）：`User.java` / `EffectiveRoleResolver.java` / `DataScopeConfigService.java`

**运行**：
```bash
npm run check:rolecode-direct-calls
```

---

## 八、测试策略

每个改 `EffectiveRoleResolver` / `DataScopeConfigService.getRoleCode` 的 PR 必须包含：

1. **OSS 用户 + cache 命中** → 返回真实 roleCode（"bid-Team" 等）
2. **OSS 用户 + cache 未命中** → 返回 null（fail-closed）
3. **admin 本地账户 + cache 未命中** → fallback 到 DB "admin"
4. **admin 本地账户 + cache 命中** → 返回 cache 值（如果 cache 写入过）

每个调用方迁移 PR 必须包含：

1. **被改动的判定路径在 OSS 用户场景下行为正确**（不是只测 admin）
2. **被改动的判定路径在 fail-closed 场景下行为正确**（返回 null 时不偷偷 fallback）
3. **回归原有 admin 行为**

---

## 九、变更日志

- 2026-06-27 PR #1245 — OSS 用户 roleCode 解析修正（局部止血，TaskService + TaskBoardService 两处）
- 2026-06-28 PR #1259 — 引入 EffectiveRoleResolver 统一入口 + 收敛 13+ 处直调（系统性根治）
- 2026-06-28 commit f584ce58d — User.getRoleCode() 加 @Deprecated + 23 处 SAFE 注释 + pre-push 拦截
- 2026-06-28 commit 3dad0e958 — 本规范文档 + CLAUDE.md 强约束段