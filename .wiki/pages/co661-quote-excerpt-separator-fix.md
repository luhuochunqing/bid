---
title: CO-661 评分引用门禁：节选分隔符导致跨段引用被误杀
space: engineering
category: lessons
tags: [scoreparse, quote, 引用校验, 评分, CO-661, 节选, EvidenceText]
created: 2026-11-13
updated: 2026-11-13
health_checked: 2026-11-13
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreAssessmentGuard.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreDocExcerptExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/EvidenceText.java
backlinks:
  - _index
---

# CO-661 评分引用门禁：节选分隔符导致跨段引用被误杀

## 结论

客观项「可核验证据不足」并非都是模型问题。`ScoreDocExcerptExtractor` 用 `\n\n---\n\n` 拼接选中的段落片段，`EvidenceText.normalize` 保留 `---` 并在两侧补空格，而原有 `ScoreAssessmentGuard.verifyQuote` 要求 quote 在节选归一化文本中**连续命中**。当引用跨越该分隔符且不含分隔符、在节选其他位置也无连续匹配时，即使 `documentRawMatch=true`，仍会报 `QUOTE_NOT_IN_EXCERPT`。

## 现场证据（测试环境 6397b775d，项目 1364）

- task `909e83ef`：`QUOTE_NOT_IN_EXCERPT` ×4，客观项 0 出分。
- 日志：`itemId=501 rawScore=5.0 quoteMissing=false quoteLength=298 documentRawMatch=true reason=QUOTE_NOT_IN_EXCERPT`（引文逐字存在于原件，仍被拒）。
- `itemId=497/498/499`：`documentRawMatch=false`，属模型复述，另一类问题。

## 修复

`verifyQuote` 增加回退：精确匹配失败后，去掉节选分隔符（`\s*-{3,}\s*` → 空格，再归一化）判断 quote 是否仍属于节选；属于则回退到全文归一化定位，返回 document 原文片段。`QUOTE_NOT_IN_EXCERPT` 语义不变（引用来自节选之外仍然拒绝）。

## 验证

- 新增 4 个用例：跨分隔符中文/英文引用可验证、西文词界不粘连、分隔符不能把不在原件中的引用判为通过。
- `ScoreAssessmentGuardTest` 16/16 通过（本轮清理前执行 `cd backend && mvn -o -q -Dtest=ScoreAssessmentGuardTest test`，失败/错误/跳过均为 0）。
- 全量 `scoreparse` 基线 11 个失败与未改动 HEAD 一致（环境/数据问题），非本次引入。

## 后续

- 模型复述类（`documentRawMatch=false`）仍需在提示词或模型侧收敛，见 Linear CO-661。
- 部署后需用项目 1364 重跑实际打分复验 A1 项。

## 补充：A1 项的第二类根因（2026-11-13 部署 4d99c0d2a 后复验）

- OMS 项已真实出分（6 分）；A1 仍 `QUOTE_NOT_IN_EXCERPT`，但 `documentRawMatch=true`、quoteLength=298。
- 排除节选覆盖：该项目投标 PDF 仅 805 字，节选等于全文（1 个 segment），引用区间完整落在节选内；离线抄原文仍失败。
- 根因是 `EvidenceText.normalize` 的上下文相关规则（行 48：句号前为数字且后接汉字时转 `.`）。引用以「…2027-12-31。」结尾时末尾无后继字符，保留 `。`；文档中该句号后紧跟「第四章」，被归一化为 `.`，仅末 1 字符不同即整体判负。
- 修复：`verifyQuote` 增加原文精确命中快速路径——`excerpt` 与 `document` 同时包含原始 quote（≥8 字）时直接 VERIFIED，再回退到归一化路径；不放松“引用必须来自节选与原件”的门禁。

## 补充二：第三类根因——换行拆断编号 + 句尾标点（部署 85dd4ee1d 复验）

- 部署后 A1 仍失败，日志两次 `quoteLength=298/289`、`documentRawMatch=false`：298 为原件带换行原文，289 为模型去掉换行后的连续文本。
- 固定宽度 PDF 换行把编号硬拆（`2022S\nR0445566`），`EvidenceText.normalize` 在两个拉丁/数字字符间补空格，模型连续写法没有该空格 → 归一化不命中。
- 句尾另有上下文依赖：引用结尾「…31。」无后继字符保留 `。`，文档中后接「第四章」被转 `.`。
- 修复：`verifyQuote` 在分隔符回退之后增加空白无关回退——去掉全部空白匹配；句尾再试一次补汉字哨兵后的归一化结果；命中后用 offset 映射回原文切片。仍要求命中节选。
- 验证：真实 A1 文本（joined 289 字）离线验证 `verified=true`，返回原件 298 字切片；`ScoreAssessmentGuardTest` 19/19。
