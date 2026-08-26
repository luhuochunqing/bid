# Research: M1 竞品维度「中标数」口径修复

**Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## R1 「是否中标」唯一数据源考证

| 证据 | 位置 | 结论 |
|---|---|---|
| 前端下拉枚举定义 | `src/constants/competitors.js:12` `COMPETITOR_WIN_STATUS_OPTIONS = ['已中标','未中标']` | 值域封闭 |
| 语义漂移写入点 | `ResultConfirmStage.vue` L208 注释锚点；写入 `project_result_competitor.notes`（该列原名「其他说明」） | 字段名不暗示语义，必须靠注释/wiki 锚定 |
| 项目级口径禁令 | `.wiki/pages/data-analysis-revamp.md` §10、`docs/lessons/lessons-learned.md` §127（PR !2339） | 禁止 status/resultType/bidResultStatus 推导 |
| 历史自由文本 | §127 + wiki §10：历史行 notes 可能为任意文本或 null | 非「已中奖」精确匹配一律按未中标 |

## R2 当前错误链路（全量）

```
图表: TrendAnalysisService.java:104-107 (competitor 路由)
  → TrendAnalysisDimensionQueryService.fetchCompetitorRows:186-191 (JPQL 不带 notes)
  → TrendAnalysisComputationService.computeDimensionTrend:53-57 (winCount 按 status==WON) ← 根因
下钻: useDrillDown.js:31-53 (dimension=competitor + seriesName=中标数 可达)
  → TrendDrillDownService.java:84-86 (status='WON') ← 同根因
  → TrendDrillDownService.java:213-225 countByStatus (summary.totalWins 同口径) ← 同根因
先例: CompetitorAnalysisQueryService.java:51,106 (select prc.notes)
     CompetitorAnalysisComputationService.java:122-124 parseCompetitorWon (+专项测试 :20-36)
```

## R3 波及面清单（零改动对账基准）

- `new DimensionRow(` 全仓 15 处：生产 2 处（fetchRegionRows L134、fetchCustomerTypeRows L156）+ 测试 13 处（TrendAnalysisComputationServiceTest，grep -c 实测）→ 3 参便捷构造器下全部零改动。
- JPQL 构造器模板 1 处：`queryDimension` L214-215 `select new ...DimensionRow(selectExpr, p.id, p.status)` → 加 extraSelect 尾参分支。
- M4 入口调用 1 处：`CompetitorAnalysisAssemblerService.java:245` → parseCompetitorWon 保留签名委托后零改动。

## R4 技术选型记录

- HQL 构造器表达式第 4 位传字面量：Hibernate 6.4 支持，但采用 extraSelect 拼接分支（null 时显式拼 `, null`），不依赖版本行为细节。
- 下钻 EXISTS 参数化：`:wonLabel` 绑定 support 常量，杜绝 SQL 内硬编码中文字面量。
- TRIM 差异：MySQL TRIM 半角 vs Java trim \u0000-\u0020，枚举场景等价，双侧各自处理即可（见 plan D5）。
