---
title: 项目 1371 DOCX 嵌套纵向合并打分表行闭合与标书原文回填根因
space: engineering
category: debugging
tags: [scoreparse, docx, source-evidence, logical-rule, vmerge, rca]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnit.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestoration.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SourceRuleEvidenceExpansion.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SourceItemNameRestoration.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnitTest.java
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
| 原文恢复熔断 | `OriginalRuleRestoration.restore` 遇到 `structural.applicable() && !structural.closed()` 触发硬中断，强制返回 `""`；`SourceRuleEvidenceExpansion` 和 `SourceItemNameRestoration` 均跳过处理 | 13 项的 `source_text` 被清空，项名保留为一级维度 |
| 人工采纳入库 | 用户点击人工确认采纳后，`ScoreReviewAdoptionPolicy.adopt` 将空白 `source_text` 写入 `score_item`，前端兜底展示为 `—` | 页面展示 100 分但无标书原文 |

## 根治方案

1. **支持父级 ID 纵向合并行识别**：在 `LogicalRuleSourceUnit.independent` 中识别 `isId` 列为合并延续（`isMergedContinue = true`）且同行具有非合并的独立非空 `name` 列的情形，判定下一行为同一父级下的独立子评分项行。
2. **放开跨章表尾自然闭合**：移除跨章结束条件中的 `unnumbered(headers)` 限制，只要当前表与后续表已跨入不同章节，末行直接闭合。
3. **基于引用列隔离前置维度列**：在 `LogicalRuleSourceUnit.resolve` 过滤规则列时，严格排除排在当前引用列左侧（`column < minCitedCol`）的前置分类列。
