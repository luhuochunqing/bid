---
title: 项目1123大跨度Rowspan与平级编号项评分表格解析失真及三阶收敛
space: engineering
category: lessons
tags: [scoreparse, rowspan, prune-parent, deduplication, production]
created: 2026-11-12
updated: 2026-11-12
health_checked: 2026-11-12
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/CanonicalTableGridFactory.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/baidu/BaiduTableGridBuilder.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/DeterministicScoreFactExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreWeightSourceEvidence.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalExtractionReviewPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SourceReviewRowRecovery.java
backlinks:
  - _index
---
# 项目1123大跨度Rowspan与平级编号项评分表格解析失真及三阶收敛

## 现场与问题现象
生产项目 1123（集采项目 100 分综合评估法，共 12 个真实评分项）：
1. 界面呈现 16 项候选，有效合计 108 分（膨胀 8 分）；
2. 页面底部存在 2 条方案外事实（19分大类“物流保障与配送服务能力”、13分大类“结算服务能力及无息账期”），显示“详细评审方案外: 无法确认属于当前评分方案”；
3. 确定性管道计算为 76 分（100 - 24 = 76），触发 `TABLE_SUBTOTAL_MISMATCH` 熔断后降级为多模态模型提取与行补漏。

## 根因致错链
1. **OCR 网格跨行续行文本割裂**：`BaiduTableGridBuilder` 处理 `safeRowspan > 1` 的续行单元格时填充空串，导致续行与父级合并单元格名称割裂。
2. **事实引用丢失合并列 Cell ID**：`DeterministicScoreFactExtractor` 过滤了 `isMergedContinue=true` 单元格，导致续行事实的 `sourceCellIds` 丢失了合并列 origin Cell ID。核验时仅用第 0 列序号和细则匹配父级维度名必然失败，报 `SOURCE_QUOTE_NOT_VERIFIED` 误杀 24 分，导致有效分暴跌至 76 分，确定性管道熔断降级。
3. **平级编号大项沦为方案外杂音**：标书细项采用平级编号（6~9、11~12），而非带点层级编码。大模型提取了 19 分和 13 分大类，因无点号层级无法挂载父子关系；`assignScope` 因其无 tableId 且 code 为纯文字判定为 `OUT_OF_SCHEME`。
4. **补漏恢复缺乏幂等去重**：`SourceReviewRowRecovery` 从 shadow table 回补时，缺少针对已有模型事实 `(code, weight)` 及文本包含的幂等去重门禁，导致模型已提取的第 6 项（4分）和第 7 项（4分）被重复追加，产生 108 分。

## 架构三阶收敛方案
1. **OCR 网格跨行继承下沉与 Cell ID 穿透**：
   - 在 `BaiduTableGridBuilder` 与 `CanonicalTableGridFactory` 中，纵向合并续行单元格完整继承父级合并单元格的内容与规范文本；
   - 在 `DeterministicScoreFactExtractor.resolveContinuationFact` 中，将合并列 origin Cell ID 穿透写入 `sourceCellIds`，并在 `sourceText` 中补充继承的维度文本；
   - 在 `ScoreWeightSourceEvidence.sameRow` 中，增强合并单元格跨行覆盖判定，避免 anchor 错位锚定到首行。
2. **虚构大项剪枝与容器化（Prune Synthetic Parent）**：
   - 在 `MultimodalExtractionReviewPolicy` 中新增 `pruneSyntheticParents`：当模型或表格存在大类总分项（如 19分、13分）与其平级子项，且子项分值和精确等于大项时，自动剪枝剔除虚构大项，禁止其流入方案外杂音；若存在显式 `parentCode` 则保留合法树结构。
3. **补漏恢复幂等与去重门禁**：
   - 在 `SourceReviewRowRecovery` 中新增 `isExistingModelFactDuplicate`：基于归一化编号 `cleanCode`、分值相等、以及维度/细则文本包含关系对现有模型事实去重，杜绝二次追加导致总分膨胀。
