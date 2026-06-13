# Data Model: 西域对接 — 组织架构SDK接入

## Entities

### OrganizationEvent (inbox)

| Field | Type | Description |
|---|---|---|
| id | Long | 主键 |
| eventKey | String | 幂等键：`{source}\|{topic}\|{key}\|{time}` 的 SHA-256 hash |
| eventSource | String | 事件来源，如 `oss` |
| eventTopic | String | `BaseOssDept` 或 `BaseOssUser` |
| traceId | String | 事件链路 trace ID |
| spanId | String | 事件链路 span ID |
| parentId | String | 父级链路 ID |
| eventTime | Long | 事件时间（毫秒时间戳） |
| key | String | 业务键：`deptId` 或 `userId` |
| subjectId | String | 提取的主体 ID（deptId 或 userId） |
| status | Enum | `PROCESSING` / `PROCESSED` / `PENDING_RETRY` / `DEAD_LETTER` / `DUPLICATE` / `REJECTED` |
| rawPayload | Text | 原始 JSON 报文 |
| retryCount | Integer | 已重试次数 |
| nextRetryAt | LocalDateTime | 下次重试时间（可为空） |
| lastErrorCode | String | 最后错误分类代码 |
| lastErrorMessage | String | 最后错误信息（不含敏感字段） |
| receivedAt | LocalDateTime | 接收时间 |

### OrganizationDepartment (本地部门)

| Field | Type | Description |
|---|---|---|
| id | Long | 主键 |
| externalDeptId | String | 幂等主键（西域 deptId） |
| departmentCode | String | 规范化部门编码 |
| departmentName | String | 部门名称 |
| parentExternalDeptId | String | 父部门 externalDeptId（可为空） |
| parentDepartmentCode | String | 父部门编码（可为空） |
| disabled | Boolean | 是否禁用（默认 `false`） |
| lastSyncedAt | LocalDateTime | 最后同步时间 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### OrganizationUser (本地员工)

| Field | Type | Description |
|---|---|---|
| id | Long | 主键 |
| externalUserId | String | 幂等主键（西域 userId） |
| username | String | 规范化用户名（loginId） |
| fullName | String | 全名 |
| email | String | 邮箱（可为空） |
| phone | String | 手机号（可为空） |
| departmentCode | String | 主部门编码 |
| departmentName | String | 主部门名称 |
| roleCode | String | 角色编码（`admin`/`manager`/`staff`） |
| disabled | Boolean | 是否禁用（默认 `false`，离职 → `true`） |
| lastSyncedAt | LocalDateTime | 最后同步时间 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### OrganizationSyncRun (同步运行)

| Field | Type | Description |
|---|---|---|
| id | Long | 主键 |
| runType | Enum | `INITIALIZATION` / `RECONCILIATION` / `MANUAL_DEPARTMENT_RESYNC` / `MANUAL_USER_RESYNC` |
| source | String | 事件来源标识 |
| triggeredBy | String | 触发人（`SYSTEM` 或用户名） |
| windowStart | LocalDateTime | 时间窗口起点 |
| windowEnd | LocalDateTime | 时间窗口终点 |
| startedAt | LocalDateTime | 开始时间 |
| completedAt | LocalDateTime | 完成时间（可为空） |
| successCount | Integer | 成功数 |
| failureCount | Integer | 失败数 |
| status | Enum | `RUNNING` / `COMPLETED` / `FAILED` |

### OrganizationSyncItem (同步项明细)

| Field | Type | Description |
|---|---|---|
| id | Long | 主键 |
| syncRunId | Long | 关联 sync run |
| objectType | Enum | `DEPARTMENT` / `USER` |
| externalId | String | deptId 或 userId |
| result | Enum | `SUCCESS` / `FAILURE` / `DISABLED` / `NOT_FOUND` |
| errorCode | String | 错误分类（可为空） |
| errorMessage | String | 错误信息（可为空） |
| processedAt | LocalDateTime | 处理时间 |

## State Transitions

### Event Inbox

```
PROCESSING
  ├── [upsert 成功] → PROCESSED
  ├── [重试失败但未耗尽] → PENDING_RETRY
  ├── [重试耗尽] → DEAD_LETTER
  └── [不合法] → REJECTED / DEAD_LETTER
```

### OrganizationUser.disabled

```
enabled = false（新增/正常同步）
  └── [YAPI 查无/禁用] → disabled = true（不物理删除）
```

## Indexes

| Table | Index | Columns |
|---|---|---|
| organization_event_logs | `idx_event_logs_status_retry` | `(status, next_retry_at)` |
| organization_event_logs | `idx_event_logs_event_key` | `(event_key)` UNIQUE |
| organization_departments | `idx_dept_external_id` | `(external_dept_id)` UNIQUE |
| organization_users | `idx_user_external_id` | `(external_user_id)` UNIQUE |
