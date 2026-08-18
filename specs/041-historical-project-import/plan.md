# Implementation Plan: 历史投标项目信息批量导入

**Branch**: `agent/claude/historical-project-import` | **Date**: 2026-08-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/041-historical-project-import/spec.md`

## Summary

扩展现有 `ProjectImportService`，支持从 19 列 Excel 采集表批量导入历史投标项目。导入时为每个项目创建占位标讯（tender）、项目立项详情（project_initiation_details）、项目结果（project_result）、项目负责人指派（project_lead_assignment），确保前端项目列表页 19 个字段和详情页联动数据完整显示。

核心技术决策：①纯核心函数处理字段映射/状态转换/用户解析（FP-Java），命令式外壳负责事务编排；②批量查询用户工号→ID 映射（避免逐条查询，遵循 Constitution V §批量优先）；③占位标讯用 external_id 前缀 `HISTORICAL_` 标记，避免污染真实标讯列表；④同步导入（500 行 <5 分钟，无需异步）。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2 + JPA

**Primary Dependencies**: Spring Boot Starter Data JPA、Apache POI（Excel 解析，已有依赖）、Lombok、JUnit 5 + Mockito + ArchUnit

**Storage**: MySQL 8.0（projects / tenders / project_initiation_details / project_result / project_lead_assignment / users 六张表）

**Testing**: JUnit 5 + Mockito（单元测试纯核心）+ ArchUnit（架构边界）+ MockMvc（Controller 集成测试）

**Target Platform**: Linux server（Spring Boot 内嵌 Tomcat）

**Project Type**: web-service（后端扩展 + 前端上传入口）

**Performance Goals**: 500 行采集表导入 <5 分钟（SC-001 要求 100 行 <5 分钟，留 5x 余量）

**Constraints**: 同步导入（无需异步，500 行内）；单事务每行独立提交（某行失败不回滚已成功行）；导入接口仅限管理员

**Scale/Scope**: 单次导入 500 行上限（FR-012）；6 张表写入；19 个字段映射

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. FP-Java Architecture | ✅ Compliant | 字段映射/状态转换/用户解析提取为纯核心 record/final class；ProjectImportService 仅做事务编排 |
| II. Real-API Only | ✅ Compliant | 不涉及 Mock，导入写真实数据库 |
| III. TDD | ✅ Compliant | 先写纯核心单元测试（字段映射、状态转换、用户解析、必填校验），再写 Service 集成测试 |
| IV. Split-First & Simplicity | ✅ Compliant | 拆分为：HistoricalProjectRowMapper（纯核心）、ProjectStatusMapper（纯核心）、UserByEmployeeNumberResolver（纯核心）、HistoricalProjectImportOrchestrator（命令式外壳）。每文件 <200 行 |
| V. OSS Integration | N/A | 不涉及 OSS 集成 |
| VI. Authorization Unification | ✅ Compliant | 导入接口用 `@PreAuthorize("hasAuthority('project:import')")`，权限键在 RoleProfileCatalog 注册 |
| VII. Defensive Collection & Graceful Degradation | ✅ Compliant | 批量查询用户用 `toMap(key, value, (a,b)->a)` 3 参数版本；工号查不到时降级跳过该行，不抛异常中断整个导入 |
| VIII. Boring Proven Patterns | ✅ Compliant | 扩展现有 ProjectImportService，不引入新框架；Excel 解析用已有 Apache POI 依赖 |

## Project Structure

### Documentation (this feature)

```text
specs/041-historical-project-import/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── historical-import-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/project/
├── controller/
│   └── HistoricalProjectImportController.java   # 新增：上传入口
├── service/
│   ├── HistoricalProjectImportOrchestrator.java # 新增：批量导入编排（命令式外壳）
│   └── ProjectImportService.java                # 现有：单条导入（保留不动）
├── core/
│   ├── HistoricalProjectRowMapper.java          # 新增：Excel 行 → 内部 record（纯核心）
│   ├── HistoricalProjectStatusMapper.java       # 新增：中文状态/阶段 → 枚举（纯核心）
│   ├── HistoricalProjectUserResolver.java       # 新增：姓名(工号) → users.id（纯核心）
│   └── HistoricalProjectFieldValidator.java     # 新增：必填字段校验（纯核心）
├── dto/
│   ├── HistoricalProjectImportRequest.java      # 新增：单行导入请求（扩展 ProjectImportRequest）
│   ├── HistoricalProjectImportResult.java       # 新增：导入结果汇总
│   └── HistoricalProjectRowFailure.java          # 新增：失败行明细
└── entity/
    └── (复用现有 Project / Tender / ProjectInitiationDetails / ProjectResult / ProjectLeadAssignment)

backend/src/test/java/com/xiyu/bid/project/
├── core/
│   ├── HistoricalProjectRowMapperTest.java       # 单元测试
│   ├── HistoricalProjectStatusMapperTest.java   # 单元测试
│   ├── HistoricalProjectUserResolverTest.java    # 单元测试
│   └── HistoricalProjectFieldValidatorTest.java # 单元测试
└── service/
    └── HistoricalProjectImportOrchestratorTest.java # 集成测试

frontend/src/
├── api/modules/
│   └── projects.js                               # 扩展：新增 importHistoricalProjects API
└── views/Project/
    └── components/
        └── HistoricalProjectImportDialog.vue     # 新增：上传 + 结果展示组件
```

**Structure Decision**: 后端遵循 FP-Java 分层——纯核心（core/ 包）处理映射/转换/解析/校验，命令式外壳（service/）负责事务编排和外部调用（Repository、Excel 解析）。前端新增独立的上传对话框组件，复用现有 projects.js API 模块。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无 Constitution 违规。所有原则均合规。
