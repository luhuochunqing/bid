# Phase 0 Research: 项目三表单自定义字段扩展

**Date**: 2026-07-31 | **Branch**: `agent/claude/co601-project-form-custom-fields`

## 1. 数据落点现状（已核实）

| scope | 业务页 | 落库表 | 提交接口 | 请求 DTO |
|---|---|---|---|---|
| `project.basic` | BasicInfoStep.vue（创建向导第 1 步） | `projects` | `POST /api/projects` | `ProjectRequest`（25 个固定字段） |
| `project.detail` | DetailStep.vue（创建向导第 2 步） | `projects`（同上，一次提交） | `POST /api/projects` | `ProjectRequest` |
| `project.initiation` | InitiationStage.vue（立项阶段） | `project_initiation_details` | `POST /api/projects/{projectId}/initiation` | `InitiationDto`（33 个固定字段） |

**关键证据**：
- 创建向导 basic+detail 两步数据合并为一次提交，payload 是**硬编码白名单**（[useProjectCreateModel.js:224-265](file:///Users/user/xiyu/worktrees/claude/src/views/Project/create/composables/useProjectCreateModel.js#L224-L265) 逐字段摘取，自定义字段直接丢弃）
- 立项 payload 是 `...form` 全展开（[useInitiationStageActions.js:154-164](file:///Users/user/xiyu/worktrees/claude/src/views/Project/stages/useInitiationStageActions.js#L154-L164)），但后端 DTO 字段固定
- 全仓无 `fail-on-unknown-properties` 配置 → Spring Jackson 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false` → **DTO 之外的字段被静默丢弃**。这就是"能填能渲染、提交即丢"的根因
- `projects` 表 64 字段无 JSON 扩展列；`project_initiation_details` 已有 JSON 列先例 `customer_info_json`（[ProjectInitiationDetails.java:150-152](file:///Users/user/xiyu/worktrees/claude/backend/src/main/java/com/xiyu/bid/project/entity/ProjectInitiationDetails.java#L150-L152)）

## 2. 动态表单渲染与数据流（已核实）

- `DynamicFormRenderer.vue` 全部字段用 `v-model="localValue[field.key]"` 绑定 → **field.key 直接映射 model 顶层属性**，自定义字段只要进 model 就能渲染和编辑
- 回显链路两页不同：
  - 创建向导 `loadProjectData` 逐字段白名单摘取（[useProjectCreateModel.js:186-200](file:///Users/user/xiyu/worktrees/claude/src/views/Project/create/composables/useProjectCreateModel.js#L186-L200)）→ 需补自定义字段合并
  - 立项 `load()` 用 `Object.assign(form, data)` 全量合并（[useInitiationStageActions.js:171](file:///Users/user/xiyu/worktrees/claude/src/views/Project/stages/useInitiationStageActions.js#L171)）→ 后端返回 `customFields` 对象会挂在 `form.customFields` 上，但渲染器需要值在 model 顶层 → **必须显式把 customFields[scope] 的键值摊平进 form**

## 3. 设计器锁定机制现状（已核实）

- 整表只读：`UNSUPPORTED_PROJECT_SCOPES = ['project.initiation', 'project.detail']`（[WorkflowFormDesigner.vue:108](file:///Users/user/xiyu/worktrees/claude/src/views/System/WorkflowFormDesigner.vue#L108)），readonly 传给 DesignerFieldList，保存/发布按钮 disabled（L63-64）
- 字段级锁定：`DesignerFieldList.vue:107-109` 消费三个清单，但**是全局数组、不按 scope 区分**：
  - `LOCKED_FIELD_KEYS`（tender.entry 的 8 个特殊渲染字段：key/type 锁 + 禁删）
  - `FIXED_GROUP_KEYS`（tender.entry 联系人组 8 个：禁拖拽 + key 锁，type 可改）
  - `KEY_LOCKED_FIELD_KEYS = LOCKED + FIXED_GROUP`
- **问题**：若直接把项目表单字段 key 加入现有全局数组，会污染 tender.entry（如 tender.entry 也有 `region` 字段会被项目清单重复锁定语义覆盖）。必须改为按 scope 的映射结构

## 4. Flyway 与 JSON 列模式（已核实）

- 当前最大版本 **V1182**（迁移目录 `backend/src/main/resources/db/migration-mysql/`），新增必须用 `bash scripts/new-migration.sh <描述>` 自动取号
- JSON 列读写先例（customerInfoJson 模式）：DTO 用强类型 `List<CustomerInfoRow>`，Mapper 手工 `ObjectMapper.writeValueAsString` 序列化存入 String 列；读取时反序列化
- ProjectRequest 中 `tagsJson`/`competitorAnalysisJson` 是 `String` 类型 DTO 字段，前端传 `JSON.stringify(...)` 后的字符串

## 5. 设计决策

### D1: 存储 — 两表各加 `custom_fields` JSON 列
- `projects.custom_fields JSON NULL`、`project_initiation_details.custom_fields JSON NULL`
- **Decision**: 新增 V 迁移 + 配套 U 回滚脚本（`new-migration.sh` 取号）
- **Rationale**: 复用 customer_info_json 已验证模式；不动既有 64/33 字段；历史数据天然为空（FR-010）
- **Alternatives rejected**: EAV 子表（过度设计，违背 Constitution VIII 平淡模式）；Mongo 式自由文档（技术栈外）

### D2: JSON 值结构 — 按 scope 命名空间两级结构
```json
{ "project.basic": { "budgetLevel": "重点客户" }, "project.detail": { "siteVisitDone": true } }
```
- **Decision**: `projects.custom_fields` 用 scope 命名空间（basic/detail 共用一表，防 key 冲突）；`project_initiation_details.custom_fields` 同样用 `{ "project.initiation": {...} }` 结构保持前后端逻辑统一
- **Rationale**: 前端收集/合并逻辑可以只写一套（始终以 scope 为键）；创建向导一次提交天然携带两个 scope 分组
- **Alternatives rejected**: projects 表加两列 basic_/detail_custom_fields（列膨胀、scope 语义散落 DDL）；平铺 {key:value}（basic 与 detail 自定义 key 撞名互相覆盖）

### D3: 后端读写 — DTO `Map<String, Object> customFields` + Service 手工序列化
- `ProjectRequest` / `InitiationDto` 加 `private Map<String, Object> customFields;`（Jackson 原生绑定 JSON 对象，无需前端 stringify）
- 详情/回显 DTO（`ProjectDTO` / `InitiationViewDto`）同样加 `customFields` 返回
- 实体侧 `customFields` 为 `String`（`columnDefinition = "JSON"`），Service/Mapper 用注入的 ObjectMapper Bean 序列化/反序列化（project_memory：禁止 new ObjectMapper）
- 反序列化失败降级为空 Map + `log.warn`（Constitution VII 防御性降级）
- **Rationale**: 与 customerInfoJson 模式一致；Map 比 String 对前端更友好（无需双重编码）

### D4: 前端数据通道 — 新增 `useCustomFields` composable（单一复用点）
- 输入：schema fields、当前 scope 预置字段清单、form model
- `collectCustomFields(model, schemaFields, presetKeys)` → `{ [scope]: {key: value} }`：schema 字段减去预置清单 = 自定义字段 key 集，从 model 摘值
- `mergeCustomFieldsIntoModel(model, customFields, scope)` → 回显时把 `customFields[scope]` 摊平进 model 顶层
- 三个业务页接入点：
  - 创建向导：`buildApiProjectPayload()` 末尾加 `customFields: collect(...)`；`loadProjectData` 加 merge
  - 立项：`buildPayload()` 加 `customFields: collect(...)`；`load()` 在 `Object.assign` 后 merge
- **Rationale**: project_memory 硬约束——公共逻辑集中 composable，禁止多组件重复编码

### D5: 设计器锁定改造 — 全局数组改按 scope 映射
- `workflowFormDesignerCore.js` 新增：
  ```js
  export const PROJECT_LOCKED_FIELD_KEYS = {
    'project.basic': [...], 'project.initiation': [...], 'project.detail': [...]
  }
  ```
- `DesignerFieldList.vue` 的 `isLocked/isKeyLocked` 加 scope 维度（从 draft.scope 取）：项目 scope 查 PROJECT_LOCKED_FIELD_KEYS[scope]，tender.entry 走原清单
- 移除 `UNSUPPORTED_PROJECT_SCOPES` 整表只读（WorkflowFormDesigner.vue:108），保存/发布/新增字段解禁；预置字段靠锁定清单保护（key/type 禁改、禁删）
- **project.basic 预置清单**（以 loadProjectData + payload 实际消费为准）：name / customer / budget / industry / region / platform / deadline / manager / competitors
- **project.detail 预置清单**：description / tags / startDate / endDate / remark / projectLeaderName / leaderDepartment
- **project.initiation 预置清单**：InitiationDto 33 字段对应的 form key（实现期逐个核对 InitiationStage form 绑定，tasks.md 单列核对任务）
- **Rationale**: 清单来源 = 业务页真实消费字段，避免 PR !2229 "锁定清单与 fallback 字段不一致"的偏差

### D6: key 冲突校验（FR-006）
- 前端设计器保存/发布前：自定义字段 key 不得命中当前 scope 预置清单、自定义字段之间不得重复 → 阻断 + ElMessage 提示
- 后端兜底：formengine 保存 schema 时对项目三 scope 做同样校验（防止绕过前端直调 API）；校验逻辑放纯函数 Policy（Constitution I），Service 编排调用

### D7: 历史值兜底（Edge Case）
- 字段类型被改（文本→下拉）后历史值渲染：DynamicFormRenderer 对无法匹配选项的值按纯文本展示（实现期确认渲染器现有行为，不足则补兜底分支）
- 被删自定义字段不渲染（schema 驱动天然满足），DB JSON 中历史键值保留（FR-011，不主动清理）

## 6. Constitution 对齐检查

| 原则 | 落点 |
|---|---|
| I. FP-Java | key 冲突校验、customFields 合并/收集为纯函数（前端 composable / 后端 Policy），Service 仅编排 |
| II. Real-API Only | 全链路真实 API，无 Mock |
| III. TDD | 后端单测（序列化/反序列化/DTO 绑定/Policy 校验）→ 前端单测（composable/锁定逻辑）→ E2E 全链路 |
| IV. Split-First | 新增文件 <300 行；DesignerFieldList 改动控制在既有职责内 |
| VI. Authorization | 设计器权限沿用现状，不新增权限模型（spec Assumptions） |
| VII. 防御性 | JSON 反序列化失败降级空 Map + log.warn；5xx handler 不动 |
| VIII. 平淡模式 | 复用 customer_info_json 已验证模式，无新框架 |

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| initiation 预置清单与 form 实际 key 不一致 → 锁定错字段 | tasks.md 单列"清单核对"任务，以 InitiationDto + form 绑定双向核对，E2E 锁定断言兜底 |
| 创建向导一次提交含两个 scope 的 customFields，后端存储合并覆盖 | D2 命名空间结构天然隔离；更新场景后端按 scope 键 merge 而非整体覆盖（实现期注意 PUT/PATCH 语义） |
| DynamicFormRenderer 对未知类型/失配值渲染异常 | D7 兜底分支 + E2E 边界用例 |
| 老数据无 custom_fields 值 | 列可 NULL，回显时空值正常渲染（FR-010），E2E 覆盖 |
