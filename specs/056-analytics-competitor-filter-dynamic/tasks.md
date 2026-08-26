# Tasks: M1/M4 竞品筛选下拉动态化

**Input**: `/specs/056-analytics-competitor-filter-dynamic/spec.md`

## Phase 1: 后端口径（Commit ①）

- [X] T001 改造 `FilterOptionsQueryService.fetchDistinctCompetitorNames()`：
      distinct 快照查询（去 DB order by，scope 保留）∪ JPQL 查 `Competitor`(status=ACTIVE, name 非空)，
      Java 端 TreeSet 合并排序返回；import 仅 `CompetitorStatus` pure enum；更新 Javadoc 口径说明与类头注释。
- [X] T002 新增 `FilterOptionsCompetitorNamesTest`（仿 FilterOptionsDepartmentTreeTest 的 Mockito 模式）：
      并集去重 / 排序 / 空名过滤 / scope 参数传递 / 字典查询状态绑定。
- [X] T003 验证 Commit ①：`mvn test -Dtest='FilterOptions*,ArchitectureTest'` 全绿后提交。

## Phase 2: 前端两模块切换（Commit ②）

- [X] T004 M1 `FilterBar.vue`：competitorOptions 初始静态兜底 + onMounted 经
      `dashboardApi.getFilterOptions()` 取 `.competitor` 覆盖；删除 `handleCompetitorSearch`
      死代码及 `@search` 绑定与 emit 声明。
- [X] T005 M4 `useCompetitorData.js`：competitorOptions 改 ref(静态兜底)；`initOptions()`
      内追加 filter-options.competitor 加载，成功后映射 options 并重置 selectedCompetitors=全集
      （onMounted 时序 await initOptions 先于 fetchData 已验证）；失败静默保留兜底。
- [X] T006 前端既有 spec 适配 + `npm run test -- m1-trend-analysis m4-competitor` 相关用例 +
      lint 通过后提交。
