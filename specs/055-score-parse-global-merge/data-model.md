# Data Model: 055

## 既有实体（只读复用，无迁移）

### ScoreCandidate（record，domain）
归并输入与输出载体。字段：`code, dim, detail, weight(BigDecimal), scoreTypeGuess, contextNote, sourceText, location, semanticPattern`。

### DocumentChunk（record，docinsight.domain）
`text + sectionPath`。ScoreTableAwareChunker 的输入/输出类型。

### ScoreItem / score_item 表
落库终点，字段不变。归并不改变持久化契约。

## 新增契约

### ScoreGlobalMergeOutput（infrastructure/openai，Jackson 绑定）

```json
{
  "items": [
    {
      "code": "S9 | null",
      "dim": "技术服务部分",
      "detail": "评分条款逐字原文（必须能在章节原文中找到）",
      "weight": 1,
      "scoreTypeGuess": "OBJECTIVE | SUBJECTIVE",
      "sourceText": "该项对应的原文片段"
    }
  ],
  "summary": {
    "mergedGroupCount": 3,
    "discardedCandidateCount": 2,
    "notes": "一句话决策说明"
  }
}
```

约束：`items[].detail` 非空且须 ⊆ 章节原文（守卫强制）；`weight` 数字或 null；未知字段忽略。

### ScoreMergeResultGuard.Outcome（domain record）

```java
record Outcome(List<ScoreCandidate> accepted, List<ScoreCandidate> restored,
               int hallucinationDropped, int mergedGroupCount) {}
```

### ScoreParseAppService 编排状态流

```
四路召回 ∪ 召回五(表格行) → mergePolicy.merge → ghostFilter → filterInvalidWeights
  → [候选≥2?] 全局归并(Executor) ─失败→ 启发式结果直通
                                  └成功→ Guard(幻觉过滤+漏项回补)
  → WeightSumCheck / ItemCountCheck → gapRecheck → trim → normalize → persist
```

## 校验规则汇总

| 规则 | 落点 | 失败行为 |
|------|------|----------|
| 归并 detail ⊆ 章节原文 | Guard 规则 1 | 丢弃该输出项 + 计数 |
| 高置信候选未被覆盖 | Guard 规则 2 | 回补进最终池 |
| 候选 <2 跳过归并 | Executor 前置 | 返回 empty（=启发式结果） |
| LLM 两连败 | Executor | 返回 empty，记降级日志 |
| weight 无法解析 | 既有 filterInvalidWeights | 丢弃（不变） |
| 合计≠100 | 既有 WeightSumCheck→recheck | 回补扫描（不变） |
