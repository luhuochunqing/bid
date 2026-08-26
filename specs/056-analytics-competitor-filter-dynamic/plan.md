# Implementation Plan: M1/M4 竞品筛选下拉动态化

**Branch**: `agent/ox/analytics-competitor-filter-dynamic` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

## Summary

`FilterOptionsQueryService.fetchDistinctCompetitorNames()` 扩展为「快照 distinct ∪ ACTIVE 字典」并集；M1/M4 前端从硬编码 24 常量切换为消费 `/api/analytics/filter-options` 的 `.competitor` 字段，静态常量降级为失败兜底。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2 | Vue 3 Composition API
**Storage**: MySQL 8.0（零迁移——纯查询口径扩展）
**Testing**: Mockito 单测（仿 FilterOptionsDepartmentTreeTest）+ 前端 vitest
**Key Dependency**: `competitionintel/entity/CompetitorStatus`（pure enum 无框架依赖，单向依赖无循环）

## Design Decisions

- **D1 并集在 Java 层合并**：两查询各自简单（scope JPQL / 字典 JPQL），避免跨表 UNION 的 collation 风险（wiki §11 教训）；TreeSet 统一自然序。
- **D2 字典部分不做项目 scope**：主数据语义与结果确认页一致（isAuthenticated 即可见）。
- **D3 M1/M4 共用 filter-options.competitor 字段**：单一数据源（FR-02），前端零口径逻辑。
- **D4 M4 时序保障**：index.vue onMounted `await initOptions()` 先于 fetchData 已验证，options 加载后同步 selectedCompetitors=全集即安全。

## Constitution Check

| 门禁 | 结论 |
|---|---|
| FP-Java | 查询层改动，无纯计算新增 |
| 零迁移 | ✅ 无 schema 变更 |
| 原子提交+测试证据 | ✅ 两 commit 各带验证 |
