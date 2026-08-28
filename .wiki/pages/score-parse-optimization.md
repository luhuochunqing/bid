---
title: 评分解析优化记录
space: engineering
category: lessons-learned
sources:
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableBlockLocator.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreMergeResultGuard.java
updated: 2026-08-28
health_checked: 2026-08-28
---

# 评分解析优化记录

## 2026-08-28 评分表格准入与守卫回补根治修复

### 问题
PR !2443 在单测通过但在真实全量文档（如山焦物资 Project 1369）下失效，解析结果充斥《供应商须知前附表》的程序性非评分条款（采购预备会、开启程序、履约保证金、失信行为等），真正评分细则被挤出。

### 根因
1. **表格块定位泛滥**：`ScoreTableBlockLocator` 仅因前附表含有“分值构成”单行即把整张前附表所有程序行全部提取。
2. **纯分值列误判编号**：`ScoreTableRowParser.pureWeightIndex` 从右向左扫描未排除 `codeIndex`，导致条款编号（如 1.8 / 1.9 / 5.2 / 7.6）被误作为 weight 分值提取。
3. **守卫回补判定盲区**：`ScoreMergeResultGuard` 的漏项回补机制仅判定 `sourceText ⊇ detail`，因表格单行天然满足该条件，将 LLM 显式丢弃的前附表非评分干扰项全量强行回填（`restored=18`），造成下游权重超载并逆向淘汰了真实评分细则。

### 修复
1. `ScoreTableBlockLocator` 增加前附表表头特征拦截（含“编列内容/编列规定/条款内容”且无评分列名时整块跳过）。
2. `ScoreTableRowParser` 严格过滤非评分程序性条款（`NON_SCORE_PROCEDURE_PATTERN`）与总览说明行（`SUMMARY_ROW_PATTERN`），且 `pureWeightIndex` 严格排除 `codeIndex`。
3. `ScoreMergeResultGuard` 修正 `isHighConfidence` 判定，要求候选具备明确评分语义证据（`SCORE_EVIDENCE_PATTERN`），非评分程序条款一律不予回补。

### 效果
- 52 个相关核心单测全部通过。
- 彻底杜绝前附表程序性条款入池与死灰复燃。
- 评分细则（财务状况10分、运营能力20分、品牌货源20分等）完整保留。
