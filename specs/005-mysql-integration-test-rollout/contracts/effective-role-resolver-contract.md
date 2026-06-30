# Contract: EffectiveRoleResolver

**Feature**: 005-mysql-integration-test-rollout
**Date**: 2026-06-30
**Source**: [backend/src/main/java/com/xiyu/bid/security/EffectiveRoleResolver.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/security/EffectiveRoleResolver.java)

## 公共方法契约

### `resolve(User user) -> EffectiveRoleResult`

**输入**: `User` 实体（可为 null）
**输出**: `EffectiveRoleResult`（含 `roleCode` + `source`），`user=null` 时返回 `new EffectiveRoleResult(null, null)`

**决策逻辑**（委托纯核心 `EffectiveRolePolicy.decide`）:
1. 读 `RoleCodeCachePort.getRoleCode(user.getUsername())`
2. 读 `user.getRoleCode()`（实体方法）
3. 判定 `isOssUser`：`external_org_source_app` 非空非 blank → OSS 用户
4. 决策：
   - 缓存命中（含非空值）→ 返回缓存值，`source=CACHE_HIT`
   - 缓存未命中 + OSS 用户 → 返回 null，`source=CACHE_MISS_FAIL_CLOSED`（**不**回退实体）
   - 缓存未命中 + 本地用户 → 返回实体 `getRoleCode()`，`source=LOCAL_USER`

**集成测试验证点**:
- 真实 `users` 表中 `role_id=NULL` 的 OSS 用户，缓存未命中时返回 null（不回退 "manager"）
- 真实 `users` 表中 `role_id` 指向 `roles.code='bid-Team'` 的 OSS 用户，缓存未命中时返回 "bid-Team"（来自实体，但 source 仍是 `CACHE_MISS_FAIL_CLOSED` —— **注意**：这取决于 `EffectiveRolePolicy.decide` 的具体实现，集成测试需验证真实行为）
- `external_org_source_app` 在 MySQL 中的真实存储（空字符串、null、非空值三种状态）

### `resolveRoleCode(User user) -> String`

**输入**: `User` 实体（可为 null）
**输出**: 角色码字符串（可为 null），等价于 `resolve(user).roleCode()`

**便捷方法**，无独立决策逻辑，集成测试主要通过 `resolve()` 验证 source 标签。

## 依赖契约

### `RoleCodeCachePort`（端口接口）

**接口**: [backend/src/main/java/com/xiyu/bid/security/RoleCodeCachePort.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/security/RoleCodeCachePort.java)

```java
public interface RoleCodeCachePort {
    Optional<String> getRoleCode(String username);
}
```

**集成测试 stub**: 用 `@TestConfiguration` 提供内存 Map 实现（[research.md 决策 1](../research.md)），不调用真实 OSS API。

### `User.getRoleCode()`（实体方法，已 `@Deprecated`）

**行为**: `role_id=NULL` 时 fallback 返回 `"manager"`（CO-361/CO-373 根因）
**集成测试关注**: 验证 `EffectiveRoleResolver` 在 OSS 用户 `role_id=NULL` 时**不**调用此方法回退（fail-closed）

### `User.getExternalOrgSourceApp()`

**行为**: 返回 `external_org_source_app` 字段值
**集成测试关注**: 验证空字符串、null、非空值三种状态下的 `isOssUser` 判定

## 异常契约

`EffectiveRoleResolver` 不抛业务异常（fail-closed 返回 null）。集成测试不验证异常路径。

## 可观测性契约

按 `source` 分级记日志（FR-009）：
- `CACHE_HIT` / `LOCAL_USER` → `log.debug`
- `CACHE_MISS_FAIL_CLOSED` → `log.warn`

集成测试不验证日志写入（日志是副作用，纯 Mock unit test 已覆盖）。
