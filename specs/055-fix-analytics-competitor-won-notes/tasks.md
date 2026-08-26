# Tasks: M1 竞品维度「中标数」口径修复

**Input**: Design documents from `/specs/055-fix-analytics-competitor-won-notes/`

**Execution Order**: 两阶段对应两个原子提交；阶段内按序执行（有依赖）。每任务完成后立即验证再进入下一任务。

## Phase 1: 口径单一来源提取（Commit ①——纯重构，零行为变化）

- [ ] T001 新建 `backend/src/main/java/com/xiyu/bid/analytics/service/CompetitorWonStatusSupport.java`：
      `public final class` + 私有构造器；`public static final String WON_LABEL = "已中标"`；
      `public static boolean parseCompetitorWon(String notes)` —— 逻辑照搬现实现（`notes != null && "已中标".equals(notes.trim())`）；
      类头注释注明语义漂移背景与 wiki §10 锚点。文件头遵守仓库 Pos 注释约定。
- [ ] T002 改造 `CompetitorAnalysisComputationService.parseCompetitorWon()` 为委托：
      方法体改为 `return CompetitorWonStatusSupport.parseCompetitorWon(notes);`，签名、Javadoc、package-private 可见性不变。
- [ ] T003 验证 Commit ①：运行 `mvn test -pl backend -Dtest='CompetitorAnalysis*Test'`（M4 计算测试全绿为重构零行为变化的证据）；同时 grep 确认主代码中「已中标」字面量此时仅剩 support 一处（原 M4 处删除）。
      通过后提交：`refactor(analytics): 提取竞品「已中标」判定到共享 support，M4 改委托（零行为变化）`。

## Phase 2: M1 图表 + 下钻口径切换（Commit ②——修复本体）

- [ ] T004 扩展 `DimensionRow.java`：canonical 构造器加第 4 参 `String competitorWonStatus`（可空）；
      新增 3 参便捷构造器 delegate（`this(category, projectId, status, null)`）；更新类 Javadoc。
      编译验证既有 13 处测试构造 + 2 处 Java 构造零改动通过编译。
- [ ] T005 改造 `TrendAnalysisDimensionQueryService`：
      a. `queryDimension` 增加 `String extraSelect` 参数，select 模板尾部按 `extraSelect == null ? ", null)" : ", " + extraSelect + ")"` 拼接；
      b. 其余 7 个 fetch 方法调用点补传 `null`（行为不变）；
      c. `fetchCompetitorRows` 传入 `"prc.notes"`；
      d. 更新类头注释与维度口径说明。
- [ ] T006 新增 `TrendAnalysisComputationService.computeCompetitorTrend(List<DimensionRow> rows, List<String> expectedCategories)`：
      复用 `(category, projectId)` 去重、bidCount 全计、expectedCategories 补零、bidCount 降序排序、winRate 公式；
      winCount 仅在 `CompetitorWonStatusSupport.parseCompetitorWon(row.competitorWonStatus())` 为 true 时递增；
      Javadoc 写明口径：投标数=该竞品参与项目数，中标数=notes 已中标次数，中标率=竞品对我司中标率。
- [ ] T007 路由切换 `TrendAnalysisService.java` competitor 分支改调 `computeCompetitorTrend`；更新类头注释。
- [ ] T008 下钻同步 `TrendDrillDownService.java`：
      a. seriesName=中标数 且 dimension=competitor 时：不再追加 `p.status='WON'`，
         追加 `EXISTS (SELECT 1 FROM project_result pr2 JOIN project_result_competitor prc2 ON prc2.result_id = pr2.id WHERE pr2.project_id = p.id AND prc2.name = :axisVal AND TRIM(prc2.notes) = :wonLabel)`，
         `:wonLabel` 绑定 `CompetitorWonStatusSupport.WON_LABEL`；axisValue 为空时回退用既有 competitorNames IN 条件拼 EXISTS（无 axisVal 限定）；
      b. `countByStatus` 增加 dimension 参数感知：competitor 维度的 totalWins 用上述 notes 口径子查询统计，其余维度保持 `p.status='WON'`；
      c. 更新类头注释（L83 注释同步改写）。
- [ ] T009 新增单测（`TrendAnalysisComputationServiceTest`，沿用 plain JUnit + AssertJ 风格）：
      ① notes='已中标' 计入 winCount 且 bidCount 正常；② '未中标'/null/空白/历史自由文本不计入；
      ③ 同项目多竞品各自独立计数；④ 去重后重复行不重复计；⑤ winRate = 已中标/参与项目数（含 0 除保护）；
      ⑥ expectedCategories 补零回归；⑦ 非竞品维度走 computeDimensionTrend 行为回归（既有用例即覆盖，跑全绿确认）。
- [ ] T010 全量验证：`mvn test -pl backend -Dtest='TrendAnalysis*,CompetitorAnalysis*'` 全绿；
      grep 对账：主代码「已中标」仅 support 一处；`new DimensionRow(` 仍为 15 处（零改动承诺兑现）。
      通过后提交：`fix(analytics): M1 竞品维度中标数改用结果确认「是否中标」列口径并同步下钻`。

## Phase 3: 知识回填（随 Commit ② 或独立 docs commit）

- [ ] T011 更新 `.wiki/pages/data-analysis-revamp.md` §10：追加一行「2026-08-26 M1 趋势分析竞品维度对齐该口径（specs/055），下钻链路同步」，不新开小节。
- [ ] T012 `docs/lessons/lessons-learned.md` 追加复发条目（引用 §127）：根因为「口径修复只落了首发模块，同根因消费方（M1 图表+下钻）未同步排查」；给出检查动作——修语义漂移字段口径时 grep 该字段全部消费方。
- [ ] T013 更新 analytics 包 `README.md` 文件清单（新增 CompetitorWonStatusSupport 行）+ 各改动类头部 Pos 注释复核。
