# Implementation Plan: 工作台产品蓝图对齐

**Branch**: `codex/workbench-blueprint-alignment` | **Date**: 2026-05-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-workbench-blueprint/spec.md`

## Summary

对齐工作台6要素至飞书产品蓝图 V1.1。核心改动：指标卡片从通用统计改为 deadline-based 分时段节点统计（本日/本周/本月 × 报名截止/开标/保证金截止），新增快捷入口组件，增强日程里程碑和标书评审展示，AI预测占位。

后端遵循 FP-Java Profile + Split-First Rule：纯核心 `WorkbenchDeadlinePolicy` (workbench/domain) 负责时间窗口计算和计数聚合，`WorkbenchDeadlineQueryService` (应用服务) 编排权限过滤和查询。

前端遵循纯核心→composable→组件分层，使用 `hasAnyPermission()` 权限驱动。

## Technical Context

**Language/Version**: Java 21 + JavaScript (Vue 3)

**Primary Dependencies**: Spring Boot 3.3, JPA/Hibernate, Element Plus, Vite 5

**Storage**: MySQL 8.0 (Tender, Fee, Project, ProjectMember tables)

**Testing**: JUnit 5 + Mockito (backend), Vitest (frontend), ArchUnit (架构门禁)

**Target Platform**: Web application (browser + Linux server)

**Constraints**: FP-Java Profile (纯核心零框架依赖), 单文件 ≤ 300行, 80%+ 测试覆盖

## Constitution Check

- [x] **FP-Java Profile**: 纯核心 `WorkbenchDeadlinePolicy` 在 `workbench/domain/`，record 不可变，零框架依赖 ✅
- [x] **Split-First Rule**: Application Service 只做编排，纯核心做业务规则 ✅
- [x] **权限门禁**: 复用 `ProjectAccessScopeService` 做项目级过滤 ✅
- [x] **Mock 政策**: 无新增 Demo/Mock 依赖 ✅
- [x] **文件锁**: 分支独立开发，无锁冲突 ✅

## Project Structure

### Backend
```
backend/src/main/java/com/xiyu/bid/
├── workbench/
│   ├── domain/
│   │   ├── WorkbenchDeadlinePolicy.java       # [NEW] 纯核心：时间窗口+计数聚合
│   │   └── package-info.java                  # [NEW]
│   ├── dto/
│   │   ├── WorkbenchDeadlineStatsDTO.java     # [NEW] API 返回 DTO
│   │   └── package-info.java                  # [NEW]
│   ├── controller/
│   │   └── WorkbenchDeadlineController.java   # [NEW] GET /api/workbench/deadline-stats
│   └── service/
│       ├── WorkbenchDeadlineQueryService.java # [NEW] 应用服务：编排权限+查询+聚合
│       └── WorkbenchScheduleQueryService.java # [MODIFY] 清理 Demo 引用
├── repository/
│   ├── TenderRepository.java                  # [MODIFY] 加 deadline 查询方法
│   └── ProjectRepository.java                 # [MODIFY] 加 findTenderIdsByProjectIds
└── fees/repository/
    └── FeeRepository.java                     # [MODIFY] 加 deposit deadline 查询
```

### Frontend
```
src/
├── views/Dashboard/
│   ├── workbench-deadline-core.js             # [NEW] 纯核心：deadline 指标计算
│   ├── workbench-deadline-core.spec.js        # [NEW]
│   ├── Workbench.vue                          # [MODIFY] 添加新组件
│   └── components/
│       ├── DeadlineMetricCards.vue            # [NEW] deadline 指标卡片
│       ├── QuickEntrySection.vue              # [NEW] 快捷入口
│       └── AiPredictionPlaceholder.vue        # [NEW] AI 预测占位
├── api/modules/
│   └── workbench.js                           # [MODIFY] 加 getDeadlineStats()
└── utils/
    └── permission.js                          # [REF] 已由 PR #281 提供
```

## Implementation Phases

| Phase | Tasks | Output |
|-------|-------|--------|
| 1. Pure Core | Task 1: `WorkbenchDeadlinePolicy` | 纯核心 + 单元测试 |
| 2. Data Access | Task 2: Repository 方法 | TenderRepository, ProjectRepository, FeeRepository 扩展 |
| 3. Service Layer | Task 2: `WorkbenchDeadlineQueryService` | 应用服务 + 权限过滤 |
| 4. API | Task 3: `WorkbenchDeadlineController` | REST 端点 |
| 5. Cleanup | Task 3: 清理 Demo 引用 | WorkbenchScheduleQueryService 简化 |
| 6. Frontend Core | Task 4: `workbench-deadline-core.js` + API 模块 | 前端纯核心 |
| 7. Components | Task 5-8: DeadlineMetricCards, QuickEntrySection, AiPredictionPlaceholder, bid-review integration | 4个新组件 |
| 8. Verify | Task 9: 全量测试 + 架构门禁 | 通过率验证 |
