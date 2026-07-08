# Data Model: 修复 OSS 用户权限扩散导致越权看所有菜单

**Date**: 2026-07-08
**Feature**: specs/032-fix-oss-permission-diffusion

## 实体关系（无新增表，仅修改现有 DTO 字段）

```text
┌─────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│   users     │     │ OssPermissionCache   │     │ AuthResponse (DTO)  │
│-------------│     │----------------------│     │---------------------│
│ id          │     │ username (key)       │     │ token               │
│ username    │◄────│ roleCode             │────►│ roleCode            │
│ role_id     │     │ menuPermissions[]    │     │ roleName            │
│ external_   │     └──────────────────────┘     │ menuPermissions[]   │
│   org_      │                                  │ isOssUser (NEW)     │
│   source_   │                                  └─────────────────────┘
│   app       │
│ enabled     │     ┌──────────────────────┐     ┌─────────────────────┐
│ role        │     │ RoleProfileCatalog   │     │ UserDetails (Spring)│
│ (enum)      │     │----------------------│     │---------------------│
└─────────────┘     │ ADMIN_CODE = "admin" │     │ username            │
                    │ seedDefinitions()    │────►│ authorities[]       │
                    │  └ admin: ["all"]    │     │  (含 menuPermissions│
                    │  └ bid-Team: [...]   │     │   + ROLE_* + all)   │
                    │ toAuthorityName()    │     └─────────────────────┘
                    │ shouldSkipLegacy...  │
                    └──────────────────────┘
```

## 字段变更清单

### 1. `AuthResponse` DTO（新增字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `isOssUser` | `boolean` | 是否 OSS 同步用户。由 `AuthService.buildAuthResponse` 根据 `user.getExternalOrgSourceApp() != null && !user.getExternalOrgSourceApp().isBlank()` 填充。 |

**向后兼容性**: 新增字段，前端旧版本忽略该字段不影响功能。前端新版本读取该字段用于 `hasPermission` 守卫。

### 2. `UserDetails.authorities`（逻辑变更，无字段新增）

| 用户类型 | 修复前 authorities | 修复后 authorities |
|---|---|---|
| 本地 admin | OSS/DB menuPermissions + 所有角色 menuPermissions + `all` + `system.admin` + `warehouse.manage` + `ROLE_ADMIN` | **不变** |
| OSS admin（如 03063/06234） | OSS menuPermissions + 所有角色 menuPermissions + `all` + `system.admin` + `warehouse.manage` + `ROLE_ADMIN` | OSS menuPermissions + `ROLE_ADMIN`（仅 OSS 返回的权限，不扩散） |
| OSS 非 admin（如 bid-Team） | OSS menuPermissions + 自身 ROLE_* + catalog seed（CO-438 管理权限点） | **不变**（已正确） |

### 3. 前端 `currentUser.menuPermissions`（逻辑变更，无字段新增）

| 用户类型 | 修复前 menuPermissions | 修复后 menuPermissions |
|---|---|---|
| 本地 admin | DB RoleProfile `["all"]` | **不变** |
| OSS admin | OSS menuCodes + catalog admin seed `["all"]` | OSS menuCodes + catalog admin seed（过滤掉 `"all"`） |
| OSS 非 admin | OSS menuCodes + catalog seed（CO-438） | **不变**（已正确） |

## 状态转移（无状态机变更）

本次修复不涉及状态机变更，只修改权限构建逻辑的条件分支。

## 验证规则

| 规则 | 验证方式 |
|---|---|
| OSS 用户 authorities 不含 `all` | `UserDetailsServiceImplTest` 断言 |
| OSS 用户 authorities 不含 `system.admin` | `UserDetailsServiceImplTest` 断言 |
| OSS 用户 menuPermissions 不含 `all` | `DataScopeConfigServiceTest` 断言 |
| 本地 admin authorities 含 `all` + `system.admin` | 回归测试断言 |
| 本地 admin menuPermissions 含 `all` | 回归测试断言 |
| 前端 OSS 用户 hasPermission 不因 `all` 短路 | `user.spec.js` 断言 |
| 前端本地 admin hasPermission 仍短路 | 回归测试断言 |
