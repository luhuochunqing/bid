# Contract: LLM 结构化输出 Schema

复用 `OpenAiStructuredOutputService`（json_schema 优先，BadRequest 自动降级 ResponseFormatJsonObject + prompt 包装）。所有用户/文档内容过 `TenderIntakeTextProcessor.sanitizeUntrusted`。

## 1. 评分项候选池提取（召回三/四 + 结构化）

`ScoreCandidateOutput`（每次 chunk 调用返回）：
```json
{
  "candidates": [{
    "code": "A1",
    "dim": "技术方案",
    "detail": "完整原文表述，禁止摘要",
    "weight": 10,
    "scoreTypeGuess": "SUBJECTIVE",
    "contextNote": "注：得分不超过 X 分",
    "sourceText": "对应原文片段",
    "location": "P47 评分办法表 第3行",
    "semanticPattern": "CONDITION_TO_SCORE | QUANTITY_TO_SCORE | GRADE_TO_SCORE | METRIC_TO_SCORE | NONE"
  }]
}
```
- `weight` 无法解析为数字时输出 null（后续 Validation 层丢弃该项并记日志，spec Edge Cases）
- `semanticPattern` 供召回三语义特征聚合与去重加权

## 2. 完整性回补扫描（完整性校验 Agent，按需触发）

`ScoreGapRecheckOutput`：
```json
{
  "missedItems": [ { "同 ScoreCandidateOutput.candidates 元素" } ],
  "checkedZones": ["footnotes", "table-remarks", "cross-page-refs"]
}
```

## 3. 客观/主观判定（独立分类调用，FR-003）

`ScoreTypeClassificationOutput`：
```json
{
  "classifications": [
    { "code": "A1", "scoreType": "SUBJECTIVE", "reason": "描述性要求：方案合理性" },
    { "code": "D2", "scoreType": "OBJECTIVE", "reason": "量化条件：具备 CMMI 5 级证书" }
  ]
}
```

## 4. 阶段 2 投标文件对标打分（每评分项一次调用）

`ScoreAssessmentOutput`：
```json
{
  "actualScore": 3,
  "matchRatio": 60,
  "evidence": "标书已补充 CMMI 3 级证书说明及替代方案，部分满足要求",
  "quote": "我方虽未取得 CMMI 5 级认证，但已通过 CMMI 3 级认证...（第 3.2 节，P15）",
  "quoteMissing": false,
  "missedReason": "CMMI 5 级认证未找到匹配证书",
  "suggestion": "建议尽快启动 CMMI 5 级认证评估流程"
}
```
- `quoteMissing=true` 时 `quote` 置 null（前端显示"标书引用：无"）
- 主观项调用仅生成 `suggestion`，`actualScore` 强制 null（模型若输出数字，后端丢弃）

## 5. 后端守卫（domain 纯核心，不信任模型数值）

| 守卫 | 规则 | 违规处置 |
|---|---|---|
| `ScoreRangeGuard` | `actualScore ∈ [0, weight]` | 置 null + status=PENDING + log.warn（FR-016） |
| `PartialScorePolicy` | 部分分 = weight × ratio / 100，四舍五入，开区间 (0, weight) | 计算值取整后落库（FR-013） |
| `SubjectiveScoreGuard` | 主观项数字得分 | 强制 null（SC-003 零泄漏） |
| `WeightSumCheck` | 权重合计 vs 100 | 不阻断，标记 weightWarning（FR-022） |
| `ItemCountCheck` | 0 项 → 解析失败终态 | 任务 FAILED + 明确 message（FR-007） |

## 6. 反序列化容错

`ObjectMapper` 沿用 `FAIL_ON_UNKNOWN_PROPERTIES=false` 等宽松配置；字段级缺失容忍（null 走守卫/丢弃路径），保证半残输出不产生半残记录。
