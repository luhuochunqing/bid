---
title: 项目1391前附表附件评分表未入作用域与权重制总分缺失
space: engineering
category: debugging
tags: [scoreparse, docx, scope, weight-closure, appendix, diagnosis]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalWeightClosurePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidator.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/OriginalSourcePublicationGuard.java
  - backend/src/test/resources/scoreparse-golden/case-1391-appendix-weight-closure.md
  - docs/lessons/lessons-learned.md
backlinks:
  - .wiki/pages/_index.md
  - .wiki/pages/scoreparse-1387-chapter-list-scope-rca.md
created: 2026-10-22
updated: 2026-10-22
health_checked: 2026-10-22
archive: false
---

# 项目1391前附表附件评分表未入作用域与权重制总分缺失

## 诊断状态

- 触发：任务 322（测试项目 1391 / 生产项目 1122），国家电网办公类物资采购文件。
- 现象：8 次模型调用 `facts` 全空，任务落 `REVIEW_REQUIRED`，评分项 0 条。
- 修复合入见 Gitee PR !2844；本地验证通过，审查 verdict `MERGE_READY`。

## 已证实因果

| 边界 | 观察 | 机制 |
| --- | --- | --- |
| 作用域 | 正文 `### 6.3 评审办法` 只有 3 行进入 `IN_SCHEME` | `textScopes` 只把正文方案根当作用域；`## 响应供应商须知前附表附件一`（真实评分表）无任何 scope |
| 总分 | 原文无正文「本项目总分 100 分」 | 唯一总分行是前附表条款 `| 6.7.1 | 分值权重构成 (总分100 分) |`，`parseText` 跳过 `|` 前缀 |
| 口径 | 叶子项各自 100 分，3 个分类合计 300 | 权重制文件的分类权重 40/50/10 才是文档总分口径，叶子项不可直接求和 |

## 修复与边界

- **作用域**：识别「附表/附件」标题；无正文方案根时，正文含评分关键词的附件自身作为方案根；单根时补齐附件 scope，不含关键词的附件显式标 `OUT_OF_SCHEME`。附件边界取「下一个附件标题 / 同级 markdown 标题 / 任意有效方案根」三者最早。
- **权重闭合**：`OriginalWeightClosurePolicy` 要求「同一行 ≥2 个百分比且合计 100」+「紧邻上文行具权重/维度语义」+「非 `otherScheme` 章节」，取值恒为 100。仅当无其他总分证据且 `localProblems` 为空时启用。
- **不可用方案**：要求表章节落在 `IN_SCHEME` 文本作用域内会误杀真实权重表（`SCHEME` 正则不含「分值」，附表一被判 `OUT_OF_SCHEME`），已放弃。
- **fail-safe 边界**：若原文真实总分 X≠100 且 facts 顶层合计为 X，`MultimodalScoreFactValidator` / `OriginalSourcePublicationGuard` 按 `comparableTotal` 判 `WEIGHT_SUM_MISMATCH`、proof=null，不会静默发布错误数据。

## 验证纪律

- 单测 `Project1391ScopeWeightClosureTest` 4 例；其中「表头误判」负向用例临时回滚旧逻辑实测 RED，非同义反复。
- 金标夹具 `case-1391-appendix-weight-closure` 已注册 `GoldenCasesReplayTest#cases()`，满足 scoreparse 金标门禁。
- 比较口径只在 `ScoreUnitPolicy.comparableTotal` 一处收敛，三处调用点同口径。
