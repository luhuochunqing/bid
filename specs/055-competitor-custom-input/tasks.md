# 竞品公司自定义输入与竞争对手库治理 - 任务拆解 (055)

> 状态标记：`[x]` 完成 / `[ ]` 待做。每个任务完成需附测试证据（Red → Green → Refactor）。

## 阶段 1：数据库迁移（基础设施）

- [ ] **T1 竞品字典迁移**：Flyway `VXXXX__competitor_status_unique_seed.sql`
  （`backend/src/main/resources/db/migration-mysql/`）+ 回滚 `UXXXX`（`db/rollback/migration-mysql/`）：
  - `competitors` 加 `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'`
  - `competitors.name` 加唯一索引 `uk_competitor_name`（迁移前清理重复，当前表近空）
  - seed 24 个现硬编码竞品（`src/constants/competitors.js` 的 `COMPETITOR_COMPANY_OPTIONS`），`status='ACTIVE'`
  - 证据：迁移脚本 Flyway 格式校验通过（`scripts/check-*`），本地迁移成功。

- [ ] **T2 Competitor 实体加状态**：`competitionintel/entity/Competitor.java` 加 `status` 字段；
  新建 `CompetitorStatus` 枚举（`ACTIVE`/`DISABLED`）。
  - 证据：实体可被 JPA 加载，`CompetitorTest`（既有）扩充 status 断言全绿。

## 阶段 2：后端权限 + 治理逻辑（TDD，纯核心优先）

- [ ] **T3 权限键注册**：`entity/RoleProfileCatalog.java` 新增常量 `COMPETITOR_CREATE_PERMISSION`
  （`competitor.create`）、`COMPETITOR_GOVERN_PERMISSION`（`competitor.govern`）；
  `RoleProfileAdminPermissions.LIST` 追加 `competitor.govern`；
  各角色 `SeedDefinition.menuPermissions` 追加：
  - `competitor.create` → `bid-Team`、`bid-TeamLeader`、`bid-projectLeader`
  - `competitor.govern` → `/bidAdmin`、`bid-SystemAdmin`、`bid-TeamLeader`
  （admin 经 `all` 动态展开，无需追加）
  - 证据：`RoleProfileCatalog` 相关测试（如有）全绿。

- [ ] **T4 权限同步迁移**：Flyway `VXXXX__add_competitor_permissions.sql`（+ U 回滚），参照
  `V1189__add_analytics_dashboard_permission_to_global_roles.sql` 的 `CONCAT + FIND_IN_SET` 幂等写法：
  - `competitor.create` → `WHERE code IN ('bid-Team','bid-TeamLeader','bid-projectLeader','/bidAdmin','bid-SystemAdmin')`
  - `competitor.govern` → `WHERE code IN ('/bidAdmin','bid-TeamLeader','bid-SystemAdmin')`
  - 证据：迁移成功，`roles.menu_permissions` 回读含新权限键。

- [ ] **T5 纯核心策略**：新建 `competitionintel/domain/CompetitorDictionaryPolicy.java`（纯函数，无 Spring/IO）：
  - 名称归一化（trim + 去首尾空白）
  - 合并目标校验（source != target、target 存在、target 非停用）
  - 证据：`CompetitorDictionaryPolicyTest` 全绿（先写测试，RED → GREEN）。

- [ ] **T6 Repository 扩展**：`CompetitorRepository.java` 加 `findAllByStatusOrderByNameAsc`、
  `findAllByOrderByNameAsc`（治理页全量）。
  - 证据：仓库层集成测试（如既有）全绿。

- [ ] **T7 治理服务**：`CompetitionIntelService.java` 新增 `upsertCompetitor(name)`（按名查重幂等）、
  `renameCompetitor(id, newName)`、`mergeCompetitor(sourceId, targetId)`（停用 source +
  `competition_analyses.competitor_id` 批量 UPDATE 到 target，历史快照不动）、`disableCompetitor(id)`；
  若 `CompetitionIntelService` 超 200 行软上限，拆出 `CompetitorGovernanceService`。
  - 证据：`CompetitionIntelServiceTest` 增补 upsert 幂等/合并/停用用例全绿（先写测试）。

- [ ] **T8 Controller 端点**：`CompetitionIntelController.java`：
  - 改造 `POST /competitors`：`@PreAuthorize` 由 `hasAnyRole('ADMIN','MANAGER')` 改为
    `hasAuthority('competitor.create')`，逻辑改为轻量 upsert（只收 `name`）
  - `GET /competitors` 加 `includeDisabled` 参数（默认仅 ACTIVE）
  - 新增 `PUT /competitors/{id}`、`POST /competitors/{id}/merge`、`POST /competitors/{id}/disable`，
    均 `@PreAuthorize("hasAuthority('competitor.govern')")`
  - 证据：`CompetitionIntelControllerIntegrationTest` 增补权限断言（非治理角色 403）全绿。

## 阶段 3：前端

- [ ] **T9 API 字段对齐**：`src/api/modules/competitionIntel.js` 的 `normalizeCompetitor` 改为与后端
  `CompetitorDTO` 一致（`id/name/industry/strengths/weaknesses/marketShare/status/createdAt`，移除
  region/website/winRate 等假字段）；新增 `upsertCompetitor/renameCompetitor/mergeCompetitor/disableCompetitor`。
  - 证据：`bidResults.spec.js` 等既有前端测试不回归；新增 normalize 单测。

- [ ] **T10 结果确认页动态下拉**：`src/views/Project/stages/ResultConfirmStage.vue` 竞品下拉：
  - 改由 `competitionIntelApi.getCompetitors()` 动态加载（`onMounted`）
  - `el-select` 加 `allow-create` + `default-first-option`，失焦/提交时对不存在的名字调用 upsert
  - 快照仍只提交 `name` 文本（不加 competitor_id）
  - 证据：`ResultConfirmStage.spec.js` 增补动态加载 + allow-create 用例全绿。

- [ ] **T11 竞争对手库管理页**：新建 `src/views/System/` 下竞品管理页组件（列表 + 改名 + 合并 +
  停用），配路由 + 侧边栏菜单 + 权限守卫（治理角色可见）；前端用
  `ROLE_AUTHORITIES`/`isGlobalManageRole` 判断入口显示。
  - 证据：组件测试（列表渲染 / 改名 / 合并 / 停用交互）全绿；非治理角色不显示入口。

- [ ] **T12 清理硬编码常量**：`src/constants/competitors.js` 删除 `COMPETITOR_COMPANY_OPTIONS`
  （改由后端加载），保留 `COMPETITOR_WIN_STATUS_OPTIONS`。
  - 证据：全局 grep 无 `COMPETITOR_COMPANY_OPTIONS` 残留引用。

## 阶段 4：验证与收尾

- [ ] **T13 全量验证**：后端 `mvn test`（含 ArchUnit / 维护性 / 权限守卫）+ 前端 `npm run build`
  + `npm run check:front-data-boundaries` 全绿。
  - 证据：命令输出全绿。

- [ ] **T14 知识沉淀 + wiki 回填**：`knowledge-capture` 沉淀到 `docs/`，`.wiki/pages/` 回填
  （竞品字典治理页 + `_index.md`），提交到本分支。
  - 证据：`wiki-health-check` 通过。

- [ ] **T15 PR 与合入**：确认门禁（Flyway 版本递增、lessons 编号、Constitution 合规声明）后提交 PR；
  合入后跑 `speckit-analyze` 校验 spec/plan/tasks 一致性。
  - 证据：门禁全绿 + PR 合入。
