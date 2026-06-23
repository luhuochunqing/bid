# Data Model: 移除 staff 角色并统一用户启用状态为 OSS

## Entity: User

| Field | Type | Source of Truth | Notes |
|---|---|---|---|
| `id` | Long | DB auto | Primary key |
| `username` | String | OSS / Local init | 工号或系统账号名 |
| `roleCode` | String | OSS / Local init | 内部角色码，仅允许 7 个有效值或 null |
| `enabled` | boolean | OSS sync / Local init | **不再由管理员手动修改** |
| `externalOrgSourceApp` | String | OSS sync | 标识为 OSS 同步用户；本地账号为 null |
| `passwordHash` | String | Local init / default | OSS 用户使用占位/无效 hash |
| `ossPermissionCache` | JSON | OSS sync | OSS 返回的权限缓存 |
| `createdAt` / `updatedAt` | Timestamp | DB | Audit |

### Validation Rules

- `roleCode` 必须是以下之一，或为 null（表示未映射/未授权）：
  - `bid_admin`
  - `bid_lead`
  - `admin`
  - `bid_specialist`
  - `sales`
  - `admin_staff`
  - `bid_other_dept`
- 当 `roleCode` 为 null 或 `enabled=false` 时，用户无法登录。
- `enabled` 对 OSS 用户仅由 OSS 同步写入；对本地账号由本地初始化器写入。

## Entity: RoleProfileCatalog

| Field | Type | Notes |
|---|---|---|
| `roleCode` | String | 内部角色唯一标识 |
| `roleName` | String | 展示名称 |
| `permissionKeys` | JSON/List | 可访问菜单/按钮权限 |
| `dataScopeType` | Enum | 数据权限范围 |

### Allowed Roles After Change

| roleCode | roleName | 数据来源 |
|---|---|---|
| `bid_admin` | 投标管理员 | `/bidAdmin` |
| `bid_lead` | 投标组长 | `bid-TeamLeader` |
| `admin` | 投标系统管理员 | `bid-SystemAdmin` |
| `bid_specialist` | 投标专员 | `bid-Team` |
| `sales` | 投标项目负责人 | `bid-projectLeader` |
| `admin_staff` | 行政人员 | `bid-administration` |
| `bid_other_dept` | 跨部门协同人员 | `bid-otherDept` |

**Removed**: `staff` 角色不再在角色目录中定义，前端不再展示，后端不再作为有效登录角色。

## Mapping: OSS Code → Internal Role Code

| OSS Code | Internal Code |
|---|---|
| `/bidAdmin` | `bid_admin` |
| `bid-TeamLeader` | `bid_lead` |
| `bid-SystemAdmin` | `admin` |
| `bid-Team` | `bid_specialist` |
| `bid-projectLeader` | `sales` |
| `bid-administration` | `admin_staff` |
| `bid-otherDept` | `bid_other_dept` |
| 未映射 / 其他 | `null`（不再 fallback 到 staff） |

## State Transitions

### OSS User Login Flow

```
[OSS Auth Success]
    │
    ▼
[Fetch OSS Role + Employee Status]
    │
    ▼
[Map Role → Internal Code]
    │
    ├── Role not in whitelist ──► [Reject Login: 无系统访问权限]
    │
    ▼
[Employee Status = Active?]
    │
    ├── No ──► [Reject Login: 账号已停用]
    │
    ▼
[Update User.enabled, roleCode, ossPermissionCache]
    │
    ▼
[Issue JWT]
```

### OSS User Sync Flow (Periodic)

```
[Fetch OSS User Snapshot]
    │
    ▼
[Map Role → Internal Code]
    │
    ├── Role not in whitelist ──► [Set enabled=false, roleCode=null]
    │
    ▼
[Employee Status = Active?]
    │
    ├── No ──► [Set enabled=false]
    │
    ▼
[Set enabled=true, roleCode=mapped]
```

## Migration

### V{next}__migrate_staff_users.sql

- 将 `role_code = 'staff'` 的用户更新为 `enabled = false`。
- 将 `role_code` 清空或设为 null。
- （可选）在 operation_log 或 audit 表中记录此次批量调整，便于追溯。
