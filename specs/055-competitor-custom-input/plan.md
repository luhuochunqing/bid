# Implementation Plan: 竞品公司自定义输入与竞争对手库治理 (055)

**Branch**: `agent/codex/competitor-custom-input` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

结果确认页竞品下拉从"前端硬编码 24 个常量"改为"后端全局竞品字典动态加载 + 输入即建（B1）"，
并为全局竞品字典补齐治理能力（改名/合并/停用），治理端为系统管理区的「竞争对手库」列表页。

复用既有 `competitionintel` 模块（`competitors` 表 + `CompetitionIntelService` + `CompetitionIntelController`），
增量改动：`competitors` 表加 `status` + `name` 唯一约束 + seed 24 竞品；后端加 upsert/rename/merge/disable
治理服务；前端改 `ResultConfirmStage.vue` 动态加载 + allow-create，新增治理页。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（后端）；Vue 3 + Vite 5 + Element Plus（前端）
**Primary Dependencies**: Spring Data JPA + Flyway；element-plus `el-select`（filterable + allow-create）
**Storage**: MySQL 8.0（`competitors` 表，Flyway 管理）
**Testing**: JUnit 5 + Mockito + ArchUnit（后端）；Vitest（前端）
**Target Platform**: Web（内网部署）
**Project Type**: Web application（frontend + backend）
**Performance Goals**: 竞品字典规模 <1000 条，列表一次加载，无分页压力
**Constraints**: Constitution VI 权限统一（`hasAuthority` 权限键，禁 `hasAnyRole`）；FP-Java 单文件 ≤200 行
**Scale/Scope**: 竞品字典百级；治理端角色 4 类；录入端角色 6 类

## Constitution Check

| 原则 | 结论 | 说明 |
|---|---|---|
| II Real-API Only | ✅ 通过 | 全链路真实后端 API，无 mock |
| III TDD | ✅ 通过 | 先写测试（upsert 幂等/合并引用迁移/停用过滤）再实现 |
| IV Split-First | ⚠️ 需拆分 | `CompetitionIntelService` 现约 180 行，加治理逻辑会超 200 行，拆出 `CompetitorDictionaryPolicy`（纯核心）+ 治理服务 |
| VI Authorization Unification | ✅ 通过 | 新增端点用 `hasAuthority('competitor.create'/'competitor.govern')`；权限键注册进 `RoleProfileCatalog` + Flyway `UPDATE roles` 同步；改造存量 POST 从 `hasAnyRole` 迁移 |
| VII Defensive Collection | ✅ 通过 | 合并引用迁移用 `@Modifying` 批量 UPDATE，不引入 2 参数 `toMap` |
| DB Migrations | ✅ 通过 | Flyway V 脚本 + 对应 U 回滚脚本 |

## 权限键设计（Constitution VI）

| 权限键 | 语义 | 授权角色（code） | 备注 |
|---|---|---|---|
| `competitor.create` | 竞品创建/upsert | `bid-Team`、`bid-TeamLeader`、`bid-projectLeader`、`/bidAdmin`、`bid-SystemAdmin` | admin 经 `all` 天然覆盖 |
| `competitor.govern` | 竞品治理（改名/合并/停用） | `/bidAdmin`、`bid-TeamLeader`、`bid-SystemAdmin` | admin 经 `all` 天然覆盖 |

注册位置：`RoleProfileCatalog` 新增常量 + 各角色 `SeedDefinition.menuPermissions` 追加；
Flyway 迁移参照 `V1189__add_analytics_dashboard_permission_to_global_roles.sql` 的 `CONCAT + FIND_IN_SET` 幂等写法。

## Project Structure（改动文件清单）

### 数据库
- `backend/src/main/resources/db/migration-mysql/VXXXX__competitor_status_unique_seed.sql`（+ U 回滚）
  - `competitors` 加 `status VARCHAR(20) DEFAULT 'ACTIVE'`
  - `competitors.name` 加唯一索引（迁移前清理重复，当前表基本为空）
  - seed 24 个现硬编码竞品（`COMPETITOR_COMPANY_OPTIONS`）

### 后端
- `competitionintel/entity/Competitor.java`：加 `status` 字段 + `CompetitorStatus` 枚举
- `competitionintel/repository/CompetitorRepository.java`：加 `findAllByStatusOrderByNameAsc`、`existsByName`
- `competitionintel/domain/CompetitorDictionaryPolicy.java`（新建，纯核心）：名称归一化、查重、合并目标校验
- `competitionintel/service/CompetitionIntelService.java`：加 `upsertCompetitor`/`renameCompetitor`/`mergeCompetitor`/`disableCompetitor`（或拆出 `CompetitorGovernanceService`）
- `competitionintel/controller/CompetitionIntelController.java`：改造 POST 权限、新增 PUT/merge/disable 端点
- `entity/RoleProfileCatalog.java` + `RoleProfileAdminPermissions.java`：注册 `competitor.create`/`competitor.govern`

### 前端
- `src/api/modules/competitionIntel.js`：`normalizeCompetitor` 字段对齐后端 DTO（industry/strengths/weaknesses/marketShare/status），新增 upsert/rename/merge/disable 调用
- `src/views/Project/stages/ResultConfirmStage.vue`：竞品下拉改动态加载 + `allow-create`（失焦/提交 upsert）
- `src/views/System/`（新建）「竞争对手库」管理页：列表 + 改名 + 合并 + 停用；路由 + 菜单 + 权限守卫
- `src/constants/competitors.js`：删除 `COMPETITOR_COMPANY_OPTIONS`（改由后端加载），保留 `COMPETITOR_WIN_STATUS_OPTIONS`

### 测试
- 后端：`CompetitorDictionaryPolicyTest`、`CompetitionIntelServiceTest` 增补（upsert 幂等/合并/停用）、`CompetitionIntelControllerIntegrationTest` 增补权限断言
- 前端：`ResultConfirmStage.spec.js` 增补动态加载/allow-create；治理页组件测试

## 关键实现决策

1. **幂等 upsert**：按 `name` 唯一查重，命中即返回已有记录，未命中才 INSERT；唯一索引兜底并发。
2. **合并语义**：`merge(sourceId, targetId)` 停用 source、把 `competition_analyses.competitor_id` 批量 UPDATE 到 target、返回 target；历史 `project_result_competitor.name` 不改。
3. **停用**：软删除（`status=DISABLED`），物理删除不在本期范围。
4. **下拉数据源**：`GET /competitors` 默认只返回 `ACTIVE`；治理页用 `GET /competitors?includeDisabled=true`（或独立治理查询）。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 新增 `CompetitorDictionaryPolicy` + 治理服务（拆分） | `CompetitionIntelService` 加 4 个治理方法后超 200 行软上限 | 单类塞满违反 FP-Java IV Split-First 原则 |
