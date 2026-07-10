# 根因分析：账户/CA 管理页面向投标项目负责人开放时的 403 反复修复

> 相关角色：`bid-projectLeader`（投标项目负责人/销售）
> 相关端点：`/api/platform/accounts`、`/api/ca-certificates`
> 修复 PR：[Gitee !1989](https://gitee.com/allinai888/bid/pulls/1989)
> 沉淀时间：2026-07-10

---

## 1. 现象

用户 5052（角色 `bid-projectLeader`）访问**账户管理**和 **CA 信息管理**页面时，前端报错：

```text
Failed to load resource: the server responded with a status of 403
Account-DtRL_iFR.js:1 Failed to load accounts: AxiosError: Request failed with status code 403
```

后端 access log 显示：

```json
{
  "@timestamp": "2026-07-10T16:11:32+08:00",
  "message": "access_log method=GET uri=/api/platform/accounts status=403 elapsed=1ms",
  "userId": "5052",
  "roleCode": "bid-projectLeader"
}
```

---

## 2. 修复历程（典型的“修 A 破 B + 盲目相信已修复”）

| 轮次 | 改动 | 结果 | 问题 |
|---|---|---|---|
| 第 1 轮 | 在 Service/Policy 层放开 `bid-projectLeader` 可查看账户/CA 全量数据 | 仍 403 | 只改了数据可见性，没到 Controller 层就被 `@PreAuthorize` 拦截 |
| 第 2 轮 | 在 `UserDetailsServiceImpl` 兜底：持有任意 `resource-*` 子权限时自动补 `resource` 父权限 | ✅ 修复 | 需要重新打包部署 |

关键转折：第 1 轮修复后**误以为已生效**并部署，实际部署的 jar 里根本没有第 2 轮的兜底逻辑，导致“代码改了但线上还是 403”。

---

## 3. 5 个为什么

1. **为什么 403？** → Spring Security 鉴权失败，用户缺少 `resource` 权限。
2. **为什么用户没有 `resource`？** → 该用户是 OSS 同步用户，OSS 端只下发了 `resource-account` / `resource-ca` 两个子菜单，没有下发 `resource` 父菜单。
3. **为什么 Controller 要 `resource`？** → `PlatformAccountController` 和 `CaCertificateController` 类级使用 `@PreAuthorize("hasAuthority('resource')")` 做模块入口兜底。
4. **为什么本地角色有 `resource` 但 OSS 角色没有？** → `RoleProfileCatalog` 中本地 `bid-projectLeader` 的 `menuPermissions` 包含 `resource`，但 OSS 菜单映射 `1005 → ?` 没有映射到 `resource`（或业务上只配了子菜单）。
5. **为什么之前没暴露？** → 早期这些页面只对 admin/bidAdmin/投标组长开放，他们通过 catalog 或 OSS 父菜单获得了 `resource`；新增向销售开放时，销售角色的 OSS 菜单只配了子权限。

---

## 4. 根因结论

属于 engineering-discipline 中的两类根因：

1. **修 A 破 B / 修得不彻底**：只修了 Service 层数据权限，没修 Controller 层入口权限。
2. **盲目相信“已修复”**：第 2 轮代码修对了，但部署的 jar 不包含修复，没有在真实环境验证 authorities 里是否出现 `resource`。

**真正根因**：OSS 菜单权限模型（只下发叶子菜单）与后端模块级 `@PreAuthorize`（要求父菜单权限）之间存在语义鸿沟。当角色只被授予子菜单时，后端必须显式做“子权限 → 父权限”的兜底推导。

---

## 5. 根治方案

在 `UserDetailsServiceImpl` 的权限构建阶段统一兜底：

```java
// 兜底：只要用户持有任意 resource-* 子权限，就自动补 resource 父权限。
// 原因：OSS 端对 bid-projectLeader 只下发 100504/100505 → resource-account/resource-ca，
// 不下发 1005 → resource，导致 @PreAuthorize("hasAuthority('resource')") 403。
if (authorities.stream().anyMatch(p -> p != null && p.startsWith("resource-"))) {
    authorities.add("resource");
}
```

同时在 OSS 路径和本地 DB 路径都应用此兜底（`UserDetailsServiceImpl#ossAuthorities` 和 `#addMenuPermissionAuthorities`）。

---

## 6. 验证方法

### 6.1 单元测试（根因行为测试）

新增 `UserDetailsServiceImplTest` 用例：

- OSS `bid-projectLeader` 只有 `resource-account` / `resource-ca` → 最终 authorities 必须包含 `resource`。
- 本地 `bid-projectLeader` 只有 `resource-account` / `resource-ca` → 最终 authorities 必须包含 `resource`。
- 无 `resource-*` 子权限的用户 → 不应被补 `resource`。

### 6.2 真实环境验证

登录 `bid-projectLeader` 账号后，检查 `/var/log/xiyu-bid/application.json.log`：

```bash
# 应看到 authorities 列表包含 resource
rg 'UserDetails authorities built.*5052' /var/log/xiyu-bid/application.json.log

# 访问以下端点应返回 200
GET /api/platform/accounts
GET /api/ca-certificates?size=500
```

---

## 7. 防复发措施

### 7.1 已落地的 pre-push 拦截脚本

`scripts/check-parent-permission-fallback.mjs`：

- 扫描所有 `@PreAuthorize(hasAuthority('X'))` 使用的权限键。
- 扫描 `RoleProfileCatalog` 中是否存在 `X-Y` 形式的子权限。
- 若存在子权限且 `@PreAuthorize` 要求父权限 `X`，则检查 `UserDetailsServiceImpl` 是否包含 `startsWith("X-")` + `authorities.add("X")` 兜底。
- 缺少兜底时阻断 push。

已在 `scripts/pre-push-gate.sh` 9.8 节接入。

### 7.2 设计原则

- **模块入口权限与叶子菜单权限解耦**：如果 OSS 侧只能授予叶子菜单，后端 Controller 要求父权限时必须提供推导逻辑，不能假设父权限一定存在。
- **权限变更必须双端验收**：修改 `RoleProfileCatalog` 或 OSS 映射时，必须同时检查 `@PreAuthorize` 使用点和 `UserDetailsServiceImpl` 兜底。
- **修复后必须验证运行时 authorities**：不能只看代码合入，必须看真实用户登录后的 `UserDetails authorities built` 日志。

---

## 8. 相关文档

- `docs/lessons/lessons-learned.md` §X — 父权限兜底缺失导致 403
- `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java`
- `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`
- `backend/src/test/java/com/xiyu/bid/auth/UserDetailsServiceImplTest.java`
