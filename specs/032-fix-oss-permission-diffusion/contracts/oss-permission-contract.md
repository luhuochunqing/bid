# OSS 权限构建契约

**Feature**: specs/032-fix-oss-permission-diffusion
**Date**: 2026-07-08

## 契约 1: `UserDetailsServiceImpl.authoritiesFor(User)` 行为契约

### 输入

| 参数 | 类型 | 说明 |
|---|---|---|
| `user` | `User` | 用户实体，含 `externalOrgSourceApp`、`roleCode`、`role` 字段 |

### 输出

`List<SimpleGrantedAuthority>` — Spring Security authorities 列表。

### 行为规则

| 条件 | authorities 内容 |
|---|---|
| `isOssUser=false` + `roleCode="admin"` + `legacyRole=ADMIN` | DB menuPermissions + 所有角色 menuPermissions（扩散）+ `all` + `system.admin` + `warehouse.manage` + `ROLE_ADMIN` + `ROLE_MANAGER`（legacy compat） |
| `isOssUser=true` + `roleCode="admin"` | **OSS menuPermissions（仅 OSS 返回的菜单码）** + `admin` + `ROLE_ADMIN`（不扩散，不补发系统级权限键） |
| `isOssUser=true` + `roleCode="bid-Team"` | OSS menuPermissions + `bid-Team` + `ROLE_BID_TEAM`（不扩散，已正确，不变） |
| `isOssUser=true` + cache miss | fail-closed，抛 `UsernameNotFoundException`（不变） |

### 不变量

- 本地 admin（`isOssUser=false` + `roleCode="admin"`）的 authorities 修复前后完全一致。
- OSS 非 admin 用户的 authorities 修复前后完全一致。
- `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 防线不动。
- `LoginRoleWhitelist` 白名单过滤不动。

## 契约 2: `DataScopeConfigService.getRoleMenuPermissions(User)` 行为契约

### 输入

| 参数 | 类型 | 说明 |
|---|---|---|
| `user` | `User` | 用户实体 |

### 输出

`List<String>` — 前端 `menuPermissions` 列表。

### 行为规则

| 条件 | 返回值 |
|---|---|
| OSS 用户（cache 命中） | OSS menuCodes（normalize）+ catalog seed（**过滤 `"all"`**） |
| 本地 admin（`isLocalSystemAccount=true`） | DB RoleProfile menuPermissions（含 `"all"`，不变） |
| OSS 用户 cache miss | 空列表（fail-closed，不变） |

### 不变量

- 本地 admin 的 menuPermissions 修复前后完全一致（含 `"all"`）。
- OSS 非 admin 用户合并 catalog seed 的逻辑不变（CO-438 管理权限点保留）。
- `normalizeMenuPermissions` 行为不变。

## 契约 3: 前端 `hasPermission(permissionKey)` 行为契约

### 输入

| 参数 | 类型 | 说明 |
|---|---|---|
| `permissionKey` | `string` | 待校验的权限键 |

### 输出

`boolean` — 是否有权限。

### 行为规则

| 条件 | 返回值 |
|---|---|
| `currentUser.isOssUser=false` + `menuPermissions.includes("all")` | `true`（短路放行，本地 admin，不变） |
| `currentUser.isOssUser=true` + `menuPermissions.includes("all")` | `false`（**不短路**，按 `permissionKey` 校验） |
| `menuPermissions.includes(permissionKey)` | `true` |
| 其他 | `false` |

### 不变量

- 本地 admin 的 `all` 短路逻辑不变。
- 非 `all` 权限键的校验逻辑不变。

## 契约 4: `AuthResponse` DTO 字段契约

### 新增字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `isOssUser` | `boolean` | 是 | 是否 OSS 同步用户。`AuthService.buildAuthResponse` 根据 `user.getExternalOrgSourceApp()` 填充。 |

### 向后兼容性

- 新增字段，旧前端忽略该字段不影响功能。
- 旧前端 `hasPermission` 仍按 `menuPermissions.includes("all")` 短路，但因后端修复点 2 已过滤 OSS 用户的 `"all"`，旧前端也不会短路（双重保险）。
