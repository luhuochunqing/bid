# Contract: 评分解析 API — Summary 增量（spec 047）

**Feature**: 根治评分项权重合计缺口 | **Date**: 2026-08-18
**Scope**: 本文件仅记录 spec 047 对既有 `score-parse-api.md`（specs/041-ai-score-parse-backend/contracts/）的**增量**。
字段名、语义、兼容性策略见下。既有字段不做破坏性变更。

## 1. 查询评分项清单（仅 summary 结构增量）

**Endpoint**: `GET /api/projects/{projectId}/score-parse/items`（不变）

**Response body 变化**：`data.summary` 新增一个可空字段，其余不变。

### 变更前 `data.summary`

| 字段 | 类型 | 说明 |
|------|------|------|
| totalWeight | number | 权重合计 |
| totalEstScore | number | 预计得分合计（仅客观项） |
| okCount / dangerCount / pendingCount | number | 满足状态三档计数 |
| objectiveWeight / subjectiveWeight | number | 客观/主观权重分组 |
| weightWarning | boolean | 合计≠100（容差±0.5）标记 |

### 变更后 `data.summary`（+ 1 可空字段）

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| totalWeight | number | 否 | 归一化后恒为 100（不含 0 项失败终态） |
| …（既有字段不变）| | | |
| **originalTotalWeight** | number | **是** | 归一化发生前的权重合计快照；未归一化时为 `null` |

### 语义约定（对接 spec FR-005 / US3）

- `originalTotalWeight === null` → 未归一化（原始合计在容差内，totalWeight 即原值）
- `originalTotalWeight === 90` → 系统曾把合计 90 补齐到 100；前端**可**据此呈现"原合计 90，已补齐到 100"
- `weightWarning`：归一化后 totalWeight 恒=100，故 weightWarning 恒为 `false`（除非 0 项失败终态）。
  **兼容说明**：pre-047 客户端若只读 `weightWarning`，在归一化生效后不再收到 `true`，属预期行为
  （合计划缺失问题已根治，不再是"提示仍缺分"）；若前端需要展示"补齐"信息，读 `originalTotalWeight`。

### 示例

```json
{
  "data": {
    "items": [],
    "summary": {
      "totalWeight": 100,
      "totalEstScore": 0,
      "okCount": 5,
      "dangerCount": 0,
      "pendingCount": 2,
      "objectiveWeight": 60,
      "subjectiveWeight": 40,
      "weightWarning": false,
      "originalTotalWeight": 90
    },
    "meta": {}
  }
}
```

## 2. 向后兼容策略

- 新增字段 `originalTotalWeight` 仅做**增加**，不删除/重命名既有字段。
- `persistItems` 写入的 `ScoreItem.weight` 改为归一化后的值；`summary.totalWeight` 由 items 聚合，随之为 100。
- FR-007：前端不做本 feature 必须的展示改动；是否呈现"原合计 X 已补齐"由产品确认文案后择机落地。