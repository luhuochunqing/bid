# Contract: LLM 全局归并裁决输出 Schema

> 演进自 `specs/041-ai-score-parse-backend/contracts/llm-output-schema.md`。新增契约 §5，不修改既有 §1-§4。

## §5 GlobalMergeOutput（spec 055 新增）

调用点：`ScoreGlobalMergeExecutor.resolve(pool, sectionText)`，单次结构化输出。

### 输入（prompt 组装）

| 段 | 内容 | 上限 |
|----|------|------|
| 候选池 JSON | `[{code,dim,detail(截断200字),weight}]` 数组，带池内索引 | 池全量 |
| 章节原文 | MarkdownScoreSectionLocator 定位内容拼接；失败 fallback 全文前缀 | 12000 字符 |

### 输出字段

```text
items[]: 最终评分项清单（归并后的完整集合）
  code:           string|null — 沿用候选编号或章节原文编号
  dim:            string      — 大类归属（从候选池已有 dim 词汇中选择或规整表述）
  detail:         string      — 该项评审细则逐字原文；必须能在"章节原文"中逐字找到
  weight:         number|null — 满分分值
  scoreTypeGuess: "OBJECTIVE"|"SUBJECTIVE"|null
  sourceText:     string      — 对应原文片段

summary:
  mergedGroupCount:        number — 本次合并掉的重复候选组数
  discardedCandidateCount: number — 判定不属于评分项而丢弃的候选数
  notes:                   string — ≤100 字决策说明
```

### 硬性规则（写入 prompt）

1. 只允许从候选池与章节原文中选取与重组，禁止发明任何候选池外的新评分项；
2. 同一条款的多次召回（含截断残缺版）必须归并为一项，detail 取最完整逐字版本；
3. 各自包含完整分值裁决（如"得X分""不得分"）的独立条款必须保持独立，禁止合并；
4. detail 一律逐字摘自章节原文，禁止摘要、改写、补写；
5. 输出 items 必须覆盖候选池中所有真实评分项，不得遗漏。

### 失败语义（Java 侧强制，非 prompt 层）

- 两连败 → empty → 启发式结果直通（fail-open）；
- 幻觉（detail ⊄ 原文）→ Guard 丢弃；
- 高置信漏项 → Guard 回补。
