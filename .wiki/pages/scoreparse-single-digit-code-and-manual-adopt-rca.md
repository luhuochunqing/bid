---
title: 单数字评分项识别缺陷与人工复核采纳死锁根因分析
space: engineering
category: debugging
tags: [scoreparse, regex, manual-review, adoption, dead-lock, rca]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalExtractionReviewPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java
  - docs/lessons/lessons-learned.md
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-29
updated: 2026-10-29
health_checked: 2026-10-29
archive: false
---

# 单数字评分项识别缺陷与人工复核采纳死锁根因分析

## 诊断状态

- 触发：项目 1392（`project1130.pdf`）重新解析时，抽屉弹出黄色卡片显示 95 分（22 项），底部 2 项（`7: 大型企事业客户数量和经验 (5.0分)` 与 `10: 质量验收扣分说明`）被判为方案外。用户点击【+ 纳入本方案】将 5 分纳入并使总分达 100 分后，点击【⚡ 一键采纳为正式评分标准】报红字 `方案外的评分事实不能采纳`（HTTP 400）。
- 现象：前端允许人工纠错，但后端拦截阻断，形成死锁闭环。
- 修复状态：本地单元测试全绿、金标回放全绿，门禁验证通过。

## 已证实因果

| 边界 | 观察 | 机制 |
| --- | --- | --- |
| 评分编号正则 | 序号为 `"7"` 的项被判为 `OUT_OF_SCHEME` | `hasScoringCode` 正则 `\d+[._()（）\-0-9]` 要求至少两位，纯单数字 `"7"` 返回 `false` |
| 自动作用域 | 跨页/非纯表格单元格事实被踢至方案外 | `scoringSection && hasPositiveWeight && hasScoringCode(code)` 走入 else，被标记为 `OUT_OF_SCHEME` |
| 人工采纳接口 | 点击采纳报 400 `方案外的评分事实不能采纳` | `ScoreParseReviewAppService` 强制校验 `eligibleIds.containsAll(selectedFactIds)` 拦截人工纳入项，且 `inputs` 仅取 `eligibleFacts` |

## 修复与长效防线

1. **评分项编号正则支持单数字**：
   - 将 `hasScoringCode` 正则修改为 `^(?:\d+|3\.2\.\d|\d+[._()（）\-0-9]|\d+（\d+）|\d+\(\d+\)|(?i)(?:BIZ|TECH|PRICE)[_\d]).*`，单数字序号 `"7"` 顺利识别为评分项。
2. **解除人工采纳死锁**：
   - 在 `ScoreParseReviewAppService.java` 中，允许用户显式选择的事实（包含人工纳入的事实）进入目标方案的 `inputs` 候选集并完成权重计算与正式落库；
   - 默认未显式选择时依然严格只采纳方案内核验项，确保自动化安全与人工纠错并存。

## 验证纪律

- `ScoreParseReviewAppServiceTest` 验证显式纳入成功持久化并拦截不存在事实。
- 55 个 `GoldenCasesReplayTest` 全部通过。
- 41 个前端组件单测全部通过。

