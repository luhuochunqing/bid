# Tasks: 选人控件统一 + 事件库同步启用

**Input**: Design documents from `.specify/specs/021-user-picker-event-sync/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/api.md

**Tests**: 包含测试任务（遵循 Constitution III. TDD NON-NEGOTIABLE）

**Organization**: 任务按 User Story 分组，支持独立实现和测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 所属 User Story（US1/US2/US3/US4）
- 包含精确文件路径

## Path Conventions

- **Web app**: `backend/src/`（后端）、`src/`（前端）
- 后端包：`backend/src/main/java/com/xiyu/bid/`
- 后端测试：`backend/src/test/java/com/xiyu/bid/`
- 前端：`src/`

---

## Phase 1: Setup（共享基础设施）

**Purpose**: 数据库迁移补齐 + 事件库 SDK 配置启用

- [ ] T001 创建 Flyway 迁移脚本补齐 users.employee_number 列在 `backend/src/main/resources/db/migration-mysql/V1093__add_users_employee_number.sql`
- [ ] T002 [P] 创建回滚脚本 `backend/src/main/resources/db/rollback/migration-mysql/U1093__add_users_employee_number.sql`
- [ ] T003 [P] 编写事件库 SDK 启用说明文档在 `.specify/specs/021-user-picker-event-sync/event-sdk-env-vars.md`（记录所需环境变量清单）

**Checkpoint**: 数据库迁移就绪，事件库 SDK 配置说明完成

---

## Phase 2: Foundational（阻塞性前置）

**Purpose**: 后端统一候选人模块基础结构，所有 User Story 依赖此模块

**⚠️ CRITICAL**: 此阶段完成前不可开始 User Story 实现

- [ ] T004 [P] 创建 AssignmentCandidateDTO record 在 `backend/src/main/java/com/xiyu/bid/user/dto/AssignmentCandidateDTO.java`（字段：userId, name, employeeNumber, roleCode, roleName, deptCode, deptName, enabled）
- [ ] T005 [P] 创建 AssignmentContext record 在 `backend/src/main/java/com/xiyu/bid/user/core/AssignmentContext.java`（字段：contextType, deptCode, roleCode）
- [ ] T006 [P] [US3] 编写 AssignmentCandidatePolicy 单元测试在 `backend/src/test/java/com/xiyu/bid/user/core/AssignmentCandidatePolicyTest.java`（测试：全局权限过滤、部门权限过滤、deptCode 参数过滤、roleCode 参数过滤、排序、空列表）
- [ ] T007 创建 AssignmentCandidatePolicy 纯核心 record 在 `backend/src/main/java/com/xiyu/bid/user/core/AssignmentCandidatePolicy.java`（依赖 T004, T005；实现 T006 的测试逻辑）
- [ ] T008 [P] [US3] 编写 AssignmentCandidateAppService 单元测试在 `backend/src/test/java/com/xiyu/bid/user/service/AssignmentCandidateAppServiceTest.java`（测试：context=task 调用链、context=tender 调用链、无效 context 抛 400、权限过滤委托 ProjectAccessScopeService）
- [ ] T009 创建 AssignmentCandidateAppService 编排服务在 `backend/src/main/java/com/xiyu/bid/user/service/AssignmentCandidateAppService.java`（依赖 T007；委托 UserRepository.findByEnabledTrue + ProjectAccessScopeService.getAllowedDepartmentCodes + RoleProfileService.hasGlobalAccess + AssignmentCandidatePolicy.filter）
- [ ] T010 [P] [US3] 编写 AssignmentCandidateController 集成测试在 `backend/src/test/java/com/xiyu/bid/user/controller/AssignmentCandidateControllerTest.java`（测试：GET /api/users/assignable-candidates?context=task 返回 200、context=tender 返回 200、无 context 返回 400、无效 context 返回 400、未认证返回 401）
- [ ] T011 创建 AssignmentCandidateController 在 `backend/src/main/java/com/xiyu/bid/user/controller/AssignmentCandidateController.java`（依赖 T009；@PreAuthorize("isAuthenticated()")，GET /api/users/assignable-candidates）
- [ ] T012 [P] 在 `src/api/modules/users.js` 新增 getAssignableCandidates 方法（调用 GET /api/users/assignable-candidates，参数 context/deptCode/roleCode）

**Checkpoint**: 统一候选人端点可用，前端 API 封装就绪，User Story 实现可开始

---

## Phase 3: User Story 2 - 事件库同步保持本地用户数据准实时 (Priority: P1) 🎯 MVP

**Goal**: 启用事件库 SDK，OSS 用户变更准实时同步到本地 users 表

**Independent Test**: 在 OSS 系统修改某用户部门，等待事件库消费后，本地选人控件搜索该用户能看到更新后的部门信息

### 测试

- [ ] T013 [P] [US2] 编写事件库 SDK 消费链路集成测试在 `backend/src/test/java/com/xiyu/bid/integration/organization/EventSdkConsumerIntegrationTest.java`（测试：BaseOssUser 事件触发 OrganizationUserSyncWriter.upsert、BaseOssDept 事件触发 OrganizationDepartmentSyncWriter.upsert、重复 eventKey 幂等不重复写入、离职用户 enabled 设为 false）

### 实现

- [ ] T014 [US2] 验证 application.yml 中 event-sdk 配置项完整性在 `backend/src/main/resources/application.yml`（确认 enabled/consumer-group/serverList/zkServers/env 配置项存在，默认值 false）
- [ ] T015 [US2] 在 `backend/src/main/resources/application.yml` 添加事件库 SDK 启用注释说明（在 event-sdk.enabled 配置项上方注释：生产环境通过 XIYU_ORG_EVENT_SDK_ENABLED=true 启用）
- [ ] T016 [US2] 验证 OrganizationEventSdkConsumerAdapter 消费链路在 `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/sdk/OrganizationEventSdkConsumerAdapter.java`（确认 @AcceptEvent 注解的三个 topic：BaseOssUser/BaseOssDept/BaseOssJob，consumerGroup=bms）
- [ ] T017 [US2] 验证 OrganizationDirectorySyncAppService.lookupAndWrite 链路在 `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationDirectorySyncAppService.java`（确认 USER_NOTICE 分支调用 fetchUserByUserId + userWriter.upsert，DEPARTMENT_NOTICE 分支调用 fetchDepartmentByDeptId + departmentWriter.upsert）
- [ ] T018 [US2] 验证 OrganizationUserSyncWriter.upsert 逻辑在 `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java`（确认更新 fullName/departmentCode/departmentName/enabled/lastOrgEventKey/lastOrgSyncedAt 字段）
- [ ] T019 [US2] 验证 OrganizationUserSyncWriter.disableByExternalId 逻辑在 `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java`（确认离职用户 enabled 设为 false）

**Checkpoint**: 事件库 SDK 启用后，OSS 用户变更准实时同步到本地 users 表

---

## Phase 4: User Story 3 - 候选人 API 统一为单一端点 (Priority: P2)

**Goal**: 两个旧候选人端点标记 @Deprecated 并委托新端点，修复标讯端点无权限过滤安全隐患

**Independent Test**: 调用 /api/tasks/assignment-candidates 和 /api/tenders/assignment-candidates，验证返回结构与统一端点一致，且都应用权限过滤

### 实现

- [ ] T020 [US3] 在 TaskController 的 assignment-candidates 方法添加 @Deprecated 注解在 `backend/src/main/java/com/xiyu/bid/task/controller/TaskController.java`（第 158-167 行，委托 AssignmentCandidateAppService.getCandidates(context=task)）
- [ ] T021 [US3] 在 TenderAssignmentQueryController 的 assignment-candidates 方法添加 @Deprecated 注解在 `backend/src/main/java/com/xiyu/bid/batch/controller/TenderAssignmentQueryController.java`（委托 AssignmentCandidateAppService.getCandidates(context=tender)，修复无权限过滤问题）
- [ ] T022 [US3] 更新 TenderAssignmentQueryService 委托新统一服务在 `backend/src/main/java/com/xiyu/bid/batch/service/TenderAssignmentQueryService.java`（getCandidates 方法改为调用 AssignmentCandidateAppService）
- [ ] T023 [US3] 更新 TaskAssignmentSupport 委托新统一服务在 `backend/src/main/java/com/xiyu/bid/task/service/TaskAssignmentSupport.java`（getAssignmentCandidates 方法改为调用 AssignmentCandidateAppService）
- [ ] T024 [P] [US3] 在 `src/api/modules/tenders/batch.js` 标记 getAssignmentCandidates 方法 @deprecated 注释（指向 usersApi.getAssignableCandidates({ context: 'tender' })）
- [ ] T025 [P] [US3] 在 `src/api/modules/users.js` 标记 getTaskAssignmentCandidates 方法 @deprecated 注释（指向 getAssignableCandidates({ context: 'task' })）

**Checkpoint**: 两个旧端点废弃并委托统一服务，标讯端点权限过滤已修复

---

## Phase 5: User Story 4 - 选人组件统一封装 (Priority: P2)

**Goal**: 封装可复用 UserPicker 组件，支持远程搜索和预加载两种模式

**Independent Test**: 将任意一个现有选人场景替换为 UserPicker，验证功能行为与替换前一致或更好

### 测试

- [ ] T026 [P] [US4] 编写 useUserPicker composable 单元测试在 `src/composables/__tests__/useUserPicker.test.js`（测试：search 模式触发 usersApi.search、candidates 模式触发 usersApi.getAssignableCandidates、防抖 300ms、loading 状态、空结果处理）

### 实现

- [ ] T027 [US4] 创建 useUserPicker composable 在 `src/composables/useUserPicker.js`（依赖 T026；导出 options/loading/search/loadCandidates/formatLabel，防抖 300ms，复用 formatDisplayName.js）
- [ ] T028 [P] [US4] 编写 UserPicker 组件测试在 `src/components/common/__tests__/UserPicker.test.js`（测试：mode=search 渲染 el-select filterable remote、mode=candidates 预加载选项、v-model 绑定 userId、@select 事件返回完整用户对象、placeholder 传递、空状态显示）
- [ ] T029 [US4] 创建 UserPicker.vue 组件在 `src/components/common/UserPicker.vue`（依赖 T027；props: modelValue/mode/context/deptCode/roleCode/placeholder，emits: update:modelValue/select）
- [ ] T030 [US4] 统一 UserPicker 选项标签格式化在 `src/components/common/UserPicker.vue`（复用 formatDisplayName.js，显示"姓名（部门·角色）"格式）

**Checkpoint**: 统一 UserPicker 组件可用，支持两种模式

---

## Phase 6: User Story 1 - 选人控件统一为本地数据源 (Priority: P1)

**Goal**: 22 处碎片化选人实现迁移到统一 UserPicker 组件

**Independent Test**: 在任意业务页面打开选人控件，输入关键字，能立即看到匹配的候选人列表

### P0 - 修复失效控件（3 处）

- [ ] T031 [P] [US1] 迁移 BasicInfoStep 选人到 UserPicker 在 `src/views/Project/create/steps/BasicInfoStep.vue`（替换 userStore.users 依赖为 UserPicker mode=search，v-model 绑定 manager 字段改为 userId）
- [ ] T032 [P] [US1] 迁移 TaskStep 选人到 UserPicker 在 `src/views/Project/create/steps/TaskStep.vue`（替换 userStore.users 依赖为 UserPicker mode=search，v-model 绑定 owner 字段改为 userId）
- [ ] T033 [P] [US1] 迁移 CollaborationCenter 选人到 UserPicker 在 `src/components/ai/CollaborationCenter.vue`（替换 userStore.users 依赖为 UserPicker mode=search，v-model 绑定 owner 字段改为 userId）
- [ ] T034 [US1] 更新 Project/Create.vue 移除 userList computed 在 `src/views/Project/Create.vue`（移除 const userList = computed(() => userStore.users)）

### P1 - 远程搜索迁移（11 处）

- [ ] T035 [P] [US1] 迁移 TaskForm 选人到 UserPicker 在 `src/components/project/TaskForm.vue`（替换 el-select filterable remote 为 UserPicker mode=search）
- [ ] T036 [P] [US1] 迁移 useTaskAssigneeOptions 到 UserPicker 在 `src/components/project/useTaskAssigneeOptions.js`（重构为调用 UserPicker，或直接删除并使用 UserPicker 内置逻辑）
- [ ] T037 [P] [US1] 迁移 MentionInput @提及到 UserPicker 在 `src/components/common/MentionInput.vue`（替换 usersApi.search 调用为 UserPicker mode=search，注意 @提及的特殊交互）
- [ ] T038 [P] [US1] 迁移 ProjectCollaboratorsDialog 到 UserPicker 在 `src/views/Dashboard/components/ProjectCollaboratorsDialog.vue`（替换 usersApi.search 调用为 UserPicker mode=search）
- [ ] T039 [P] [US1] 迁移 TaskKanban 选人到 UserPicker 在 `src/views/Project/stages/components/TaskKanban.vue`（替换 usersApi.search 调用为 UserPicker mode=search）
- [ ] T040 [P] [US1] 迁移 TaskDecomposeDialog 选人到 UserPicker 在 `src/views/Project/stages/components/TaskDecomposeDialog.vue`（替换 usersApi.search 调用为 UserPicker mode=search）
- [ ] T041 [P] [US1] 迁移 TenderSearchCard 选人到 UserPicker 在 `src/views/Bidding/list/components/TenderSearchCard.vue`（替换 usersApi.search 调用为 UserPicker mode=search）
- [ ] T042 [P] [US1] 迁移 useReminderSettings 到 UserPicker 在 `src/views/Bidding/list/components/useReminderSettings.js`（替换 usersApi.search 调用为 UserPicker mode=search）
- [ ] T043 [P] [US1] 迁移 CAFormDialog 保管员选择到 UserPicker 在 `src/views/Resource/components/CAFormDialog.vue`（替换 usersApi.search 调用为 UserPicker mode=search）
- [ ] T044 [P] [US1] 迁移 InitiationStage 选人到 UserPicker 在 `src/views/Project/stages/InitiationStage.vue`（替换 usersApi.search 调用为 UserPicker mode=search，biddingLeaderId/biddingAssistantId 两个字段）
- [ ] T045 [P] [US1] 迁移 DraftingStage 审核人选择到 UserPicker 在 `src/views/Project/stages/DraftingStage.vue`（替换 usersApi.search 调用为 UserPicker mode=search，bidReviewerId 字段）
- [ ] T046 [P] [US1] 迁移 useProjectSearch 到 UserPicker 在 `src/views/Project/composables/useProjectSearch.js`（替换 userStore.users + usersApi.search 双源为 UserPicker mode=search）

### P2 - 候选人列表迁移（6 处）

- [ ] T047 [P] [US1] 迁移 AssignDialog 到 UserPicker 在 `src/views/Bidding/list/components/AssignDialog.vue`（替换 batchTendersApi.getAssignmentCandidates 为 UserPicker mode=candidates context=tender）
- [ ] T048 [P] [US1] 迁移 List.vue 转派到 UserPicker 在 `src/views/Bidding/List.vue`（替换 batchTendersApi.getAssignmentCandidates 为 UserPicker mode=candidates context=tender）
- [ ] T049 [P] [US1] 迁移 TenderCreatePage 指派到 UserPicker 在 `src/views/Bidding/TenderCreatePage.vue`（替换 batchTendersApi.getAssignmentCandidates 为 UserPicker mode=candidates context=tender）
- [ ] T050 [P] [US1] 迁移 useBiddingDetailPage 到 UserPicker 在 `src/views/Bidding/detail/useBiddingDetailPage.js`（替换 batchTendersApi.getAssignmentCandidates 为 UserPicker mode=candidates context=tender）
- [ ] T051 [P] [US1] 迁移 useTenderDistribution 到 UserPicker 在 `src/views/Bidding/list/useTenderDistribution.js`（替换 batchTendersApi.getAssignmentCandidates 为 UserPicker mode=candidates context=tender）

### P3 - 清理

- [ ] T052 [US1] 从 stores/user.js 移除 users state 在 `src/stores/user.js`（移除 users: [] 初始化，确认无其他引用后删除相关 action）

**Checkpoint**: 22 处选人控件全部迁移到统一 UserPicker，userStore.users 死代码已清理

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 测试验证 + 门禁通过 + 文档更新

- [ ] T053 [P] 编写 UserPicker E2E 测试在 `e2e/user-picker.spec.js`（覆盖：任务表单选人、标讯指派选人、项目创建页选人、空状态、搜索防抖）
- [ ] T054 运行后端全量测试在 `backend/`（mvn test，确认 AssignmentCandidatePolicyTest/AssignmentCandidateAppServiceTest/AssignmentCandidateControllerTest/EventSdkConsumerIntegrationTest 全绿）
- [ ] T055 运行架构测试在 `backend/`（mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest，确认 FP-Java 分层无违规）
- [ ] T056 运行前端构建在仓库根目录（npm run build，确认无编译错误）
- [ ] T057 [P] 运行前端单元测试在仓库根目录（npm run test:unit，确认 useUserPicker.test.js/UserPicker.test.js 全绿）
- [ ] T058 [P] 运行前端数据边界检查在仓库根目录（npm run check:front-data-boundaries，确认无违规导入）
- [ ] T059 [P] 运行文档治理检查在仓库根目录（npm run check:doc-governance，确认文档一致）
- [ ] T060 [P] 运行行数预算检查在仓库根目录（npm run check:line-budgets，确认无超限文件）
- [ ] T061 运行 E2E 测试在仓库根目录（npm run test:e2e，确认 user-picker.spec.js 全绿）
- [ ] T062 [P] 更新 quickstart.md 验证清单在 `.specify/specs/021-user-picker-event-sync/quickstart.md`（勾选所有验证项）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖 - 立即开始
- **Foundational (Phase 2)**: 依赖 Setup 完成 - 阻塞所有 User Story
- **User Story 2 (Phase 3)**: 依赖 Foundational - 事件库 SDK 启用验证
- **User Story 3 (Phase 4)**: 依赖 Foundational - 候选人 API 废弃委托
- **User Story 4 (Phase 5)**: 依赖 Foundational - UserPicker 组件封装
- **User Story 1 (Phase 6)**: 依赖 User Story 4（UserPicker 组件）+ User Story 3（统一候选人 API）- 22 处迁移
- **Polish (Phase 7)**: 依赖所有 User Story 完成

### User Story Dependencies

- **User Story 2 (P1)**: 可在 Foundational 完成后开始 - 无其他 Story 依赖
- **User Story 3 (P2)**: 可在 Foundational 完成后开始 - 独立可测
- **User Story 4 (P2)**: 可在 Foundational 完成后开始 - 独立可测
- **User Story 1 (P1)**: 依赖 User Story 4（UserPicker 组件必须先存在）+ User Story 3（统一候选人 API 必须先可用）

### Within Each User Story

- 测试先写并 FAIL（TDD）
- DTO/Record 先于 Service
- Service 先于 Controller
- 组件 composable 先于组件本身
- 修复失效控件先于常规迁移

### Parallel Opportunities

- Phase 1 的 T002/T003 可与 T001 并行
- Phase 2 的 T004/T005/T006/T008/T010/T012 可并行（不同文件）
- Phase 3 的 T013 可与其他测试并行
- Phase 4 的 T024/T025 可与 T020-T023 并行
- Phase 5 的 T026/T028 可并行
- Phase 6 的 P0 修复（T031-T033）可并行
- Phase 6 的 P1 迁移（T035-T046）可并行
- Phase 6 的 P2 迁移（T047-T051）可并行
- Phase 7 的 T053/T057/T058/T059/T060/T062 可并行

---

## Parallel Example: User Story 1 P0 修复

```bash
# 3 处失效控件可并行迁移（不同文件）：
Task: "迁移 BasicInfoStep 选人到 UserPicker 在 src/views/Project/create/steps/BasicInfoStep.vue"
Task: "迁移 TaskStep 选人到 UserPicker 在 src/views/Project/create/steps/TaskStep.vue"
Task: "迁移 CollaborationCenter 选人到 UserPicker 在 src/components/ai/CollaborationCenter.vue"
```

## Parallel Example: User Story 1 P1 迁移

```bash
# 11 处远程搜索迁移可并行（不同文件）：
Task: "迁移 TaskForm 选人到 UserPicker 在 src/components/project/TaskForm.vue"
Task: "迁移 MentionInput @提及到 UserPicker 在 src/components/common/MentionInput.vue"
Task: "迁移 ProjectCollaboratorsDialog 到 UserPicker 在 src/views/Dashboard/components/ProjectCollaboratorsDialog.vue"
# ... 其余 8 处同理
```

---

## Implementation Strategy

### MVP First（User Story 2 + User Story 4 + User Story 1 P0）

1. 完成 Phase 1: Setup（数据库迁移）
2. 完成 Phase 2: Foundational（统一候选人模块）
3. 完成 Phase 3: User Story 2（事件库 SDK 启用）
4. 完成 Phase 5: User Story 4（UserPicker 组件封装）
5. 完成 Phase 6 P0: User Story 1 失效控件修复（3 处）
6. **STOP and VALIDATE**: 验证事件库同步 + 失效控件修复 + UserPicker 可用

### Incremental Delivery

1. Setup + Foundational → 基础就绪
2. User Story 2 → 事件库同步启用 → 验证（MVP!）
3. User Story 4 → UserPicker 组件 → 验证
4. User Story 1 P0 → 失效控件修复 → 验证
5. User Story 3 → 候选人 API 统一 → 验证
6. User Story 1 P1/P2 → 22 处迁移 → 验证
7. Polish → 全部门禁通过 → PR

### Parallel Team Strategy

With multiple developers:

1. Team 完成 Setup + Foundational
2. Foundational 完成后：
   - Developer A: User Story 2（事件库验证）
   - Developer B: User Story 4（UserPicker 封装）
   - Developer C: User Story 3（候选人 API 废弃）
3. User Story 4 完成后：
   - Developer A/B/C: User Story 1 的 22 处迁移（分批并行）
4. Polish 阶段统一验证

---

## Notes

- [P] 任务 = 不同文件，无依赖
- [Story] 标签映射任务到具体 User Story
- 每个 User Story 应独立可完成和可测试
- 测试先 FAIL 再实现（TDD）
- 每个任务或逻辑组完成后提交
- 在任何 checkpoint 可独立验证 Story
- 避免：模糊任务、同文件冲突、破坏独立性的跨 Story 依赖
- **MVP 优先级**：User Story 2（事件库）+ User Story 4（UserPicker）+ User Story 1 P0（失效修复）为最高优先级
