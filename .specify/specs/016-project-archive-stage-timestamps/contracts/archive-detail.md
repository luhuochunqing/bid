# Contract: Archive Detail API Response

**Feature**: specs/016-project-archive-stage-timestamps/spec.md

## API Endpoint

```
GET /api/archive/{archiveId}
```

## Response Schema

```json
{
  "archiveId": 123,
  "projectName": "某政府采购项目",
  "projectType": "CENTRALIZED",
  "projectStatus": "ACTIVE",
  "bidResult": "AWARDED",
  "tenderAgency": "某省政府采购中心",
  "initiatedAt": "2026-01-15T09:30:00",
  "bidSubmissionAt": "2026-02-20T14:00:00",
  "bidOpeningAt": "2026-02-21T09:00:00",
  "closedAt": "2026-04-30T17:00:00",
  "projectManager": "张三",
  "bidManager": "李四",
  "files": [...],
  "logs": [...]
}
```

## Field Definitions

| Field | Type | Format | Description |
|-------|------|--------|-------------|
| `archiveId` | Long | - | 档案 ID |
| `projectName` | String | - | 项目名称 |
| `projectType` | String | enum | 项目类型：OFFICE/COMPREHENSIVE/CENTRALIZED/INDUSTRIAL/OTHER |
| `projectStatus` | String | enum | 项目状态：ACTIVE/ARCHIVED |
| `bidResult` | String | enum | 中标结果：AWARDED/LOST/ABANDONED/IN_PROGRESS/OTHER |
| `tenderAgency` | String | - | 招标主体 |
| `initiatedAt` | String | ISO 8601 | 立项日期（进入 INITIATED 阶段时间），无值时为 null |
| `bidSubmissionAt` | String | ISO 8601 | 标书提交日期（进入 EVALUATING 阶段时间），无值时为 null |
| `bidOpeningAt` | String | ISO 8601 | 开标日期（来自 Tender.bidOpeningTime），无值时为 null |
| `closedAt` | String | ISO 8601 | 结项日期（进入 CLOSED 阶段时间），无值时为 null |
| `projectManager` | String | - | 项目负责人 |
| `bidManager` | String | - | 投标负责人 |
| `files` | Array | - | 档案文件列表 |
| `logs` | Array | - | 操作日志列表 |

## Frontend Rendering Contract

| Field | Has Value | Display | No Value |
|-------|-----------|---------|----------|
| `initiatedAt` | `2026-01-15 09:30` | YYYY-MM-DD HH:mm | `-` |
| `bidSubmissionAt` | `2026-02-20 14:00` | YYYY-MM-DD HH:mm | `-` |
| `bidOpeningAt` | `2026-02-21 09:00` | YYYY-MM-DD HH:mm | `-` |
| `closedAt` | `2026-04-30 17:00` | YYYY-MM-DD HH:mm | `-` |

## Test Scenarios

### US-1: 查看档案详情中的真实时间节点

```
Scenario 1: 项目已进入 EVALUATING 阶段
  GET /api/archive/123
  Response:
    initiatedAt: "2026-01-15T09:30:00"
    bidSubmissionAt: "2026-02-20T14:00:00"
    bidOpeningAt: "2026-02-21T09:00:00"
    closedAt: null

Scenario 2: 项目已 CLOSED
  GET /api/archive/456
  Response:
    initiatedAt: "2026-01-01T08:00:00"
    bidSubmissionAt: "2026-01-20T10:00:00"
    bidOpeningAt: "2026-01-21T09:00:00"
    closedAt: "2026-03-15T16:30:00"

Scenario 3: 项目处于 DRAFTING 阶段
  GET /api/archive/789
  Response:
    initiatedAt: "2026-05-01T09:00:00"
    bidSubmissionAt: null
    closedAt: null
```

### US-2: 状态变更自动记录时间节点

```
Scenario: 阶段推进 INITIATED → EVALUATING
  POST /api/projects/123/stage
  Body: { "target": "EVALUATING" }
  Response: 200 OK
  Then GET /api/archive/{archiveId}
  Response.bidSubmissionAt: non-null (timestamp when EVALUATING entered)
```

### US-3: 批量导入历史档案时间线

```
Scenario: 导入历史项目（含自定义时间戳）
  POST /api/projects/import
  Body: {
    "name": "2023年某项目",
    "tenderId": 1,
    "initiatedAt": "2023-06-01T00:00:00",
    "evaluatingAt": "2023-06-15T00:00:00",
    "closedAt": "2023-09-30T00:00:00"
  }
  Response: 201 Created
  Then GET /api/archive/{newArchiveId}
  Response:
    initiatedAt: "2023-06-01T00:00:00"
    bidSubmissionAt: "2023-06-15T00:00:00"
    closedAt: "2023-09-30T00:00:00"
```
