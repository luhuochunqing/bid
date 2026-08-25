# 数据模型：竞品公司自定义输入与竞争对手库治理 (055)

## 变更总览

| 表 | 变更 | 说明 |
|---|---|---|
| `competitors` | 加 `status` 列 | 软停用（ACTIVE / DISABLED） |
| `competitors` | `name` 加唯一索引 | 幂等与并发防重的数据库兜底 |
| `competitors` | seed 24 行 | 迁移现有前端硬编码竞品 |
| `project_result_competitor` | 无变更 | 快照只存 `name`，不加外键 |
| `competition_analyses` | 无变更 | 合并时仅 UPDATE `competitor_id` 引用 |

## competitors（竞品全局字典）

现有字段保持不变，新增：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'ACTIVE'` | `ACTIVE` / `DISABLED`，软停用标记 |

索引变更：
- `idx_competitor_name`（现有，非唯一）→ 新增 `uk_competitor_name`（唯一），保留原普通索引或替换。

状态机：
```
ACTIVE ──(disable)──▶ DISABLED
DISABLED ──(无恢复路径，本期不做)──▶ ACTIVE
```

合并语义：合并时 `source` 置 `DISABLED`，`competition_analyses.competitor_id` 由 `source` 批量改为 `target`。

## 快照隔离（不变式）

`project_result_competitor.name` 为登记时刻的文本快照，与竞品字典解耦（无 `competitor_id` 外键）。
字典改名/合并/停用**不得**回溯改写快照（审计真实性，见 spec 边界与权衡）。

## seed 数据

迁移现有 `src/constants/competitors.js` 的 `COMPETITOR_COMPANY_OPTIONS`（24 个公司名）为
`competitors` 初始行，`status=ACTIVE`，其余字段（industry/strengths 等）留空。
