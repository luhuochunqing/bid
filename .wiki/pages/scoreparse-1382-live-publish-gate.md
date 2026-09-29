---
title: 项目1382线上100分被0分指引和空模型总分否决
space: engineering
category: debugging
tags: [scoreparse, 1382, publication-gate]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreWeightEvidencePolicy.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/LiveScorePublicationGateTest.java
backlinks:
  - .wiki/pages/scoreparse-1382-post2767-runtime-rca.md
created: 2026-09-28
updated: 2026-09-29
health_checked: 2026-09-29
archive: false
---

# 项目1382线上100分被0分指引和空模型总分否决

## 结论

测试机项目 1382 在 2026-09-28 的三次重新解析里，叶子合计和原文总分都已经是 100，正式评分项仍是 9 月 27 日人工采纳的 91 分。发布门禁只要还剩一条违规，就不替换 `score_item`。

前几轮 PR 的全链路测试在进入解析器之前做了四件线上不会发生的事：删掉表号为空的事实、把引文改成短句、把模型总分写成 100、把复核标记成已经完成。所以测试能自动通过，页面不变。

## 线上共同否决与 Task 290 根因复盘

- 「见评审办法前附表」和报价公式说明被收成权重 0 的方案内事实，原文里没有「0分」。
- 抽取提示允许模型把 `declaredTotal` 留空；原文总分已是 100 时，留空仍被记成 `MODEL_TOTAL_MISSING`。
- 模型把「商务 / 技术 / 报价」写进 dimension，所引行的分值已经对上，仍被记成 `ITEM_SOURCE_SCOPE_MISMATCH`。
- 紧凑复核三次都在调用模型前抛 `IllegalStateException`，第一次抽取的结果被留下。
- **Task 290 关键阻断（2026-09-29）**：
  1. **父项编码歧义降级（0权重说明句 vs 15分正式父项）**：模型在抽取大纲说明文本与表格项时，将说明行（如“3.2.3(1) 商务评分标准说明...”）与正式父项同时赋予相同编码，由于原本按纯编码做唯一性校验，两者冲突触发 `\u0000AMBIGUOUS\u0000` 标记，导致所有商务子项在查找父项时降级失败；修复方案：在 `MultimodalFactParentCodeNormalizer` 中实施“正权重优先”防歧义规则。
  2. **0权重合同项与表外说明项范围逃逸**：`MultimodalExtractionReviewPolicy.assignScope` 因过早判断 `!fact.scopeStatus().isBlank()`，导致分片盖章的表外事实逃逸了 `OUT_OF_SCHEME` 兜底守卫；修复方案：前置范围守卫截断逃逸，同时在 `OriginalScoreQualificationPolicy` 中放行 0 权重 `OUT_OF_SCHEME` 项的严格逐字引文比对。
  3. **0权重非计分项证据误报**：`OriginalScoreEvidencePolicy` 对 0 权重事实放宽权重单元格强校验，避免非计分项误报单元格缺失。

91 分是任务 275 的 10 条：报价父项 40 和子项 28、12 一起计入，其余只有网点 3、财务 1、两个服务方案 3 和 4。

## 发布规则与 Task 290 回放基准

非正权重且不是父项的事实仍记录资格，但不进入可发布集合，也不能否决已闭合的叶子。原文总分已核验时，模型总分为空不再否决；模型总分和原文总分不一致仍然拒绝。只有 dimension 是父类词、并且所引行分值与声明权重一致时，才不记范围不匹配。具体项名对到另一行仍然拒绝。发布名称取原文标题「名称（N分）」，去掉前面的父类词。

端到端验收基准以现场从服务器提取的 Task 290 真实 51 项无清洗事实为准（`Project1382FullPipelineIntegrationTest.verifyTask290LiveOnlineFacts`），验证通过全链路 `AUTO_APPROVED` 判定，14 项有效评分项（13 项叶子项 + 1 项扣分清单规则项，总分 100.00 分）自动持久化落库。

