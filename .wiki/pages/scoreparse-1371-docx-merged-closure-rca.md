---
title: 项目 1371 DOCX 嵌套纵向合并打分表行闭合与标书原文回填根因
space: engineering
category: debugging
tags: [scoreparse, docx, source-evidence, logical-rule, vmerge, rca, graceful-degradation]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnit.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestoration.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryScoreFactMapper.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreExtractionSnapshot.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SourceRuleEvidenceExpansion.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SourceItemNameRestoration.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnitTest.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestorationTest.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/TextPrimaryScoreFactMapperTest.java
  - docs/testing/scoreparse-holdout-benchmark.md
backlinks:
  - .wiki/pages/_index.md
  - .wiki/pages/scoreparse-cross-format-strategy.md
created: 2026-10-29
updated: 2026-10-29
health_checked: 2026-10-29
archive: false
---

# 项目 1371 DOCX 嵌套纵向合并打分表行闭合与标书原文回填根因

## 状态与现场证据

- 项目 1371（`SJZBJM08000026H101FV88 山焦物资焦煤易购工业品服务平台框架协议采购项目.docx`）在 Task 351 解析总分达到 100 分（14 项）。
- 用户界面中第 1~5 项及第 7~14 项的「标书原文」全部显示为 `—`，评分项名称退化为一级维度名称（“商务”/“技术”），仅第 6 项（20 分）能正常显示标书原文与项名。

## 致错机制与因果链

| 阶段 | 输入与行为 | 故障现象 |
|---|---|---|
| DOCX 表格网格化 | `doc0-t-0001` 中条款号列（`c0`）在大类下纵向合并（商务 `3.3(1)` 跨 6 行，技术 `3.3(2)` 跨 10 行）。除首行外，其余子项行的 `c0` 在文档中均为 `isMergedContinue = true, rawText = ""` | 子行 `c0` 无独立文本，但 `c2` 为独立评分项名称 |
| 行闭合证明 (`LogicalRuleSourceUnit.closed`) | `independent(...)` 校验下一行是否为独立行时，机械要求 `id && name`，其中 `id` 必须在当前行有非空文本 | 合并子行的 `c0` 文本为空，导致除第 6 项（其下一行刚好是技术大类首行 `c0="3.3(2)"` 非空）外，其余 13 项全部被误判为 `closed = false` |
| 表尾跨章闭合 | 表格末行（`r42`）跨章判断施加了 `unnumbered(headers)` 限制，有条款号列的打分表无法因跨入第四章表格而自然闭合 | 导致末行同样被判为 `closed = false` |
| 原文恢复熔断（全有或全无） | `OriginalRuleRestoration.restore` 遇到 `structural.applicable() && !structural.closed()` 触发硬中断，强制返回 `""`；`SourceRuleEvidenceExpansion` 和 `SourceItemNameRestoration` 均跳过处理 | 13 项的 `source_text` 被暴力抹空为 `""`，下游 `publishedDimension` 无法提取细则名称退化为一级维度 |
| 人工采纳入库 | 用户点击人工确认采纳后，`ScoreReviewAdoptionPolicy.adopt` 取 `detail = fact.sourceText()`，持久化层落库 `detail = " — "` | 页面展示 100 分但无标书原文，评分项退化为“商务”/“技术” |

## 根治方案与长效防线

1. **支持父级 ID 纵向合并行识别**：在 `LogicalRuleSourceUnit.independent` 中识别 `isId` 列为合并延续（`isMergedContinue = true`）且同行具有非合并的独立非空 `name` 列的情形，判定下一行为同一父级下的独立子评分项行。
2. **放开跨章表尾自然闭合**：移除跨章结束条件中的 `unnumbered(headers)` 限制，只要当前表与后续表已跨入不同章节，末行直接闭合。
3. **基于引用列隔离前置维度列**：在 `LogicalRuleSourceUnit.resolve` 过滤规则列时，严格排除排在当前引用列左侧（`column < minCitedCol`）的前置分类列。
4. **废除暴力清空，建立优雅降级机制（Graceful Rule Restoration）**：
   - 在 `OriginalRuleRestoration` 中，对非跨项歧义单元格（排除多兄弟事实共享同一个单表格单元格的情况）在结构未闭合时降级保留引用单元格原文（`citedRaw`）或模型规则文本（`modelRuleText`），并将 `closed` 标记为 `false`；
   - 在 `TextPrimaryScoreFactMapper` 中对 `!restored.closed()` 强制状态降级为 `REVIEW_REQUIRED`（防误采纳自动发布），诊断追加 `UNVERIFIED_CLOSURE` 与 `FULL_RULE_UNIT_UNRESOLVED`，并支持从完整规则提取叶子评审要素名称；
   - 在 `ScoreExtractionSnapshot` 中将规则来源判定与 `sourceText.isBlank()` 盲目判断解耦，未验证项如实标记来源为 `"MODEL_CLAIM"`。
5. **建立 25 份核心样本 Holdout 留出评测基准**：
   - 编写 `docs/testing/scoreparse-holdout-benchmark.md`，确立四大核心金标度量指标（叶子项 $F_1 \ge 92\%$、总分闭合率 $\ge 96\%$、证据溯源率 $\ge 95\%$、优雅降级率 $100\%$）；
   - 作为后续推进 Sidecar 表格网格自动平铺归一化、布局感知与统一网格契约演进的客观验收门禁与衡量标尺。
