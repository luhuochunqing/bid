# Data Model: 059

## 新增枚举与值对象

### ScoreParseDecision（domain，纯枚举）
- `AUTO_APPROVED`
- `REVIEW_REQUIRED`
- `STRUCTURE_INCOMPLETE`
- `PARSE_FAILED`

```java
public enum ScoreParseDecision {
    AUTO_APPROVED, REVIEW_REQUIRED, STRUCTURE_INCOMPLETE, PARSE_FAILED
}
```

### PipelineVersion 常量
```java
public final class ScoreParsePipeline {
    private ScoreParsePipeline() {}
    public static final String LEGACY_FALLBACK = "LEGACY_FALLBACK";
    public static final String EVIDENCE_PIPELINE_V1 = "EVIDENCE_PIPELINE_V1";
}
```

## ScoreParseTask 新增列

| 列 | 类型 | 含义 |
|---|---|---|
| `pipeline_version` | VARCHAR(32) NULL | 解析 pipeline 标识：LEGACY_FALLBACK / EVIDENCE_PIPELINE_V1 |
| `decision_status` | VARCHAR(32) NULL | 解析结果决策：AUTO_APPROVED/REVIEW_REQUIRED/STRUCTURE_INCOMPLETE/PARSE_FAILED |
| `fallback_reason` | VARCHAR(128) NULL | 退路说明（如 RESORT_TO_REGEX / MISSING_ARTIFACT） |

回滚：删除三列（不影响现有 run_summary）。

## run_summary JSON 新增键（向后兼容）

```json
{
  "pipelineVersion": "LEGACY_FALLBACK",
  "decisionStatus": "REVIEW_REQUIRED",
  "fallbackReason": "RESORT_TO_REGEX"
}
```

旧字段（lanes/aligned/merge/items/finalTotalWeight）保持顺序不变。

## Phase 5 数据库形态（不在 Phase 0 实施）

- 新表 `score_parse_fact`：`fact_id`、`task_id`、`source_table_id`、`source_cell_ids` (JSON)、`source_text`、`code`、`dimension`、`weight`、`weight_evidence` (JSON)、`parent_fact_id`、`extraction_method`、`confidence`、`validation_status`、`created_at`。
- 新表 `score_parse_evidence`：`evidence_id`、`fact_id`、`kind`、`cell_id`、`quoted_text`、`start_offset`、`end_offset`、`explanation`。
- 新表 `score_parse_review`：`review_id`、`task_id`、`decision_status`、`fallback_reason`、`reviewed_by`、`reviewed_at`、`audit` (JSON)。
- `score_item` 追加列：`source_cell_ids` (JSON NULL)、`weight_evidence` (JSON NULL)、`extraction_method` (VARCHAR(32) NULL)、`confidence` (DECIMAL(4,3) NULL)、`validation_status` (VARCHAR(32) NULL)、`parent_score_item_id` (BIGINT NULL)。
