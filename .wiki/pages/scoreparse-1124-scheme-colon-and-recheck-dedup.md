---
title: 项目1124评分项假多方案、纳入按钮无响应与复核同格克隆根因及修复
space: engineering
category: lessons
tags: [scoreparse, scheme-normalization, deduplication, review-ui, prod-1124]
created: 2026-11-16
updated: 2026-11-17
health_checked: 2026-11-17
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceTableHeaderPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceScoreHierarchyPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/PdfFullRuleQuoteBinder.java
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

## 2026-11-17 后续回归：116分来源闭合（本地修复，未部署）

- 原件正文26–35页：商务20＋技术50＋价格30＝100，逻辑上同一评分方案。
- 任务246的116＝100＋重复22－物流配送漏计6；重复为财务/承诺6、供应商/绿色供应链8、运营服务8。不能把候选计数当成唯一评分单元数。
- 双层名称列、无表头续页列角色缺失、标点差异、同来源不同摘要、别名合并后父项覆盖相互放大。通过原件新OCR产物及持久化引文重建回放定位；未取得最初模型pool，不能冒充全模型重跑。
- `SourceTableHeaderPolicy`复用来源文档/方案/列布局；`SourceScoreHierarchyPolicy`恢复标签列边界内的父项；`PdfFullRuleQuoteBinder`仅接受完整文字/数字/运算符与明确行分值一致的唯一原文；`SourceFactIdentity`在完整来源单元与独立子项边界上去重。
- 原件回放当前37条、原文总分100、叶子100、无空正文；部署、真实模型重解析、采纳/读回尚未执行。正式生效仍是旧任务242，不得将本地回放说成历史数据已恢复。
- 合成回归：`SourceClosureRegressionTest`、`SourceClosureGoldenReplay`、`case-crosspage-source-closure.md/.expected.json`。

### 第1轮审查修复：层级作用域与来源隔离

- `SourceScoreHierarchyPolicy`原先逐列保留最后一个标签，商务组内的运营服务会残留到技术组。外层标签出现时，清除同列及更内层的旧标签；同一外层内的父子关系继续保留。
- `GroupedScoreTotalPolicy`原先只按方案标题汇总，三个独立文档的商务20、技术50、价格30会被拼成可信100。跨表推导现在要求所有参与表的`documentFor(tableId)`来源元数据一致（包含documentId和sha256）；多来源没有明确关系证据时返回未知，保留`ORIGINAL_TOTAL_UNKNOWN`诊断。同一文档跨页仍可闭合。
- `SourceClosureRegressionTest`新增回归先复现技术子项错挂与跨文档伪100，再验证修复；覆盖仅ID相同、仅摘要相同的拒绝场景，以及来源相同的多前缀表仍闭合。
