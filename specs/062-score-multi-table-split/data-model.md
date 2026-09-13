# Data Model: 多表评分标准自动拆分（062）

> 本任务**不新增数据库字段/表**（见 spec Assumptions：不加 score_item 评分标准维度字段）。
> 所有模型为纯核心不可变值对象（FP-Java record），仅存在于内存与 `run_summary` JSON 审计文本中。

## 领域模型

### ScoreFact（既有，分组输入）

- 关键字段：`sourceTableId`（来源评分表 ID，**分组键**）、`factId`（含 tableId 前缀，跨表唯一）、
  `parentFactId`（父子结构）、`weight`/`weightEvidence`（权重与证据）、`validationStatus`
  （AUTO_APPROVED / REVIEW_REQUIRED / STRUCTURE_INCOMPLETE / DUPLICATE）。
- 角色：按 `sourceTableId` 拆分为多组；`toCandidate()` 仅对 AUTO_APPROVED 状态返回落库候选。

### DeclaredTotalEvidence（既有，按表过滤）

- 关键字段：`value`（声明总分）、`tableId`（**归属键**）、`row`、`sourceCellId`、`quotedText`。
- 角色：`ScoreTableSplitPolicy` 按 `tableId` 归属到对应评分表；归属不到任何评分表的证据丢弃。

### ScoreCompletenessResult（既有，逐表复用）

- 关键字段：`complete`、`decision`（ScoreParseDecision）、`fallbackReason`、`declaredTotalScore`、
  `leafWeightSum`、`leafFacts`、`parentFacts`、`defects`。
- 角色：每张独立评分表调用一次 `ScoreCompletenessPolicy.evaluateCompleteness` 产出一份。

### TableCompleteness（新增，纯核心 record）

```text
record TableCompleteness(
    String tableId,              // 来源评分表 ID；无证据组合成 "NO_DECLARED_TOTAL"
    boolean hasOwnDeclaredTotal, // 是否被识别为独立评分标准（本表存在结构化总分证据）
    ScoreCompletenessResult completeness
)
```

### TableSplitResult（新增，纯核心 record）

```text
record TableSplitResult(
    List<TableCompleteness> tables,            // 每张独立表一份（含无证据组）
    ScoreCompletenessResult documentLevel      // 文档级聚合（决策取最差）
)
```

## 状态转换（任务级决策）

文档级决策聚合优先级（最差优先）：

```text
PARSE_FAILED > STRUCTURE_INCOMPLETE > REVIEW_REQUIRED > AUTO_APPROVED
```

落库不受文档级决策阻塞：任一表 `AUTO_APPROVED` 即落库该表候选；任务级 `decisionStatus`
仍写文档级聚合值（保持既有字段语义）。

## 持久化

| 表 | 变更 | 说明 |
|----|------|------|
| `score_item` | 无 | 通过表的候选经既有 `ScoreItemPersistenceService.persistItems` 落库 |
| `score_parse_fact` / `score_parse_evidence` | 无 | 全部事实（含未通过表）仍持久化，供复核 UI 使用 |
| `score_parse_task.run_summary` | 无 schema 变更 | JSON 增加 `tableSplits` 键：`[{tableId, declaredTotal, decision, fallbackReason}]` |

## 不变式

- 每张独立评分表的 `leafWeightSum` MUST 等于该表 `declaredTotalScore` 才算 `AUTO_APPROVED`。
- 无独立总分证据的表 MUST 合并为单组校验（正文/默认 100 兜底），单表无证据时行为与现状一致。
- 结构破损表 MUST 按 `STRUCTURE_INCOMPLETE` 单独标记，不产出落库候选。
- 落库候选 MUST 全部带单元格证据（`sourceCellIds` + `weightEvidence`），满足
  `ScoreItemPersistenceService.persistFactsAndEvidence` 的拒绝无证据自动通过项约束。
