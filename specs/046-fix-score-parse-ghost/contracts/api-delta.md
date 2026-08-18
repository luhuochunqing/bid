# API Delta Contract: fix-score-parse-ghost

## GET /api/projects/{projectId}/score-parse/items 响应增量

`ScoreParseItemsDTO.Meta` 新增字段（末位）：

```json
{
  "items": [...],
  "summary": {...},
  "meta": {
    "sourceFileName": "...",
    "parseTime": "...",
    "bidFileName": "...",
    "scoreTime": "...",
    "lastParseStatus": "...",
    "lastParseError": null,
    "lastScoringOutcome": "...",
    "lastScoringHint": "...",
    "circuitOpen": false,
    "reusedItemCount": 0,
    "skippedChunks": 2
  }
}
```

- `skippedChunks`: `Integer | null`
  - `null` — 旧任务（加列前的历史数据）或未走 LLM 召回
  - `0` — 全部 chunk 提取成功（含重试成功）
  - `≥1` — 重试后仍失败被跳过的 chunk 数（解析质量警示信号）
- 语义：仅统计召回四（LLM 全文语义）的失败 chunk；正则/结构/语义路无此概念。
- 兼容性：增量字段，前端旧版本忽略未知字段，无破坏性。

## 行为变化（同端点，无签名变化）

1. items 列表顺序：由"召回首现序"改为"编号数值序"（1,2,3,4-1,4-2,...），`itemIndex` 连续重编 1..n。
2. `code` 字段：无编号项由假编号（"1"/"2"...）改为空串 `""`。
3. `summary.totalWeight`：幽灵项剔除后应回到 100 容差内（原 200 场景修复）。

## 无契约变化的部分

- 请求方法/路径/权限（`@PreAuthorize` 不动）
- POST parse / POST scoring / GET results 端点签名不变（内部行为增强）
