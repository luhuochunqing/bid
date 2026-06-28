# Phase 1 Data Model: 统一服务层角色码解析入口

**Date**: 2026-06-27

本特性不新增数据库表或字段。以下描述涉及的现有数据结构与新增的领域模型。

## 现有实体（不改）

### User（users 表）
- `id`: Long
- `username`: String（OSS 用户为工号）
- `role`: enum {ADMIN, MANAGER}（遗留字段，仅 admin/manager 二值）
- `roleProfile`: RoleProfile（JPA 关联，`role_id` 外键，OSS 用户为 NULL）
- `externalOrgSourceApp`: String（`external_org_source_app` 列，OSS 来源标识，非空表示 OSS 用户）
- 其他字段略

### RoleProfile（role_profiles 表）
- `code`: String（内部角色码，如 `bid-Team`、`bid-projectLeader`、`admin`）
- `name`: String（显示名）

### OssPermissionCache.CacheEntry（Redis `oss:perm:` key）
- `roleCode`: String（OSS 解析的内部角色码）
- `menuPermissions`: List<String>
- `permission`: CrmUserPermission
- `expiresAt`: Instant（8h TTL）

## 新增领域模型（纯核心）

### EffectiveRoleResult（record）
```java
public record EffectiveRoleResult(
    String roleCode,        // 有效角色码，可空（fail-closed 时为 null）
    Source source           // 决策来源
) {
    public enum Source {
        CACHE_HIT,              // OSS 缓存命中
        LOCAL_USER,             // 非 OSS 用户，用实体角色码
        CACHE_MISS_FAIL_CLOSED  // OSS 用户缓存未命中，fail-closed 返回 null
    }
}
```

**不变性**: record 不可变，符合 FP-Java Profile。
**用途**: 纯核心 `EffectiveRolePolicy` 的返回值，外壳据此记录决策日志。

## 新增组件

### EffectiveRolePolicy（纯核心，`security/domain` 包）
- **职责**: 根据显式入参决定有效角色码
- **输入**: `Optional<String> cachedRoleCode`, `String entityRoleCode`, `boolean isOssUser`
- **输出**: `EffectiveRoleResult`
- **规则**:
  1. 若 `cachedRoleCode` 非空非 blank → `CACHE_HIT` + 缓存值
  2. 否则若 `!isOssUser` → `LOCAL_USER` + 实体角色码
  3. 否则 → `CACHE_MISS_FAIL_CLOSED` + null
- **依赖**: 无（纯函数，可单测）

### EffectiveRoleResolver（外壳，`security` 包）
- **职责**: 编排——读缓存、取实体属性、调纯核心、记日志
- **依赖**: `OssPermissionCache`（Gateway）
- **方法**: `EffectiveRoleResult resolve(User user)` / `String resolveRoleCode(User user)`
- **日志**: 按 source 分级（CACHE_HIT/LOCAL_USER→debug，CACHE_MISS_FAIL_CLOSED→warn）

## 改造组件（接口不变，内部改调 resolver）

| 组件 | 改造内容 |
|------|----------|
| CurrentUserResolver | `getCurrentRoleCode()` 改调 `effectiveRoleResolver`；新增 `resolveEffectiveRoleCode(User)` |
| TaskPermissionGuard | 4 处 `currentUser.getRoleCode()` 改调 resolver |
| TenderCommandAccessGuard | 2 处改调 resolver |
| ProjectTaskAuthorizationGuard | 1 处改调 resolver |
| ProjectAccessScopeService | 3 处改调 resolver |
| BatchAssignmentPolicy | 1 处改调 resolver |
| AssignmentCandidatePolicy | 3 处改调 resolver |
| SettingsService | 1 处改调 resolver |
| PlatformAccountService | 1 处改调 resolver |
| RoleProfileService | 1 处改调 resolver |
| ProjectTaskWorkflowService | 1 处改调 resolver |
| ProjectDocumentWorkflowService | 1 处改调 resolver |

## 收敛组件（删除重复实现，改用 resolver）

| 组件 | 收敛内容 |
|------|----------|
| ProjectDraftingService | 删除私有 `resolveEffectiveRoleCode`，注入 resolver |
| DataScopeConfigService | 删除私有缓存读取，改用 resolver |
| UserDetailsServiceImpl | 角色码读取改用 resolver |
| AuthService | 角色码读取改用 resolver |

## 不改的调用（研究 R6）

- Assembler/Mapper 类的 `getRoleCode()`（数据快照用途）
- TraceFilter 的 MDC 记录
- User 实体方法本身
- 测试代码
