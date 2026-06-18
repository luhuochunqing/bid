# Data Model: OSS 批量岗位/角色回查优化

**Feature**: OSS 批量岗位/角色回查优化
**Date**: 2026-06-18

## Entities

### OssUserJobAndRoleDto

批量接口返回的单条记录，按工号索引。

| Field | Type | Description | Constraints |
|---|---|---|---|
| `jobNumber` | `String` | 员工工号 | 非空，唯一键 |
| `jobName` | `String` | 岗位名称 | 可能为空 |
| `sysRoleList` | `List<String>` | 系统角色名称列表 | 可能为空 |
| `employeeStatus` | `String` | 在职状态 | 可能为空 |
| `status` | `String` | 账号状态 | 可能为空 |
| `username` | `String` | 用户姓名 | 可能为空 |

### JobRoleLookupMap

按工号索引的批量查询结果集合，供同步流程快速查找。

| Field | Type | Description |
|---|---|---|
| `entries` | `Map<String, OssUserJobAndRoleDto>` | key 为工号，value 为对应查询结果 |

### JobRoleLookupContext

批量查询的调用上下文，包含分片参数与原始响应摘要。

| Field | Type | Description |
|---|---|---|
| `requestedJobNumbers` | `List<String>` | 本次请求的工号列表 |
| `responseCount` | `int` | OSS 返回记录数 |
| `durationMs` | `long` | 调用耗时毫秒 |

### RoleMappingSource（枚举）

角色映射来源，用于优先级判定与日志。

| Value | Priority | Description |
|---|---|---|
| `PERSON` | 1 | 人员级规则（person-to-role-mappings） |
| `DEPARTMENT` | 2 | 部门级规则（department-to-role-mappings） |
| `JOB` | 3 | 岗位规则（position-to-role-mappings） |
| `SYS_ROLE_LIST` | 4 | 系统角色列表（sysRoleList） |
| `NONE` | 5 | 未匹配到任何来源 |

### ResolvedRole

角色解析结果。

| Field | Type | Description |
|---|---|---|
| `roleCode` | `String` | 内部 role_code，可能为空 |
| `source` | `RoleMappingSource` | 命中来源 |
| `matchedText` | `String` | 命中的原始文本（岗位名/角色名/规则名） |

## Relationships

```text
OrganizationUserSyncWriter
    ├── JobRoleLookupResolver
    │       ├── PositionToRoleMapper
    │       └── SystemRoleListMapper
    └── OrganizationDirectoryGateway
            └── OrganizationDirectoryHttpGateway
                    └── OssUserJobAndRoleDto[]
```

## Validation Rules

- `OssUserJobAndRoleDto.jobNumber` 非空；为空记录应被丢弃并记录警告。
- `sysRoleList` 中每条角色名在映射前需去除前后空白。
- 角色解析优先级严格按 `PERSON > DEPARTMENT > JOB > SYS_ROLE_LIST` 执行，高优先级命中后不再尝试低优先级。
- 所有来源名称比较大小写不敏感，使用 `Locale.ROOT`。
