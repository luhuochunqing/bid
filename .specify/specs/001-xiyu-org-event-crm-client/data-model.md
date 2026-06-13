# Data Model: 西域事件库 SDK 订阅与 CRM 出向客户端

**Created**: 2026-05-16

## Entity Overview

```
┌──────────────────────────┐    ┌──────────────────────────┐
│ OrganizationEventInbox   │    │ LocalDepartment          │
├──────────────────────────┤    ├──────────────────────────┤
│ id (PK, BIGINT)          │    │ id (PK, BIGINT)          │
│ trace_id (VARCHAR 64)    │    │ dept_id (UK, VARCHAR 64) │
│ span_id (VARCHAR 64)     │    │ dept_name (VARCHAR 256)  │
│ event_topic (VARCHAR 64) │    │ parent_dept_id (VARCHAR) │
│ event_type (VARCHAR 32)  │    │ dept_path (VARCHAR 1024) │
│ payload (MEDIUMTEXT)     │    │ status (VARCHAR 16)      │
│ status (VARCHAR 16)      │    │ source_updated_at (DATETIME)│
│ retry_count (INT)        │    │ created_at (DATETIME)    │
│ last_error (TEXT)        │    │ updated_at (DATETIME)    │
│ created_at (DATETIME)    │    └──────────────────────────┘
│ updated_at (DATETIME)    │
└──────────────────────────┘    ┌──────────────────────────┐
  UK: (trace_id, span_id,       │ LocalUser                │
       event_topic)             ├──────────────────────────┤
                                │ id (PK, BIGINT)          │
                                │ user_id (UK, VARCHAR 64) │
                                │ user_name (VARCHAR 128)  │
                                │ email (VARCHAR 256)      │
                                │ mobile (VARCHAR 32)      │
                                │ dept_id (FK→LocalDept)   │
                                │ position (VARCHAR 128)   │
                                │ status (VARCHAR 16)      │
                                │ source_updated_at (DATETIME)│
                                │ created_at (DATETIME)    │
                                │ updated_at (DATETIME)    │
                                └──────────────────────────┘

┌──────────────────────────────┐
│ CrmToken (Memory Only)       │
├──────────────────────────────┤
│ access_token (String)        │
│ expires_in (long, seconds)   │
│ acquired_at (Instant)        │
│ expires_at (Instant)         │  ← derived: acquired_at + expires_in
└──────────────────────────────┘
```

## Entity Definitions

### OrganizationEventInbox

事件收件箱，SDK 和 HTTP 双路共用。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| trace_id | VARCHAR(64) | NOT NULL, UK part | 事件追踪 ID |
| span_id | VARCHAR(64) | NOT NULL, UK part | 事件跨度 ID |
| event_topic | VARCHAR(64) | NOT NULL, UK part | 事件主题 (BaseOssDept / BaseOssUser) |
| event_type | VARCHAR(32) | NOT NULL | CREATE / UPDATE / DELETE |
| payload | MEDIUMTEXT | NOT NULL | 原始 JSON payload |
| status | VARCHAR(16) | NOT NULL, DEFAULT 'PENDING' | PENDING / PROCESSING / SUCCESS / FAILED / SKIPPED |
| retry_count | INT | NOT NULL, DEFAULT 0 | 已重试次数 |
| last_error | TEXT | NULLABLE | 最近一次错误信息 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 最后更新时间 |

**Unique Index**: `uk_event_dedup` ON (trace_id, span_id, event_topic)
**Index**: `idx_status_created` ON (status, created_at) — for pending event polling

### LocalDepartment

西域组织架构部门本地副本。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| dept_id | VARCHAR(64) | UNIQUE, NOT NULL | 西域部门 ID |
| dept_name | VARCHAR(256) | NOT NULL | 部门名称 |
| parent_dept_id | VARCHAR(64) | NULLABLE | 上级部门 ID（回查填充） |
| dept_path | VARCHAR(1024) | NULLABLE | 部门路径（如 /A/B/C） |
| status | VARCHAR(16) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / INACTIVE / DELETED |
| source_updated_at | DATETIME | NULLABLE | 西域侧最后更新时间 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 最后更新时间 |

### LocalUser

西域组织架构用户本地副本。

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT | 自增主键 |
| user_id | VARCHAR(64) | UNIQUE, NOT NULL | 西域用户 ID |
| user_name | VARCHAR(128) | NOT NULL | 用户姓名 |
| email | VARCHAR(256) | NULLABLE | 邮箱 |
| mobile | VARCHAR(32) | NULLABLE | 手机号 |
| dept_id | VARCHAR(64) | NULLABLE | 所属部门 ID（回查填充） |
| position | VARCHAR(128) | NULLABLE | 职位 |
| status | VARCHAR(16) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / INACTIVE / DELETED |
| source_updated_at | DATETIME | NULLABLE | 西域侧最后更新时间 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 最后更新时间 |

### CrmToken (Memory Only)

CRM 访问 Token，不持久化到数据库。

| Field | Type | Description |
|-------|------|-------------|
| access_token | String | CRM 返回的访问令牌 |
| expires_in | long | 有效期（秒），由 applyToken 响应下发 |
| acquired_at | Instant | Token 获取时间 |
| expires_at | Instant | Token 过期时间 = acquired_at + expires_in |

## State Transitions

### Event Inbox Status

```
PENDING ──→ PROCESSING ──→ SUCCESS
    │                         ↑
    │           FAILED ───────┘ (retry_count < max)
    │              │
    │              ↓ (retry_count >= max)
    │           SKIPPED (dead letter, manual inspection)
    └──────────→ SKIPPED (duplicate event, dedup hit)
```

### Organization Entity Status

```
ACTIVE ──→ INACTIVE  (西域标记禁用)
   │
   └──→ DELETED   (西域删除)
```
