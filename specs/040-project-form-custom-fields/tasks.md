---
description: "CO-601 项目三表单自定义字段 — 任务拆解"
---

# Tasks: 项目三表单已有字段锁定 + 自定义字段扩展

**Input**: Design documents from `/specs/040-project-form-custom-fields/`

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Constitution III（TDD NON-NEGOTIABLE）+ Blueprint E2E Mandate → 测试任务必须先行（Red → Green → Refactor）

**Organization**: 按 spec.md 三个 User Story 组织（US1=P1 落库回显主链路 / US2=P2 预置字段锁定 / US3=P3 生命周期管理）

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: US1 / US2 / US3
- 所有路径相对仓库根 `/Users/user/xiyu/worktrees/claude`

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 运行 `bash scripts/next-migration-version.sh --reserve` 预约版本号，记录 V#### 编号（供 T011 使用）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 纯函数基础件 + 预置字段清单，三个 User Story 都依赖

**⚠️ CRITICAL**: 本阶段未完成前不得开始任何 User Story

- [ ] T002 [P] 后端单测（红）：`backend/src/test/java/com/xiyu/bid/project/service/CustomFieldsCodecTest.java` — Map↔JSON String 序列化/反序列化、非法 JSON 降级空 Map、null 输入降级空 Map
- [ ] T003 [P] 前端单测（红）：`src/composables/__tests__/useCustomFields.spec.js` — collectCustomFields（schema 减预置清单=自定义 key 集、从 model 摘值、跳过 undefined）/ mergeCustomFieldsIntoModel（摊平进 model 顶层、不覆盖已有预置键、customFields 为空不报错）
- [ ] T004 [P] 实现 `backend/src/main/java/com/xiyu/bid/project/service/CustomFieldsCodec.java` — 纯函数工具：ObjectMapper 构造注入（禁止 new）、`toJson(Map)` / `fromJson(String)` 失败降级 `log.warn` + 空 Map（Constitution VII），<100 行
- [ ] T005 [P] 实现 `src/composables/useCustomFields.js` — 纯函数 composable：`collectCustomFields(model, schemaFields, presetKeys, scope)` / `mergeCustomFieldsIntoModel(model, customFields, scope)`（签名见 contracts §6），不含 UI 提示逻辑（project_memory）
- [ ] T006 预置字段清单核对与落地：`src/views/System/workflow-form-designer/workflowFormDesignerCore.js` 新增 `PROJECT_LOCKED_FIELD_KEYS = { 'project.basic': [...], 'project.initiation': [...], 'project.detail': [...] }`
  - basic/detail 清单以 research.md §D5 为基线（basic: name/customer/budget/industry/region/platform/deadline/manager/competitors；detail: description/tags/startDate/endDate/remark/projectLeaderName/leaderDepartment）
  - **initiation 必须逐个核对**：`InitiationDto` 33 字段 ↔ `InitiationStage.vue` form 实际绑定 key（含 contactName/contactPhone 等联系人组与 customerInfoRows 等特殊结构），输出核对注释写进清单上方
  - 注释互指：前端清单注明"后端 CustomFieldsSchemaPolicy 内嵌同一清单，改动必须双向同步"（T026 落地后端侧）

**Checkpoint**: T002-T005 全绿（新测试通过）、T006 清单评审确认 → 可开始 User Story

---

## Phase 3: User Story 1 - 自定义字段落库与回显主链路 (Priority: P1) 🎯 MVP

**Goal**: 管理员新增的自定义字段在业务页可填、提交落库（DB 可查）、重新打开正确回显

**Independent Test**: project.basic 加一个文本字段并发布 → 创建向导填写提交 → SQL 查到值 → 重开项目回显一致（quickstart.md §2）

### Tests for User Story 1 ⚠️（先写，确认 FAIL 再实现）

- [x] T007 [P] [US1] 后端单测（红）：`backend/src/test/java/com/xiyu/bid/project/service/ProjectServiceCustomFieldsTest.java` — 创建项目带 `customFields` 持久化到 `projects.custom_fields`；详情返回 Map；未知 scope 键过滤丢弃 + log.warn；列 NULL 老数据返回空 Map
- [x] T008 [P] [US1] 后端单测（红）：`backend/src/test/java/com/xiyu/bid/project/service/ProjectInitiationCustomFieldsTest.java` — InitiationDto.customFields 经 Mapper 存入 `project_initiation_details.custom_fields`；InitiationViewDto 返回 Map；按 scope 键整体替换语义（不动其他 scope 键）
- [x] T009 [P] [US1] 前端单测（红）：`src/views/Project/create/composables/__tests__/useProjectCreateModel.customFields.spec.js` — buildApiProjectPayload 含 `customFields: {"project.basic": {...}, "project.detail": {...}}`；loadProjectData 把 customFields 摊平进 basicForm/detailForm
- [x] T010 [P] [US1] 前端单测（红）：`src/views/Project/stages/__tests__/useInitiationStageActions.customFields.spec.js` — buildPayload 含 `customFields: {"project.initiation": {...}}`；load() 在 Object.assign 后把 customFields[scope] 摊平进 form

### Implementation for User Story 1

- [x] T011 [US1] Flyway 迁移（依赖 T001）：`backend/src/main/resources/db/migration-mysql/V####__add_custom_fields_to_project_tables.sql`（两表 `ADD COLUMN custom_fields JSON NULL`）+ 配套 `db/rollback/migration-mysql/U####__...sql`（`DROP COLUMN`，注释注明"回滚丢已存值仅灾备"）；执行后本地库验证列存在
- [x] T012 [P] [US1] 实体加列：`backend/src/main/java/com/xiyu/bid/entity/Project.java` 与 `backend/src/main/java/com/xiyu/bid/project/entity/ProjectInitiationDetails.java` 各加 `@Column(name="custom_fields", columnDefinition="JSON") private String customFields;`
- [x] T013 [P] [US1] DTO 加字段：`project/dto/ProjectRequest.java` 加 `Map<String, Object> customFields`；**先核对** `project/dto/ProjectDTO.java` 与 `dto/ProjectDTO.java` 哪一个是创建/详情实际返回（两处 grep 引用点确认），给实际使用的加 `customFields` 返回字段，核对结论写 PR 描述
- [x] T014 [P] [US1] DTO 加字段：`project/dto/InitiationDto.java` + `project/dto/InitiationViewDto.java` 各加 `Map<String, Object> customFields`
- [x] T015 [US1] 创建/详情链路编排（依赖 T004/T011/T012/T013）：`ProjectService` 创建时 `customFieldsCodec.toJson` 过滤非法 scope 键后落列；详情装配时 `fromJson` 进 DTO
- [x] T016 [US1] 立项链路编排（依赖 T004/T011/T012/T014）：`ProjectInitiationMapper.applyInput`/`mergeForUpdate` 按 scope 键整体替换写列；`InitiationViewDto` 装配 `fromJson`
- [x] T017 [US1] 创建向导接入（依赖 T005/T006）：`src/views/Project/create/composables/useProjectCreateModel.js` — `buildApiProjectPayload()` 末尾加 `customFields`（basic/detail 两 scope 分别 collect）；`loadProjectData()` 加 merge
- [x] T017b [US1] AdaptiveFormPage 混合渲染模式（依赖 T006）：`src/components/common/AdaptiveFormPage.vue` 新增 `hybrid` + `preset-keys` props — hybrid=true 时 fallback-form 始终渲染（保留复杂交互），DynamicFormRenderer 追加渲染 `fields − presetKeys`；hybrid=false（默认）行为零变化（tender.entry 不受影响）
  - **背景**：实施期实测确认 project.initiation/project.detail 当前 schema 为空（`{"fields":[]}`，V1078/V1082），业务页走 fallback 硬编码表单；若发布非空 schema，DynamicFormRenderer 会整体替换 fallback，保证金/客户矩阵/审批/OBS 上传等复杂交互全灭。project.basic 当前 schema 已有 8 字段（V140 种子），已走纯 schema 渲染，无需 hybrid
- [x] T018 [US1] 立项页接入（依赖 T005/T006/T017b）：`src/views/Project/stages/useInitiationStageActions.js` — `buildPayload()` 加 `customFields` collect；`load()` 在 `Object.assign(form, data)` 后 merge；`InitiationStage.vue` / `DetailStep.vue` 的 AdaptiveFormPage 传 `hybrid` + 对应 scope preset-keys（BasicInfoStep 维持纯 schema 渲染，不传）
  - **实施补充**：Create.vue 通过 `@schema-loaded → model.setCustomFieldsSchema(scope, fields)` 登记 schema；为守 line-budget 拆出 `useProjectCreateCustomFields.js`（注册表）与 `queryDecode.js`（query 解码），Create.vue 388 行零增长、model 288 行
- [x] T019 [US1] 后端门禁验证：`cd backend && mvn test -Dtest='*CustomFields*'` + `mvn test -Dtest=ArchitectureTest,FlywayRollbackScriptCoverageTest` 全绿

**Checkpoint**: US1 独立可用 — quickstart.md §2 步骤 1-5 手动走通（basic 当前设计器可发布，可先绕开 US2 验证主链路）

---

## Phase 4: User Story 2 - 系统预置字段防误改保护 (Priority: P2)

**Goal**: 三 scope 预置字段 key/type 不可改、不可删；initiation/detail 从整表只读解禁为"预置锁定 + 自定义可增"

**Independent Test**: 设计器中预置字段 key/type 输入框禁用、删除按钮隐藏；自定义字段编辑/删除/拖拽正常；tender.entry 行为零变化

### Tests for User Story 2 ⚠️（先写，确认 FAIL 再实现）

- [ ] T020 [P] [US2] 前端单测（红）：`src/views/System/workflow-form-designer/components/__tests__/DesignerFieldList.scopeLock.spec.js` — project.* scope 下预置字段 isLocked=true（key/type disabled、删除隐藏）、自定义字段全可用；tender.entry 走原 LOCKED_FIELD_KEYS/FIXED_GROUP_KEYS 不变
- [ ] T021 [P] [US2] 后端单测（红）：`backend/src/test/java/com/xiyu/bid/formengine/domain/CustomFieldsSchemaPolicyTest.java` — 自定义 key 撞预置清单拒绝、自定义 key 互撞拒绝、合法 schema 放行、非项目 scope 不校验

### Implementation for User Story 2

- [ ] T022 [US2] `workflowFormDesignerCore.js` 导出 `PROJECT_LOCKED_FIELD_KEYS`（T006 产物）+ scope 判定 helper（如 `isProjectScope(scope)`）
- [ ] T023 [US2] `DesignerFieldList.vue`（依赖 T022）：`isLocked/isKeyLocked/isFixedGroup` 加 scope 维度 — project.* 查 PROJECT_LOCKED_FIELD_KEYS[scope]，tender.entry 走原清单；需要 scope prop 或从注入的 draft 取
- [ ] T024 [US2] `WorkflowFormDesigner.vue`：移除 `UNSUPPORTED_PROJECT_SCOPES` 整表只读（L106-109 及模板中全部 `isUnsupportedProjectScope` 引用），保存/发布/表单名/启用/新增字段对 project.initiation、project.detail 解禁
- [ ] T025 [US2] 设计器保存/发布前校验（依赖 T022）：`WorkflowFormDesigner.vue` saveAll/publish 前跑 key 冲突检查（撞预置清单/自定义互撞 → ElMessage 阻断指出冲突 key）
- [ ] T026 [US2] 后端兜底校验（依赖 T006/T021）：`backend/src/main/java/com/xiyu/bid/formengine/domain/CustomFieldsSchemaPolicy.java` 纯函数（内嵌三 scope 预置清单，注释与前端互指）；挂载 `FormDefinitionAdminService` 保存草稿 + publish 路径，冲突返回 400 + ApiResponse.msg 指出冲突 key；非项目 scope 跳过
- [ ] T027 [US2] tender.entry 回归验证：设计器打开 tender.entry，8 个 LOCKED + 8 个 FIXED_GROUP 字段行为与 main 分支一致（人工对照 + T020 单测）

**Checkpoint**: US1+US2 均独立可用 — 三 scope 新增/保存/发布全通，预置字段 100% 机制拦截（SC-003）

---

## Phase 5: User Story 3 - 自定义字段全生命周期管理 (Priority: P3)

**Goal**: 自定义字段编辑 label 重发布回显不变；删除字段不再渲染但历史值保留；类型变更后历史值不报错

**Independent Test**: 改 label 重发布 → 业务页新 label 旧值仍在；删字段 → 新表单不渲染、DB 值保留；文本改下拉 → 历史值按文本兜底展示

### Tests for User Story 3 ⚠️（先写，确认 FAIL 再实现）

- [ ] T028 [P] [US3] 前端单测（红）：DynamicFormRenderer 对下拉字段无法匹配的存量值按纯文本兜底渲染（`src/components/common/__tests__/DynamicFormRenderer.fallback.spec.js`）

### Implementation for User Story 3

- [ ] T029 [US3] `DynamicFormRenderer.vue`：确认 select/radio 等枚举类字段对失配值的现有渲染行为；若报错/空白则补文本兜底分支（优先验证现状，能用则不改 — Constitution VIII）
- [ ] T030 [US3] 验证删除字段后 DB 历史值保留（无需代码，SQL 确认 + E2E 断言即可）；如发现合并逻辑误删历史键（merge 时整列覆盖），修 T016 写列逻辑为"保留非当前 scope 键 + 当前 scope 键整体替换"

**Checkpoint**: 三个 User Story 全部独立可用

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T031 [US1+US2+US3] E2E 全链路：`e2e/project-form-custom-fields.spec.js` — 设计器新增字段并发布（三 scope）→ basic/创建向导填写提交 → 立项填写提交 → 重开回显一致 → 预置字段锁定断言 → 老项目（无 custom_fields）打开不报错 → 删除字段不再渲染；遵循 e2e-selectors 门禁
- [ ] T032 [P] 前端门禁：`npm run check:front-data-boundaries && npm run check:line-budgets && npm run build && npm run test:unit` 全绿
- [ ] T033 [P] 后端门禁：`cd backend && mvn test` 全量 + `mvn -Pjava-quality checkstyle:check`（带 profile，CLAUDE.md 坑点 11）全绿
- [ ] T034 quickstart.md §1-§4 全量走查（含 SQL 落库验证、回滚脚本演练记录）
- [ ] T035 知识沉淀：关键决策/坑点追加 `docs/lessons/lessons-learned.md` 或对应既有文档（禁止新建顶层 md）；过 `agent-finish-task.sh` wiki checkpoint

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 无依赖，立即开始
- **Phase 2 Foundational**: 依赖 T001；**阻塞所有 User Story**
- **Phase 3 US1 (P1)**: 依赖 Phase 2 — MVP 主链路
- **Phase 4 US2 (P2)**: 依赖 Phase 2 + T006 清单；与 US1 共享 T006 但实现文件不同，可部分并行
- **Phase 5 US3 (P3)**: 依赖 US1（回显链路）+ US2（设计器解禁）
- **Phase 6 Polish**: 依赖 US1+US2+US3

### 关键依赖链

```text
T001 → T011（迁移）
T002/T003（测试先红）→ T004/T005（实现转绿）
T006（清单）→ T017/T018（业务页 collect）→ T022/T025/T026（设计器+后端校验）
T011+T012+T013 → T015（创建链路）
T011+T012+T014 → T016（立项链路）
T024（解禁）→ T031（E2E 走 UI 发布 schema）
```

### Parallel Opportunities

- T002/T003 测试编写并行；T004/T005 实现并行
- T007/T008/T009/T010 全部测试并行
- T012/T013/T014 DTO/实体加字段并行
- T015（后端创建链）与 T016（后端立项链）与 T017/T018（前端接入）三路并行
- T032/T033 前后端门禁并行

---

## Implementation Strategy

### MVP First（仅 US1）

1. Phase 1 + Phase 2 → 基础件就绪
2. Phase 3 US1 → 主链路可用（basic 可先行手动验证）
3. **STOP**：quickstart §2 步骤 1-5 走通后再进 US2

### 全量交付

US1 → US2 → US3 顺序推进，每 Phase 末过 Checkpoint；Phase 6 门禁全绿后提 PR（前后端同特性可同 PR，但 commit 按后端/前端/测试/E2E 原子拆分）。

## Notes

- 迁移版本号冲突：如 T001 预约后他人抢先合入同号，pre-push gate 会自动重编号，以最终 push 时版本为准
- 原子提交纪律：每任务或逻辑组一次 commit；WIP 每日 push（Constitution 协作纪律）
- 单文件 <300 行棘轮门禁：DesignerFieldList.vue / WorkflowFormDesigner.vue 改动后须确认未超限
- 禁止 `git push --no-verify`；遇 Flyway checksum 失败先 `rm -rf backend/target` 再重跑（CLAUDE.md 坑点）
