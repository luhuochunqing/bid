# Data Model: 项目档案时间戳

**Feature**: specs/016-project-archive-stage-timestamps/spec.md

## Entity Changes

### Project Entity (backend/src/main/java/com/xiyu/bid/entity/Project.java)

| Field | Type | Nullable | Default | Description |
|-------|------|----------|---------|-------------|
| `initiatedAt` | `LocalDateTime` | YES | NULL | 立项时间（首次进入 INITIATED 阶段） |
| `evaluatingAt` | `LocalDateTime` | YES | NULL | 评标时间（首次进入 EVALUATING 阶段） |
| `closedAt` | `LocalDateTime` | YES | NULL | 结项时间（首次进入 CLOSED 阶段） |

**Note**: 字段仅在首次进入对应阶段时写入，已存在值不覆盖。

## Database Schema

### Migration: V1044__project_stage_timestamps.sql

```sql
-- 新增 3 个阶段时间戳列
ALTER TABLE projects
    ADD COLUMN initiated_at DATETIME NULL COMMENT '立项时间',
    ADD COLUMN evaluating_at DATETIME NULL COMMENT '评标时间',
    ADD COLUMN closed_at DATETIME NULL COMMENT '结项时间';

-- 历史数据回填：已有记录的 initiated_at 回填为 created_at（近似值）
-- evaluating_at 和 closed_at 在历史数据中无来源，迁移后保持 NULL
UPDATE projects SET initiated_at = created_at WHERE initiated_at IS NULL;
```

### Rollback: U1044__project_stage_timestamps.sql

```sql
ALTER TABLE projects
    DROP COLUMN IF EXISTS initiated_at,
    DROP COLUMN IF EXISTS evaluating_at,
    DROP COLUMN IF EXISTS closed_at;
```

## DTO Mappings

### ProjectArchiveDetailResponse (backend/src/main/java/com/xiyu/bid/casework/dto/ProjectArchiveDetailResponse.java)

| DTO Field | Source Entity Field | Fallback |
|-----------|---------------------|----------|
| `initiatedAt` | `Project.initiatedAt` | `Project.createdAt`（降级） |
| `bidSubmissionAt` | `Project.evaluatingAt` | NULL |
| `closedAt` | `Project.closedAt` | NULL |

**Note**: `bidOpeningAt` 保持从 `Tender.bidOpeningTime` 获取，无变更。

## State Transitions

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  INITIATED  │────▶│  DRAFTING   │────▶│ EVALUATING  │────▶│RESULT_PNDG  │────▶│RETROSPECTIVE│────▶│   CLOSED    │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
      │                                      │                                                                │
      ▼                                      ▼                                                                ▼
 initiatedAt (首次创建时写入)        evaluatingAt (进入EVALUATING时写入)                                   closedAt (进入CLOSED时写入)
```

| Transition | Side Effect |
|------------|-------------|
| Project 创建 | `initiatedAt = NOW()`（仅首次） |
| INITIATED/DRAFTING → EVALUATING | `evaluatingAt = NOW()`（仅首次） |
| any → CLOSED | `closedAt = NOW()`（仅首次） |

## Validation Rules

1. **首次写入原则**: `if (field == null) field = NOW()`
2. **不覆盖已有值**: 时间戳一旦写入，不再修改
3. **历史数据兼容**: 迁移脚本回填 `createdAt` 作为近似值
