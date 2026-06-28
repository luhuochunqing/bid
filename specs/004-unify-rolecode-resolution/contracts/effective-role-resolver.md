# 接口契约: EffectiveRoleResolver

**Date**: 2026-06-27

## 后端内部组件契约（非 HTTP API）

本特性不新增对外 HTTP 接口。以下是后端内部组件契约，供各 Guard/Service 接入时参考。

### EffectiveRolePolicy（纯核心）

```java
package com.xiyu.bid.security.domain;

/**
 * 有效角色码解析决策（纯核心）。
 * 不依赖 Spring/Redis/JPA，可独立单元测试。
 */
public final class EffectiveRolePolicy {

    /**
     * 决定有效角色码。
     *
     * @param cachedRoleCode  OSS 缓存角色码（命中时非空，未命中为 empty）
     * @param entityRoleCode  用户实体的角色码（roleProfile.code 或实体回退值）
     * @param isOssUser       是否 OSS 同步用户（external_org_source_app 非空）
     * @return 解析结果，含角色码与决策来源
     */
    public static EffectiveRoleResult decide(
        java.util.Optional<String> cachedRoleCode,
        String entityRoleCode,
        boolean isOssUser
    ) { ... }
}
```

### EffectiveRoleResult（record）

```java
public record EffectiveRoleResult(
    String roleCode,
    Source source
) {
    public enum Source { CACHE_HIT, LOCAL_USER, CACHE_MISS_FAIL_CLOSED }
}
```

### EffectiveRoleResolver（外壳编排）

```java
package com.xiyu.bid.security;

/**
 * 有效角色码解析器（外壳）。
 * 读取 OSS 缓存后委托纯核心决策，记录解析日志。
 * 服务层权限校验的唯一角色码读取入口。
 */
@Component
public class EffectiveRoleResolver {

    private final OssPermissionCache ossPermissionCache;

    /**
     * 解析用户的角色码。
     * @param user 当前用户（不可空）
     * @return 有效角色码（OSS 缓存未命中时为 null，fail-closed）
     */
    public String resolveRoleCode(User user) { ... }

    /**
     * 解析用户的角色码（含决策来源，用于日志/测试断言）。
     */
    public EffectiveRoleResult resolve(User user) { ... }
}
```

## 接入约定

各 Guard/Service 改造模式：

```java
// 改造前
String roleCode = currentUser.getRoleCode();
TaskOperationPolicy.canManageTask(roleCode, ...);

// 改造后
String roleCode = effectiveRoleResolver.resolveRoleCode(currentUser);
TaskOperationPolicy.canManageTask(roleCode, ...);
```

- Guard/Service 注入 `EffectiveRoleResolver`（构造器注入）。
- 调用 `resolveRoleCode(User)` 获取角色码字符串。
- 若需决策来源（如审计），调用 `resolve(User)` 获取完整结果。

## 现有 HTTP 接口行为变更

无新增接口。以下现有接口的 403 行为将修正（OSS 用户不再被误拒）：

| 接口 | 修正前 | 修正后 |
|------|-------|-------|
| `POST /api/tasks` 分配任务 | OSS 用户 403 | 成功（缓存角色在白名单内） |
| `POST /api/projects/{id}/drafting/submit-review` | OSS 用户 403 | 成功 |
| `POST /api/projects/{id}/drafting/submit-bid` | OSS 用户 403 | 成功 |

本地用户与管理员接口行为不变。
