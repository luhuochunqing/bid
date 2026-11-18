---
title: 项目 38 和 39 的 OCR 评分表解析与人工采纳校验
space: engineering
category: lessons
tags: [scoreparse, baidu-ocr, manual-adoption]
created: 2026-11-18
updated: 2026-11-18
health_checked: 2026-11-18
sources:
  - docs/exec-plans/active/scoreparse-38-39-plan.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/BaiduCrossPageTableLinker.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/GroupedCriteriaScoreTable.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewTotalPolicy.java
backlinks:
  - _index
---

# 项目 38 和 39 的 OCR 评分表解析与人工采纳校验

- 项目 38 的前附表误接到评分明细，导致分值列映射错误。按明细表头分开表格，只在有规则续行证据时接续同类表。
- 项目 39 将未勾选的方法识别成额外评分方案，且把总分公式当成评分项。按原文编号切分规则，把叶子项与原文分组总分对账。
- 人工采纳补齐正式写入前的校验。空原文、同源重复、已知漏项和总分冲突不得覆盖当前生效项。
- 真实 OCR 录制回放和本地 MySQL 读回确认，项目 38 为 17 项、项目 39 为 10 项，两案各 100 分。历史 135 分候选均被拒绝。该证据用于代码验收，生产存量需上线后重解析。

相关排查见 [[scoreparse-subjective-naming-and-recheck-dedup-rca]]。
