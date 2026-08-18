# Tasks: 历史投标项目信息批量导入

**Input**: Design documents from `/specs/041-historical-project-import/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/historical-import-api.md

**Tests**: 本功能遵循 Constitution III (TDD NON-NEGOTIABLE)，纯核心组件必须先写测试 → FAIL → 实现 → GREEN。所有测试任务已显式标注。

**Organization**: Tasks 按 User Story 组织，使每个故事可独立实现和测试。MVP = User Story 1。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 所属 User Story（US1/US2/US3）
- 所有任务都包含精确文件路径

## Path Conventions

- **后端**: `backend/src/main/java/com/xiyu/bid/...` 和 `backend/src/test/java/com/xiyu/bid/...`
- **前端**: `frontend/src/`（实际仓库中为 `src/`）
- **迁移**: `backend/src/main/resources/db/migration-mysql/`
- **采集表模板**: 由后端在运行时生成下载，无独立文件

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 权限键注册、Flyway 迁移、DTO 骨架、纯核心数据结构

- [ ] T001 在 `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` 的 admin 角色 `SeedDefinition.menuPermissions` 中注册 `project:import` 权限键
- [ ] T002 [P] 创建 Flyway 迁移脚本 `backend/src/main/resources/db/migration-mysql/V{next}__add_project_import_permission.sql`（用 `bash scripts/new-migration.sh add_project_import_permission` 生成版本号），为 admin 角色插入 `project:import` 权限
- [ ] T003 [P] 创建 DTO `backend/src/main/java/com/xiyu/bid/project/dto/HistoricalProjectImportResult.java`（含 totalRows/successCount/failureCount/skippedCount/failures/skipped 字段）
- [ ] T004 [P] 创建 DTO `backend/src/main/java/com/xiyu/bid/project/dto/HistoricalProjectRowFailure.java`（含 rowNumber/projectName/reason/field 字段）
- [ ] T005 [P] 创建纯核心 record `backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectRow.java`（19 字段映射 Excel 行，不可变 record）
- [ ] T006 [P] 创建纯核心 record `backend/src/main/java/com/xiyu/bid/project/core/ParsedUserRef.java`（承载"姓名（工号）"解析结果：name/employeeNumber）

**Checkpoint**: 权限键、DTO 骨架、纯核心数据结构就绪，可开始纯核心 TDD

---

## Phase 2: Foundational (纯核心 TDD - 阻塞所有 User Story)

**Purpose**: 纯核心组件（字段映射/状态转换/用户解析/必填校验）用 TDD 实现，是 US1 导入流程的基础

**⚠️ CRITICAL**: 纯核心组件必须先写测试并 FAIL，再实现使其 GREEN。此阶段完成前不得开始 US1 实现

### 2.1 纯核心测试（先写，确保 FAIL）

- [ ] T007 [P] 创建测试 `backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectStatusMapperTest.java`，覆盖：4 个终态状态映射（已中标→WON/未中标→LOST/已流标→FAILED/已放弃→ABANDONED）、非法状态值（投标中/评标中/空值）抛异常、stage 固定返回 CLOSED
- [ ] T008 [P] 创建测试 `backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectRowMapperTest.java`，覆盖：19 列完整映射、客户营收×100000000 转元、开标时间多格式解析（LocalDate/字符串 yyyy-MM-dd/yyyy年MM月dd日）、可选字段为空时仍能映射
- [ ] T009 [P] 创建测试 `backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectUserResolverTest.java`，覆盖：解析"姓名（工号）"全角括号/半角括号、批量查询构建 Map<工号,用户ID>、工号不存在时返回 Optional.empty()、格式不匹配（无括号）返回 Optional.empty()
- [ ] T010 [P] 创建测试 `backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectFieldValidatorTest.java`，覆盖：14 个必填字段缺失时返回对应失败原因、全部必填字段齐全时校验通过、可选字段（客户营收/入围供应商数/联系人/投标月份）为空不报错

### 2.2 纯核心实现（使测试 GREEN）

- [ ] T011 [P] 实现 `backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectStatusMapper.java`（final class，方法 `mapStatus(String chinese) -> Project.Status`、`mapStage() -> String` 返回 "CLOSED"、`mapResultType(String chinese) -> ProjectResult.ResultType`）
- [ ] T012 [P] 实现 `backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectRowMapper.java`（final class，方法 `map(Row source) -> HistoricalProjectRow`，含客户营收×100000000 转换、开标时间多格式解析）
- [ ] T013 [P] 实现 `backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectUserResolver.java`（final class，方法 `parseUserRef(String text) -> Optional<ParsedUserRef>`、`resolveBatch(List<String> rawTexts, UserRepository repo) -> Map<String, Long>`，遵循 Constitution V §批量优先）
- [ ] T014 [P] 实现 `backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectFieldValidator.java`（final class，方法 `validate(HistoricalProjectRow row) -> List<ValidationIssue>`，返回空列表表示通过）

**Checkpoint**: 纯核心全绿，可开始 US1 命令式外壳实现

---

## Phase 3: User Story 1 - 管理员批量导入历史项目 (Priority: P1) 🎯 MVP

**Goal**: 管理员上传 19 列 Excel 采集表，系统批量导入历史项目，每行创建占位标讯/项目/立项详情/结果/负责人指派 5 条关联记录，返回成功/失败/跳过汇总

**Independent Test**: 导入含 5 条历史项目的测试采集表，登录 admin 账号调用 `POST /api/projects/import/historical`，确认返回"成功 5 条，失败 0 条"；打开项目列表页确认 5 条项目出现且字段完整；逐条打开详情页确认基本信息/标讯联系人/阶段时间轴正常加载

### 3.1 集成测试（先写，确保 FAIL）

- [ ] T015 [US1] 创建集成测试 `backend/src/test/java/com/xiyu/bid/project/service/HistoricalProjectImportOrchestratorTest.java`，覆盖：5 行全部成功（验证 5 个 Project+5 个 Tender+5 个 ProjectInitiationDetails+5 个 ProjectResult+5 个 ProjectLeadAssignment）、1 行工号不存在失败其余成功、1 行必填字段缺失失败其余成功、重复导入同表走幂等跳过、状态非法（投标中）行失败
- [ ] T016 [US1] 创建 Controller 测试 `backend/src/test/java/com/xiyu/bid/project/controller/HistoricalProjectImportControllerTest.java`（MockMvc），覆盖：上传 .xlsx 返回 200 + 汇总、上传 .csv 返回 400、上传空文件返回 400、上传 501 行返回 400（超 500 上限）、无 project:import 权限返回 403

### 3.2 Orchestrator 实现（使命令式外壳 GREEN）

- [ ] T017 [US1] 实现 `backend/src/main/java/com/xiyu/bid/project/service/HistoricalProjectImportOrchestrator.java`：
  - `importFromExcel(MultipartFile file, Long operatorUserId) -> HistoricalProjectImportResult`
  - 用 Apache POI 读取 Excel → `List<HistoricalProjectRow>`
  - 批量查询所有工号 → `Map<String, Long>`（调 `HistoricalProjectUserResolver.resolveBatch`）
  - 批量查询已存在 external_id（`HISTORICAL_` 前缀）→ 跳过清单
  - 每行独立事务（`@Transactional(propagation = REQUIRES_NEW)`）写入 5 张表
  - 失败行 catch 后记入 failures，不中断后续行
  - 成功行触发 `ProjectArchiveWorkflowService.createArchive`
  - 每行写入顺序遵循 data-model.md §数据库写入顺序（Tender → Project → ProjectInitiationDetails → ProjectResult → ProjectLeadAssignment → Archive）

### 3.3 Controller 实现

- [ ] T018 [US1] 实现 `backend/src/main/java/com/xiyu/bid/project/controller/HistoricalProjectImportController.java`：
  - `POST /api/projects/import/historical`（`@PreAuthorize("hasAuthority('project:import')")`，multipart/form-data）
  - `GET /api/projects/import/historical/template`（下载采集表模板 .xlsx，含 19 列表头 + 数据验证下拉）
  - 文件类型/大小/行数校验（>500 行返回 400）
  - 异常处理：文件解析失败、IO 异常返回 500 + Sentry 上报

### 3.4 占位标讯过滤（避免污染标讯列表页）

- [ ] T019 [US1] 修改 `backend/src/main/java/com/xiyu/bid/repository/TenderRepository.java`：标讯列表查询方法追加 `AND external_id NOT LIKE 'HISTORICAL_%'` 条件（或新增 `findActiveTendersExcludingHistorical` 方法）
- [ ] T020 [US1] [P] 验证 `backend/src/main/java/com/xiyu/bid/project/service/TenderQueryService.java`（或对应标讯列表 Service）调用过滤后的查询方法，确认占位标讯不出现在标讯列表页

**Checkpoint**: User Story 1 完成 — 管理员可批量导入历史项目，前端列表页和详情页字段完整显示，标讯列表页不污染

---

## Phase 4: User Story 2 - 项目列表页字段完整显示 (Priority: P2)

**Goal**: 导入成功的历史项目在项目列表页 19 个字段全部有值显示（优先级/客户营收/入围供应商数/投标月份/项目负责人姓名/投标负责人姓名/服务周期/服务周期截止时间等扩展字段），支持按扩展字段筛选

**Independent Test**: 导入 3 条历史项目（优先级/客户营收/入围供应商数/服务周期均填写），打开项目列表页确认 3 条记录的扩展字段全部有值不显示"-"；按"优先级=S"筛选返回正确结果

### 4.1 列表页字段映射验证

- [ ] T021 [US2] 验证 `backend/src/main/java/com/xiyu/bid/project/service/ProjectListService.java`（或 ProjectQueryService）的列表查询 DTO 包含 tenders.priority、project_initiation_details.annual_revenue/expected_bidders/bid_month、project_result.service_period_years/service_period_end_date、users.name（负责人姓名）等扩展字段
- [ ] T022 [US2] [P] [测试] 补充 `backend/src/test/java/com/xiyu/bid/project/service/ProjectListServiceTest.java`（或对应测试）：导入历史项目后查询列表，断言扩展字段非空
- [ ] T023 [US2] [P] 验证项目列表筛选接口支持按 priority/customer_type/status 等扩展字段筛选（FR-005、SC-005 要求）

### 4.2 前端上传入口

- [ ] T024 [US2] 扩展 `src/api/modules/projects.js`：新增 `importHistoricalProjects(file)` 方法（POST multipart/form-data 到 `/api/projects/import/historical`）和 `downloadHistoricalProjectTemplate()` 方法
- [ ] T025 [US2] 创建 `src/views/Project/components/HistoricalProjectImportDialog.vue`：上传按钮 + 文件选择 + 进度提示 + 导入结果展示（成功/失败/跳过汇总 + 失败明细表格：行号/项目名称/失败原因/字段）
- [ ] T026 [US2] 集成到项目列表页 `src/views/Project/ProjectList.vue`（或对应列表页组件）：在工具栏新增"导入历史项目"按钮，点击弹出 HistoricalProjectImportDialog，导入成功后刷新列表

**Checkpoint**: User Story 2 完成 — 列表页字段完整显示，支持扩展字段筛选，前端有上传入口

---

## Phase 5: User Story 3 - 项目详情页联动数据正常加载 (Priority: P3)

**Goal**: 导入的历史项目点击进入详情页，基本信息卡字段完整（招标主体/来源平台/客户类型/优先级/开标时间），标讯联系人模块显示采集表填写的联系人，阶段时间轴显示"已结项"，不出现"关联标讯已被删除"错误

**Independent Test**: 导入 1 条含联系人"张三/13800138000"的采集表，打开该项目详情页确认基本信息卡字段完整、标讯联系人模块显示"张三 / 13800138000"、阶段时间轴显示"已结项"

### 5.1 详情页联动验证

- [ ] T027 [US3] 验证 `backend/src/main/java/com/xiyu/bid/project/service/ProjectDetailService.java`（或对应 Service）按 projectId 查询时能正确联表加载 tender（含 contact_name/contact_phone/priority/bid_opening_time）、project_initiation_details、project_result，占位标讯不被详情页查询过滤
- [ ] T028 [US3] [P] [测试] 补充 `backend/src/test/java/com/xiyu/bid/project/service/ProjectDetailServiceTest.java`：导入历史项目后查询详情，断言 tender 联表字段非空、project_initiation_details 字段非空、project_result 字段非空
- [ ] T029 [US3] [P] 验证详情页前端组件 `src/views/Project/ProjectDetail.vue`（或对应详情页组件）的"基本信息卡""标讯联系人""阶段时间轴"模块能正确渲染导入的字段值，不出现"关联标讯已被删除"

### 5.2 阶段时间轴验证

- [ ] T030 [US3] [P] 验证 `backend/src/main/java/com/xiyu/bid/project/service/ProjectStageService.java`（或对应 Service）对 stage=CLOSED 的历史项目返回"已结项"阶段信息，时间轴正常加载

**Checkpoint**: User Story 3 完成 — 详情页联动数据完整，无错误提示

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 跨 Story 的改进、文档、门禁验证

- [ ] T031 [P] 补充 `backend/src/test/java/com/xiyu/bid/project/controller/HistoricalProjectImportControllerTest.java`：补充模板下载接口测试（GET /template 返回 .xlsx + Content-Disposition 正确）
- [ ] T032 [P] 运行架构测试 `mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`，确认新代码符合 FP-Java 分层、文件行数 <300、无 Collectors.toMap 2 参数版本
- [ ] T033 [P] 运行前端门禁 `npm run check:front-data-boundaries && npm run check:doc-governance && npm run check:line-budgets && npm run build`
- [ ] T034 [P] 在 `docs/lessons/lessons-learned.md` 追加本次导入功能的设计决策（19 字段映射、占位标讯策略、幂等键设计、批量用户查询），禁止新建独立页面
- [ ] T035 运行 `specs/041-historical-project-import/quickstart.md` 端到端验证场景（导入测试采集表 → 验证列表页 → 验证详情页 → 验证筛选 → 验证幂等）
- [ ] T036 [P] 检查 `.wiki/pages/` 是否需要回填"历史项目导入"相关页面（复合查询回填触发器，详见 `.wiki/WIKI.md §2 触发点 3`）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始。T001 先于 T002（权限键注册在 RoleProfileCatalog 后，迁移脚本才能正确引用）
- **Foundational (Phase 2)**: 依赖 Setup 完成。**阻塞所有 User Story**。TDD 强制：T007-T010 测试必须先写并 FAIL，T011-T014 实现使其 GREEN
- **US1 (Phase 3)**: 依赖 Foundational 完成。T015-T016 测试先于 T017-T018 实现。T019-T020 占位标讯过滤可与 Orchestrator 并行
- **US2 (Phase 4)**: 依赖 US1 完成（需要导入的数据才能验证列表页字段显示）。前端任务 T024-T026 依赖后端 API 可用
- **US3 (Phase 5)**: 依赖 US1 完成（需要导入的数据才能验证详情页联动）。可与 US2 并行（验证不同页面）
- **Polish (Phase 6)**: 依赖所有 User Story 完成

### Within Each User Story

- 测试（如果包含）MUST 先写并 FAIL
- 纯核心 → 命令式外壳 → Controller → 前端
- 核心实现完成 → Story 可独立验证

### Parallel Opportunities

- Phase 1: T002/T003/T004/T005/T006 可并行（不同文件）
- Phase 2: T007/T008/T009/T010 测试可并行；T011/T012/T013/T014 实现可并行（不同 final class）
- Phase 3: T015 和 T016 可并行；T019 和 T020 可并行
- Phase 4: T022 和 T023 可并行；T024/T025/T026 前端任务需顺序（API → 组件 → 集成）
- Phase 5: T027/T028/T029/T030 大部分可并行（不同验证点）

---

## Parallel Example: Phase 2 Foundational

```bash
# 4 个纯核心测试可并行编写（不同 final class，无依赖）：
Task: "T007 HistoricalProjectStatusMapperTest in backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectStatusMapperTest.java"
Task: "T008 HistoricalProjectRowMapperTest in backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectRowMapperTest.java"
Task: "T009 HistoricalProjectUserResolverTest in backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectUserResolverTest.java"
Task: "T010 HistoricalProjectFieldValidatorTest in backend/src/test/java/com/xiyu/bid/project/core/HistoricalProjectFieldValidatorTest.java"

# 测试全部 FAIL 后，4 个纯核心实现可并行：
Task: "T011 HistoricalProjectStatusMapper in backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectStatusMapper.java"
Task: "T012 HistoricalProjectRowMapper in backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectRowMapper.java"
Task: "T013 HistoricalProjectUserResolver in backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectUserResolver.java"
Task: "T014 HistoricalProjectFieldValidator in backend/src/main/java/com/xiyu/bid/project/core/HistoricalProjectFieldValidator.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup（权限键 + 迁移 + DTO + record）
2. 完成 Phase 2: Foundational（纯核心 TDD：测试 → FAIL → 实现 → GREEN）
3. 完成 Phase 3: User Story 1（Orchestrator + Controller + 占位标讯过滤）
4. **STOP and VALIDATE**: 用 Postman/curl 上传测试采集表，验证 5 条项目导入成功 + 列表页 + 详情页字段完整
5. 部署/演示（如 ready）

### Incremental Delivery

1. Setup + Foundational → 纯核心就绪
2. + User Story 1 → 后端可批量导入，列表页/详情页字段完整 → MVP!
3. + User Story 2 → 列表页筛选 + 前端上传入口
4. + User Story 3 → 详情页联动验证
5. Polish → 架构测试 + 文档 + 门禁

### TDD Compliance (Constitution III)

- 每个 final class 纯核心组件：先写 Test → 确认 FAIL → 实现 → 确认 GREEN → Refactor
- 集成测试（Orchestrator/Controller）：先写测试桩 → 实现 → GREEN
- 架构测试（ArchitectureTest）：实现完成后运行，验证 FP-Java 分层 + 文件行数 + toMap merge function
- **禁止跳过测试直接实现**（Constitution III NON-NEGOTIABLE）

---

## Notes

- [P] 任务 = 不同文件、无依赖
- [Story] 标签映射到 spec.md 的 User Story
- 每个纯核心组件用 `final class`（Constitution I：不可变核心）
- 每个 Java 文件软上限 200 行，硬上限 300 行（Constitution IV）
- 所有 `Collectors.toMap` 用 3 参数版本（Constitution VII）
- 所有 5xx 异常 handler 打印堆栈 + Payload + Sentry 上报（Constitution VII）
- `@PreAuthorize("hasAuthority('project:import')")` 不用 `hasRole`（Constitution VI）
- 每个 task 完成后 commit；每个 Story 完成后 push WIP 分支
- 在任意 Checkpoint 可独立验证 Story
