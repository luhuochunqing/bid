---
title: 项目1124评分项假多方案、纳入按钮无响应与复核同格克隆根因及修复
space: engineering
category: lessons
tags: [scoreparse, scheme-normalization, deduplication, review-ui, prod-1124]
created: 2026-11-16
updated: 2026-11-16
health_checked: 2026-11-16
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewFactMapper.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceFactIdentity.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalRuleRestoration.java
  - src/views/Project/stages/components/ScoreCandidateReviewCard.vue
backlinks:
  - _index
---

# 项目1124评分项假多方案、纳入按钮无响应与复核同格克隆根因及修复

## 现场证据（生产 `parseTaskId=242`，`traceId=e427cf6fa2804e1087c1097151d7bc59`）

1. 前端复核卡片提取 61 条事实但候选 0 项（0 分），全部落入底部「当前方案外或来源未验证事实」，提示「文档包含多个评分方案，请先选择一个方案」；点击「+ 纳入本方案」无任何界面变化。
2. 人工选中方案采纳后生成 35 项 / 108 分，其中 15 项规则详情显示 `—`。
3. 生产日志证实：百度 OCR 已成功识别第 26–36 页表格，但校验阶段触发 `ORIGINAL_SCHEME_AMBIGUOUS`（列出 `第四章：谈判及评审方法`、`第四章谈判及评审方法` 及 `3 形式评审标准...` / `8 资格评审标准...` / `11 响应性评审标准...`），导致 `OriginalScoringContext.resolve` 短路返回 `declaredTotal=null`；随后局部复核 `SourceRecheckPlan` 追加 `f1/f2/f3` 未被合并，日志报 `DUPLICATE_SOURCE`。

## 致错链

1. **假多方案**：`OriginalScoringSchemePolicy.CHAPTER` 未剥离章节号后的冒号（`第四章谈判及评审方法` vs `第四章：谈判及评审方法`），`isValidSchemeRoot` 未排除初步评审表行，且 `OriginalScoringTextScopePolicy` 未替换正文列举清单占位行。
2. **「+ 纳入本方案」静默无反应**：`manualScoreCandidate.js` 在 `schemeCount > 0 && !schemeId` 时恒返回 `false`，`ScoreCandidateReviewCard.vue` 未向用户提示需先选方案。
3. **复核克隆逃逸与单格置空**：`SourceFactIdentity` 对引文微差/换行格式差异的复核克隆返回 `false`；`OriginalRuleRestoration` 在唯一闭合单元格因 OCR 单字差异未命中 `uniqueSpan` 时未回退单元格原文，置空为 `""`。

## 修复边界

- 章节号后剥离标点冒号、排除初步评审表行成为方案根、以正式章节替换相邻列举清单占位行；`schemeFrom` 复用已解析的 `IN_SCHEME` 正文作用域标题，避免清单标题与正文标题生成不同方案 ID 而过滤总分。`ScoreParseReviewFactMapper` 在存在 `IN_SCHEME` 事实时仅排除全部事实均为 `OUT_OF_SCHEME` 的方案，保留含 `SOURCE_UNVERIFIED` 事实的真实方案及原有顺序，要求用户明确选择，避免待验证事实被自动归入其他方案；不存在 `IN_SCHEME` 事实时保留全部方案的人工恢复入口。混合状态回归测试覆盖已验证方案在前和待验证方案在前两种顺序，且待验证事实保持不可自动采纳。
- `ScoreCandidateReviewCard.vue` 在未选方案时点击「+ 纳入本方案」弹出明确提示并拒绝静默修改（不猜方案）。
- `SourceFactIdentity` 在同方案、同来源单元格和同文档身份前提下，要求规则文本经 `EvidenceText.normalize` 归一化后相等，或引文相同且双方均声明同一完整行，才合并克隆；仅规则包含关系不足以合并，独立子项仍保留。`OriginalRuleRestoration` 在唯一闭合无子项共享单元格回退保留原文。
