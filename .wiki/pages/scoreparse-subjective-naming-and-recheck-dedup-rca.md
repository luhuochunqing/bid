---
title: 项目38/39主客观跨行识别失效、大类覆盖具体细则与重试重复解析根因与修复
space: engineering
category: lessons
tags: [scoreparse, subjective-objective, title-extraction, recheck-dedup, prod-38, prod-39]
created: 2026-11-18
updated: 2026-11-18
health_checked: 2026-11-18
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTypeClassificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemTitleExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreCategoryClassifier.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemPersistenceService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceFactIdentity.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/SourceRecheckPlan.java
backlinks:
  - _index
---

# 项目38/39主客观跨行识别失效、大类覆盖具体细则与重试重复解析根因与修复

## 现场证据

1. **项目 38（襄阳航泰动力机器厂网采项目）**：
   - 生产任务评分项“能够提供最长账期”（5分）等客观评分项被识别为主观题 `SUBJECTIVE`；
   - 评分项名称直接显示为大类名“商务”，未能提取细则标题“能够提供最长账期”。
2. **项目 39（中国烟草总公司广西壮族自治区公司采购项目）**：
   - 评分项解析出现重复项，例如“交货期限承诺”解析出两次（一次名称为“技术”，一次为“交货期限承诺”），总分虚高膨胀至 135 分。

## 根因剖析

1. **主客观识别跨行失效**：标书表格经 OCR/文本提取后包含大量换行符 `\n`。`QUALIFIED_NUMERIC_PATTERN` 原先缺少 `Pattern.DOTALL` 标志，导致只要数字与量化词跨行即失效；同时缺少商业高频量化指标（账期、承兑、仓库面积、送达时限）。
2. **细则标题被泛大类吞噬**：表格无子项列时模型倾向于将“商务”填入 `dimension`。`resolveDim` 在遇到非冒号陈述句时未能从 `detail` 提取具体短标题，使得所有项均被降级显示为“商务”。
3. **重试结果重复追加与 135 分膨胀**：`SourceRecheckPlan.build` 仅将违规项放入 `targets`。重试模型重扫文本输出非目标项时，`merge` 判定 `previous.isEmpty()` 并误判为新项追加；同一单元格先后被赋予“技术”与“交货期限承诺”不同代号时，`SourceFactIdentity.sameUnit` 过于严苛未认定为同单元格别名。

## 系统性修复策略

1. **主客观正则强化（`ScoreTypeClassificationPolicy`）**：
   - 正则增加 `Pattern.DOTALL` 支持跨换行匹配；
   - 扩充“账期”、“月/个月（含）以上”、“承兑”、“承诺”、“小时（含本数）内”、“仓库/仓储面积/㎡/m2/m²”、“大于/小于”等硬性客观指标。
2. **首行细则标题提取与大类识别解耦（`ScoreItemTitleExtractor` & `ScoreCategoryClassifier`）**：
   - 支持首行无冒号短标题提取与跨换行窄列折行短词拼接；
   - 建立领域层纯核心分类器 `ScoreCategoryClassifier.isTopLevelCategory`，集中管理“商务/技术/价格/综合”等大类判断；
   - 在 `ScoreItemPersistenceService` 与 `ScoreReviewCandidateFactMapper` 中，当细则存在可提取标题时强制覆盖泛大类。
3. **重试覆盖与名称升级机制（`SourceRecheckPlan` & `SourceFactIdentity`）**：
   - 同单元格不同大类维度认定为同单元格别名；
   - `SourceRecheckPlan.merge` 检测非目标单元格，杜绝重复追加；若原项为泛大类而重试输出了具体细则名，原位升级名称并保留证据链，杜绝分数膨胀。

## 本地验证

- `ScoreTypeClassificationPolicyTest`（33 项全绿）：覆盖账期、承兑、仓库及交货承诺客观用例；
- `ScoreItemTitleExtractorTest`（9 项全绿）与 `ScoreItemPersistenceServiceTest`（12 项全绿）：覆盖首行无冒号短标题与大类覆盖逻辑；
- `ScoreCategoryClassifierTest`（31 项全绿）：覆盖大类识别与具体细则标题区分；
- `SourceRecheckPlanTest`（12 项全绿）与 `SourceFactDeduplicationTest`（9 项全绿）：验证重试不重复追加及大类名称升级；
- `GoldenCasesReplayTest#subjectiveAndPaymentPeriodNamingFixtureRetainsLeafTitleAndAvoidsDedupInflation`：金标回放测试全绿通过；
- `ScoreParseArchitectureTest`：DDD 架构与依赖规则 100% 验证通过。
