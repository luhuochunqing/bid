---
title: 项目 38 和 39 的 OCR 评分表解析与人工采纳校验
space: engineering
category: lessons
tags: [scoreparse, baidu-ocr, manual-adoption]
created: 2026-11-18
updated: 2026-11-19
health_checked: 2026-11-19
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
- 2026-11-19 E2E (!3055) 复查追加修复（PR2 scoreparse）：
  1. `ScoreCategoryClassifier` / `ScoreItemTitleExtractor` / `DeterministicScoreFactExtractor` / `OriginalScoreEvidencePolicy` 保留未闭合括注（如 `"商务评分（20"`）直至名称恢复。从竖线分隔的原始行抽取标题时跳过序号列（如 `A1`）。未从原文恢复具体标题的截断项无法发布。
  2. 复核卡片与 `ScoreReviewAdoptionPolicy` 按实际采纳的计分单元拦截空、纯序号（含中文序号）、未闭合括号、含分隔符及 `"评分项 N"` 兜底名称；`ScoreItemPersistenceService` 保留人工采纳确认的细则名称并拦截首行大类兜底；`ScoreExtractionSnapshot` 与 `ScoreVersionActivationService` 对新旧快照同步阻止空/无效名称标记可计算或激活落库。
  3. `ScoreParseReviewFactMapper` 保留证据中的 `scoreType=(OBJECTIVE|SUBJECTIVE)` 标注（避免被规则文本覆写），`ScoreExtractionPanel.vue` 展示客观项/主观项类型标识。
  4. `BaiduTableArtifactEnricher` 保留 `BAIDU_OCR_PAGE_FAILED` 与原生解析结果。评分表分类、页码和单元格只能证明存在评分表片段。表头页和仅保留一格的续表页都有失败诊断回归覆盖。
  5. 同一格中引文和规则均不同的等分细则可分别计分。占位名仅触发标题恢复和发布校验。旧版覆盖在候选名称解析后仍无效时整批拒绝，保留既有正式项和历史打分。历史快照的不计分汇总行不妨碍有效明细自动激活。

相关排查见 [[scoreparse-subjective-naming-and-recheck-dedup-rca]]。
