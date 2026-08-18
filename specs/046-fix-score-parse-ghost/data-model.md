# Data Model: fix-score-parse-ghost

## 表变更（一条迁移）

### score_parse_task 加列

```sql
ALTER TABLE score_parse_task
    ADD COLUMN skipped_chunks INT NULL COMMENT 'LLM 提取跳过的 chunk 数（FR-005，NULL=旧任务未统计）';
```

- 回滚：`ALTER TABLE score_parse_task DROP COLUMN skipped_chunks;`
- 实体映射：`ScoreParseTask.skippedChunks`（`Integer`，可空，无 NOT NULL 约束 —— 旧数据兼容）
- 版本号由 `bash scripts/new-migration.sh add-skipped-chunks` 分配，禁止手猜

## 候选池流转模型（纯内存，零持久化结构变化）

```text
四路召回 candidates
  → ScoreItemMergePolicy.merge            （现有，去重）
  → filterInvalidWeights                  （现有，weight 非数丢弃）
  → GhostCandidateFilter.filter           （新增 FR-001：幽灵滤除）
  → WeightSumCheck.checkCandidates        （现有，合计校验）
      ├─ total > 100.5 → WeightOverloadTrimPolicy.trim（新增 FR-002：嫌疑剔除）→ 重算
      └─ total < 99.5 → recheckGaps 补漏（现有，不动）
  → ScoreItemOrderPolicy.sort             （新增 FR-003：编号数值序）
  → persistItems（FR-004：code 空白落 ""，不再补行号假编号）
```

## 实体/DTO 变更

| 对象 | 变更 | 说明 |
|---|---|---|
| `ScoreParseTask`（entity） | + `Integer skippedChunks` | 列映射，可空 |
| `ScoreCandidate`（domain record） | 不变 | 9 字段维持 |
| `ScoreParseItemsDTO.Meta` | + `Integer skippedChunks` | 末位追加，不破坏现有序 |
| `RecallOutcome`（新 record） | `List<ScoreCandidate> candidates, int skippedChunks` | infrastructure/openai 包 |

## 校验规则（FR-006 不变量）

- GhostCandidateFilter 规则 2 要求 code 空 **且** sourceText 空 —— 有任一即保留（真实项保护）
- WeightOverloadTrimPolicy 只剔 无 code → 无 sourceText 两轮，剔尽仍 >100.5 保留全部
- ScoreItemOrderPolicy 不改 code 内容，仅重排顺序；itemIndex 由 persistItems 按新序 1..n 重编
