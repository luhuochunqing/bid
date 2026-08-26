# Implementation Plan: M1 竞品维度「中标数」口径修复

**Branch**: `agent/ox/fix-analytics-competitor-won-notes` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/055-fix-analytics-competitor-won-notes/spec.md`

## Summary

竞品维度趋势图的「中标数」从项目级状态 `projects.status=WON` 切换为竞品级事实 `project_result_competitor.notes='已中标'`，并同步修复下钻链路（明细过滤 + summary.totalWins），保证图表与下钻数字同源。投标数口径维持不变。「已中标」枚举值与判定函数提取到 analytics 共享 support 类，M4 委托调用消除第三处定义。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（Hibernate 6.4，JPQL 构造器表达式）

**Primary Dependencies**: Spring `@Service`/`@Component`、Jakarta Persistence、Lombok；计算层零框架依赖（FP-Java 契约：纯核心可单测）

**Storage**: MySQL 8.0（`project_result_competitor.notes` 为既有 varchar 列，**无迁移**）

**Testing**: JUnit 5 + AssertJ（plain unit test，不启 Spring 上下文）

**Target Platform**: 后端服务（backend/ 模块）

**Performance Goals**: 无新增全表扫描——notes 经既有 INNER JOIN 链路带出（select 多一列）；下钻 EXISTS 子查询走 `result_id`/`project_id` 既有关联路径

**Constraints**: 其他 6 个维度 + 时间维度行为零变化；M4 测试零改动全绿；「已中标」字面量后端主代码仅一处

**Scale/Scope**: 5 个生产文件 + 2 个测试文件 + spec 文档，单 PR 两个原子提交

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 门禁 | 结论 |
|---|---|
| FP-Java 纯核心可单测 | ✅ 计算层新方法为纯函数，plain JUnit 覆盖 |
| 真实 API 唯一源，禁止 Mock | ✅ 不涉及前端 API；测试为纯计算单测非接口 Mock |
| 数据库迁移规范 | ✅ 无 schema 变更，无需 Flyway |
| 原子提交 + 测试证据 | ✅ 两 commit：①重构（M4 全绿为证）②修复（新增单测为证） |
| lessons/wiki 回填纪律 | ✅ tasks 含 wiki §10 追加 + lessons 复发条目 |

## Project Structure

### Documentation (this feature)

```text
specs/055-fix-analytics-competitor-won-notes/
├── spec.md                 # 需求规格（已完成）
├── plan.md                 # 本文件
├── research.md             # 口径纪律与先例调研（已完成，见下方 Research Summary）
└── tasks.md                # 任务分解
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/analytics/
├── service/
│   ├── CompetitorWonStatusSupport.java        # [新建] WON_LABEL 常量 + parseCompetitorWon 共享纯函数
│   ├── DimensionRow.java                      # record 加第 4 参 competitorWonStatus + 3 参便捷构造器
│   ├── TrendAnalysisDimensionQueryService.java# queryDimension 加 extraSelect 参数；fetchCompetitorRows 带 prc.notes
│   ├── TrendAnalysisComputationService.java   # 新增 computeCompetitorTrend 纯函数
│   ├── TrendAnalysisService.java              # competitor 分支路由到 computeCompetitorTrend
│   ├── TrendDrillDownService.java             # 竞品+中标数 下钻过滤与 totalWins 改 notes 口径
│   └── CompetitorAnalysisComputationService.java # parseCompetitorWon 改委托共享 support（签名不变）
backend/src/test/java/com/xiyu/bid/analytics/service/
├── CompetitorAnalysisComputationServiceTest.java # 零改动（回归证据）
└── TrendAnalysisComputationServiceTest.java      # 新增竞品维度用例组
```

**Structure Decision**: 全部改动收敛在 analytics 包内，遵循既有 support 类模式（仿 `AnalyticsRegionFilterSupport`）；不新建包、不动实体层。

## Research Summary (Phase 0)

1. **口径纪律出处**：`.wiki/pages/data-analysis-revamp.md` §10 + `docs/lessons/lessons-learned.md` §127——竞品「是否中标」唯一合法数据源为 `prc.notes`（trim 后精确匹配「已中标」），禁止项目级状态推导。本任务为 M1 复发修复。
2. **M4 先例**：`CompetitorAnalysisQueryService.java:51,106` JPQL select 已带 `prc.notes`；`CompetitorAnalysisComputationService.parseCompetitorWon()`（package-private）+ 专项测试（含 `"  已中标 "` trim 场景）→ 提取时保留原签名委托。
3. **下钻链路证据**：`TrendDrillDownService.java:84-86`（seriesName=中标数 → status='WON'）、`:213-225`（countByStatus 同口径）、`:275-277`（competitor 维度 axisValue EXISTS 过滤）；前端 `useDrillDown.js:37-39` 确认 dimension=competitor + seriesName=中标数 可达。
4. **HQL null 字面量**：Hibernate 6.4 构造器表达式支持字面量参数；但采用 extraSelect 字符串拼接分支更稳（避免依赖版本行为细节），其余维度第 4 位显式拼 `null`。
5. **TRIM 边缘差异**：MySQL `TRIM()` 仅去半角空白，Java `String.trim()` 覆盖 \u0000-\u0020；对枚举值场景实际数据等价，双侧各自 trim 后精确匹配即可，不过度工程。

## Design Decisions

- **D1 DimensionRow 兼容扩展**：canonical 构造器 4 参（+`@Nullable String competitorWonStatus`），保留 3 参便捷构造器 delegate——13 处测试构造 + 2 处 Java 构造 + 6 个维度 JPQL 全部零改动。
- **D2 计算层独立方法**：新增 `computeCompetitorTrend(rows, expectedCategories)`，不向共用 `computeDimensionTrend` 加布尔参数；排序/补零/winRate 公式复用既有私有逻辑形态。
- **D3 去重先行**：延续 `(category, projectId)` 去重键，去重后再判 notes（重复脏行取首行语义，与现口径一致）。
- **D4 下钻条件组装**：seriesName=中标数 且 dimension=competitor 时，追加 `EXISTS (... prc.name = :axisVal AND TRIM(prc.notes) = :wonLabel)`；axisValue 为空回退到 `:competitorNames` IN 过滤 + notes 条件；countByStatus 增加 dimension 感知参数，竞品维度汇总同口径。
- **D5 常量单一来源**：`CompetitorWonStatusSupport.WON_LABEL = "已中标"` + `parseCompetitorWon(String)` static 纯函数；SQL 参数与 Java 解析共用。

## Complexity Tracking

> 无 constitution 违规项，表格留空。
