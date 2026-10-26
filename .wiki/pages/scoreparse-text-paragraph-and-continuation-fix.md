---
title: 纯文本段落评分事实穿透还原与表格跨行续行识别契约
space: engineering
category: architecture
tags: [scoreparse, 评分解析, 文本穿透, 表格折行, 证据还原]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnit.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestoration.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceSpanResolver.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-26
updated: 2026-10-26
health_checked: 2026-10-26
archive: false
---

# 纯文本段落评分事实穿透还原与表格跨行续行识别契约

## 业务痛点与技术现场

在项目 43（中粮集团综合消费品招标文件）AI 评分标准解析中，招标文件声明总分 100 分，但页面仅解析出 61 分，丢失 39 分。
排查定位到两处底层解析与还原机制缺陷：

1. **纯文本段落引用丢失（缺陷 A，丢失 30 分价格条款）**：
   - 商务报价条款位于招标文件正文段落第 673~695 行（`text:line[693]`），不在 `tables` 中。
   - `SourceSpanResolver.resolve` 仅检索 `artifact.tables()` 与 `artifact.textBlocks()`，导致未还原出 `restored.text`（返回空字符串）。
   - 空字符串触发 `TextPrimaryScoreFactMapper` 的 `!restored.text().isBlank()` 门禁，将 30 分合法价格条款降级为 `REVIEW_REQUIRED` 且被 candidate 提取过滤。

2. **表格跨行续行识别过严（缺陷 B，丢失 5 分商务业绩与 4 分技术团队）**：
   - 表格中评审标准较长发生跨行折行，相邻续行仅第 2 列（评审标准列）有文本，分值列（第 3 列）和序号列（第 0 列）为空。
   - `LogicalRuleSourceUnit.isContinuationRow()` 原先死板假定续行文本必在最后一列 `maxCol`，导致中间列折行判定失败，无法合并为逻辑行，规则还原同样置空降级。

3. **短 quote 截断编号材料退化风险**：
   - 在支持纯文本正文引用穿透还原时，如果事实仅引用了短 quote（如“方案最高3分。”）而模型提取的规则包含后续编号材料（如“1、须提供合同首页... 2、须提供验收证明...”），盲目以短 quote 覆盖 `restored.text` 会导致编号材料被抹杀。

## 核心架构契约

### 1. SourceSpanResolver 兜底 spans 穿透机制
- 重载 `resolve(DocumentArtifact artifact, List<String> ids, List<SourceSpan> fallbackSpans)`。
- 当 artifact 的表格与 textBlocks 无法解析正文 locator 时，从 facts 绑定的 `fallbackSpans` 中按 locator/sourceId 提取，确保纯文本段落事实能成功还原 spans。

### 2. OriginalRuleRestoration 编号材料防截断守卫
- 纯文本正文引用无表格物理行边界约束，通过 `erasesNumberedMaterials` 正则检测：
  `(?m)^\s*(?:(?:\d+|[一二三四五六七八九十]+)[、.）\)\]]|[（\(\[]\d+[）\)\]])`
- 当模型规则包含后续编号条款，而原文 quote 仅为局部短引用且未覆盖编号材料时，置空 `restored.text`，回退至 `MODEL_CLAIM` 并标记 `FULL_RULE_UNIT_UNRESOLVED`，严禁截断擦除后续材料要求。

### 3. LogicalRuleSourceUnit 中间列续行与表头角色扩展
- 扩充表头 `ID`、`NAME`、`SCORE` 角色正则，支持常见招投标表头（条款号、条目、评审内容、评分内容、评审要素、指标名称、权重、满分等）以及空白/换行清洗。
- 重构 `isContinuationRow`：支持结构列为空且包含正文的中间列续行判定，并在 `logicalRow` 中双向合并续行，形成完整逻辑行单元。

## 验证基线

- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnitTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestorationTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/application/FullRuleSourceReplayTest.java`
