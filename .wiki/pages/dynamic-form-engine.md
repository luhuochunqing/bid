---
title: 动态表单自定义引擎
space: engineering
category: feature
tags: [表单, 动态表单, 自定义, schema, 字段可见性, 跨字段验证, 多租户]
created: 2026-05-24
updated: 2026-07-31
health_checked: 2026-08-12
sources:
  - backend/src/main/java/com/xiyu/bid/formengine/
  - backend/src/main/resources/db/migration-mysql/V140__dynamic_form_engine.sql
  - backend/src/main/resources/db/migration-mysql/V141__cross_field_validation.sql
  - backend/src/main/resources/db/migration-mysql/V142__tenant_form_override.sql
  - backend/src/main/resources/db/migration-mysql/V143__form_submission_audit.sql
  - backend/src/main/resources/db/migration-mysql/V1183__add_custom_fields_to_project_tables.sql
  - backend/src/main/java/com/xiyu/bid/project/service/CustomFieldsCodec.java
  - backend/src/main/java/com/xiyu/bid/formengine/domain/CustomFieldsSchemaPolicy.java
  - src/components/common/AdaptiveFormPage.vue
  - src/composables/useCustomFields.js
  - src/views/System/workflow-form-designer/workflowFormDesignerCore.js
  - docs/artifacts/dynamic-form-engine-v1-20260524.html
  - docs/testing/form-engine-uat.md
  - specs/040-project-form-custom-fields/
backlinks:
  - _index
---
# 动态表单自定义引擎

> M4.2 功能交付文档 — 表单自定义适配器升级方案 v1.0

## 1. 概述

动态表单自定义引擎（Dynamic Form Engine）是西域数智化投标管理平台的**运行时表单自定义基础设施**。它将原来硬编码在 Vue 组件中的表单字段定义为可配置、可发布、可按角色/租户定制的运行时数据，使管理员无需修改代码即可调整业务表单的字段、验证规则和可见性。

### 1.1 与原有 workflowform 的关系

| 维度 | workflowform（~泛微 OA 表单~ 已取消） | formengine（动态表单引擎） |
|------|--------------------------|--------------------------|
| **用途** | OA 审批流程配置 | 全系统业务表单运行时自定义 |
| **配置方式** | 管理员 UI + JSON Schema | 管理员 UI + JSON Schema |
| **角色可见性** | 泛微 OA 平台控制 | 独立 `form_field_visibility` 表 |
| **跨字段验证** | 无 | 支持 8 种操作符 |
| **多租户覆盖** | 无 | 租户级 label/required/options 覆盖 |
| **存储位置** | `workflow_form_*` 表 | `form_definition_registry` 等新表 |

两者**并行独立**，原有 OA 审批功能不受影响。

---

## 2. 架构

```
前端
  ├─ AdaptiveFormPage.vue       # 页面级包装：优先加载动态 schema，降级到 fallback
  ├─ DynamicFormRenderer.vue   # Schema → Element Plus 组件渲染引擎
  └─ WorkflowFormDesigner.vue  # 管理员 UI：字段编辑、版本管理、角色预览

后端
  ├─ formengine.domain         # 纯数据 record（无框架依赖）
  │     FieldVisibility        # 字段可见性规则
  │     CrossFieldValidationRule # 跨字段验证规则
  │     ResolvedField / ResolvedForm # 计算后的运行时模型
  │     ValidationResult / SubmitResult
  │
  ├─ formengine.application    # 纯核心业务逻辑
  │     AdaptiveFormService    # Schema 加载 + 字段过滤 + 条件计算 + 验证编排
  │     RoleBasedFieldFilter  # 角色/组织级字段可见性过滤
  │     TenantOverrideService  # 租户字段覆盖
  │     ConditionEvaluator     # 字段间依赖条件求值
  │     CrossFieldValidator   # 跨字段验证执行器
  │     FormSchemaParser      # JSON Schema → 内存模型
  │     FormSubmissionAuditService # 提交审计写入
  │
  └─ formengine.infrastructure # 持久化 + REST API
        FormDefinitionController   # 运行时 API（表单渲染、验证、提交）
        FormDefinitionAdminController # 管理 API（CRUD、发布、规则保存）

数据库（V140-V143）
  form_definition_registry       # 表单定义元注册（scope / version / schema_json）
  form_field_visibility         # 字段可见性规则（角色/组织级别）
  form_field_condition          # 字段间依赖条件
  cross_field_validation_rule    # 跨字段验证规则
  tenant_form_field_override    # 租户级字段覆盖
  form_submission_audit         # 提交审计日志
```

### 2.1 纯核心与外壳分离

按 FP-Java Profile，`formengine.domain` 中的 record 均不含框架依赖，`formengine.application` 中的 service 为纯核心，不直接操作 JPA Repository。持久化在 `formengine.infrastructure` 层实现。

---

## 3. 核心能力

### 3.1 20+ 字段类型

| 类型 | 说明 | 类型 | 说明 |
|------|------|------|------|
| TEXT | 单行文本 | TEXTAREA | 多行文本 |
| NUMBER | 数字 | CURRENCY | 货币金额 |
| PERCENT | 百分比 | EMAIL | 邮箱 |
| PHONE | 电话 | DATE | 日期 |
| DATETIME | 日期时间 | ADDRESS | 地址 |
| SELECT | 下拉选择 | MULTI_SELECT | 多选 |
| RADIO | 单选按钮 | CHECKBOX | 复选框 |
| FILE | 文件上传 | IMAGE | 图片上传 |
| TABLE | 子表格 | PERSON | 人员选择 |
| DEPT | 部门选择 | PROJECT | 项目选择 |

### 3.2 字段级增强属性

每个字段支持：`placeholder`、`rows`、`min/max`、`minLength/maxLength`、`customRegex`、`errorMessage`、`options`（选项列表）、`limit`（文件数限制）、`accept`（文件类型）、`hidden`、`readonly`、`columns`（表格列定义）、`minRows/maxRows`。

### 3.3 跨字段验证（8 种操作符）

| 操作符 | 说明 | 示例 |
|--------|------|------|
| `less_than` | A < B | `budget < estimated_cost` |
| `greater_than` | A > B | `win_rate > 50` |
| `equals` | A == B | `status == 'draft'` |
| `not_equals` | A != B | `status != 'cancelled'` |
| `sum_equals` | A + B == C | `pre_tax + tax == total` |
| `one_filled` | 至少填一个 | `contact_phone` 或 `contact_email` |
| `both_filled` | 必须同时填 | `start_date` 和 `end_date` |
| `not_after` | 日期 A ≤ 日期 B | `start_date ≤ end_date` |

### 3.4 字段间依赖条件

支持字段间联动：`show` / `hide` / `require` / `skip` / `readonly`，操作符包括 `eq/neq/in/not_in/contains/gt/gte/lt/lte`。

### 3.5 角色级可见性控制

| 规则 | 说明 |
|------|------|
| `visible=true` | 字段可见 |
| `readonly=true` | 字段只读（不隐藏） |
| `hidden=true` | 字段完全隐藏（优先级最高） |

`hidden` 与 `readonly` 取 OR 合并（最严格），`visible` 仅在明确指定且无 `hidden` 时为 true。

### 3.6 多租户字段覆盖

支持租户级覆盖：`label`、`required`、`default_value`、`options`、`hidden`、`readonly`。覆盖在运行时合并，不修改全局模板。

### 3.7 审计日志

每次表单提交（成功/验证失败/处理错误）均写入 `form_submission_audit`，记录操作人、租户、表单数据 SHA-256 哈希、JSON 快照和状态。

---

## 4. API 端点

### 4.1 运行时 API（需认证）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/form-definitions/{scope}/active` | 获取当前活跃 schema（含角色过滤） |
| POST | `/api/form-definitions/{scope}/validate` | 验证表单数据 |
| POST | `/api/form-definitions/{scope}/submit` | 提交表单（写入业务 + 审计） |

### 4.2 管理 API（需 ADMIN 角色）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/admin/form-definitions` | 分页列表 |
| POST | `/api/admin/form-definitions` | 创建新定义 |
| GET | `/api/admin/form-definitions/{id}` | 获取详情 |
| PUT | `/api/admin/form-definitions/{id}` | 更新定义 |
| DELETE | `/api/admin/form-definitions/{id}` | 删除定义 |
| POST | `/api/admin/form-definitions/{id}/publish` | 发布（递增版本） |
| POST | `/api/admin/form-definitions/{id}/visibility` | 保存可见性规则 |
| POST | `/api/admin/form-definitions/{id}/conditions` | 保存条件规则 |

---

## 5. 种子数据

V140 迁移脚本预注册 4 个核心业务域：

| Scope | 名称 | 用途 |
|-------|------|------|
| `tender.entry` | 标讯手工录入 | 投标手工录入 Dialog |
| `project.basic` | 项目基本信息 | 项目创建基本信息步骤 |
| `resource.expense` | 费用申请 | 费用申请表单 |
| `knowledge.case` | 案例建档 | 知识库案例建档 |

---

## 6. 数据库迁移

| 版本 | 迁移文件 | 说明 |
|------|---------|------|
| V140 | `V140__dynamic_form_engine.sql` | form_definition_registry / form_field_visibility / form_field_condition + 种子数据 |
| V141 | `V141__cross_field_validation.sql` | cross_field_validation_rule 表 |
| V142 | `V142__tenant_form_override.sql` | tenant_form_field_override 表 |
| V143 | `V143__form_submission_audit.sql` | form_submission_audit 表 |

回滚脚本：`db/rollback/migration-mysql/U140-U143`。

---

## 7. 测试

UAT 测试文档：`docs/testing/form-engine-uat.md`（43 个用例，覆盖 M1-M6）。

---

## 8. 前端集成状态

| 页面 | 组件 | 集成状态 |
|------|------|---------|
| 投标手工录入 | `ManualTenderDialog.vue` | ✅ 已集成 AdaptiveFormPage |
| 项目基本信息 | `BasicInfoStep.vue` | ✅ 已集成 AdaptiveFormPage |
| 项目详情步骤 | `DetailStep.vue` | ✅ 已集成 AdaptiveFormPage |
| 项目立项阶段 | `InitiationStage.vue` | ✅ 已集成 AdaptiveFormPage |
| 评标表单 | `TenderEvaluationFormAdaptive.vue` | ✅ 新增 |
| 表单设计器 | `WorkflowFormDesigner.vue` | ✅ 扩展支持 20+ 字段类型 |

---

## 9. CO-601 hybrid 渲染模式与自定义字段扩展（2026-07-31 / PR !2235）

> CO-601 为项目三表单（`project.basic` / `project.initiation` / `project.detail`）引入自定义字段扩展能力，同时引入 hybrid 渲染模式解决 fallback 硬编码表单与动态 schema 共存问题。

### 9.1 问题背景：fallback 与动态 schema 的冲突

CO-601 实施前，`project.initiation` / `project.detail` 的 schema 为空（`{"fields":[]}`，V1078/V1082），业务页走 `AdaptiveFormPage` 的 fallback 硬编码表单（含保证金、客户矩阵、审批、OBS 上传等复杂交互）。若直接发布非空 schema，`DynamicFormRenderer` 会整体替换 fallback，导致所有复杂交互全灭。

`project.basic` 当前 schema 已有 8 字段（V140 种子），已走纯 schema 渲染，不需要 fallback。

### 9.2 hybrid 渲染模式

`AdaptiveFormPage.vue` 新增 `hybrid` + `preset-keys` 两个 props：

| Prop | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `hybrid` | Boolean | `false` | true 时 fallback-form 始终渲染 + DynamicFormRenderer 追加渲染 `fields − presetKeys`（仅自定义字段） |
| `preset-keys` | Array | `[]` | 预置字段 key 清单，用于从 schema 中减去预置字段，仅渲染自定义字段 |

**渲染逻辑**：

```
hybrid=false（默认）：
  有 schema → DynamicFormRenderer 整体渲染（行为零变化，tender.entry 不受影响）
  无 schema → fallback 硬编码表单

hybrid=true：
  fallback 硬编码表单始终渲染（保留复杂交互）
  + DynamicFormRenderer 追加渲染 schema.fields − presetKeys（仅自定义字段）
```

**当前使用情况**：

| Scope | hybrid | preset-keys | 原因 |
|-------|--------|-------------|------|
| `project.basic` | `false` | 不传 | schema 已有 8 字段（V140 种子），走纯 schema 渲染 |
| `project.initiation` | `true` | 33 个预置字段 key | fallback 含保证金/客户矩阵/审批/OBS 上传等复杂交互 |
| `project.detail` | `true` | 7 个预置字段 key | fallback 含描述/标签/日期/备注等硬编码表单 |
| `tender.entry` | `false`（默认） | 不传 | 行为零变化 |

### 9.3 自定义字段持久化

**数据库**：V1183 迁移为 `projects` 和 `project_initiation_details` 各加 `custom_fields JSON NULL` 列。

**编解码**：`CustomFieldsCodec` 纯函数工具类，ObjectMapper 构造注入，失败降级空 Map（Constitution VII）。

**scope 键整体替换语义**：`projects.custom_fields` 单列存储所有 scope 的自定义字段，merge 时按 scope 键整体替换，保留非当前 scope 键。这确保删除字段不误删其他 scope 历史值（US3）。

```
projects.custom_fields = {
  "project.basic": { "budgetLevel": "重点客户" },
  "project.detail": { "remark": "特殊备注" }
}
```

### 9.4 预置字段锁定

**前端**：`workflowFormDesignerCore.js` 导出 `PROJECT_LOCKED_FIELD_KEYS`（三 scope 预置清单）+ `isProjectScope` + `validateCustomFieldKeyConflicts` 纯函数。

**后端兜底**：`CustomFieldsSchemaPolicy` 纯函数（内嵌三 scope 预置清单，注释与前端互指），挂载 `FormDefinitionAdminService` create/update + publish 路径，冲突返回 400 + ApiResponse.msg 指出冲突 key。

**锁定行为**：

| 操作 | 预置字段 | 自定义字段 |
|------|---------|----------|
| key 修改 | ❌ 禁用 | ✅ 可改 |
| type 修改 | ❌ 禁用 | ✅ 可改 |
| 删除 | ❌ 按钮隐藏 | ✅ 可删 |
| 新增 | N/A | ✅ 可增 |

**前后端双向校验**：前端纯函数负责 UX 阻断，后端 Policy 兜底防护。注释互指，禁止单边改动。

### 9.5 生命周期管理（US3）

- **改 label 重发布**：schema 字段 label 变更，业务页新 label 旧值仍在（值按 key 而非 label 关联）
- **删除字段**：新表单不渲染，DB 历史值保留（删 schema 字段仅 UPDATE `form_definition_registry`，不触碰 `projects.custom_fields`）
- **类型变更**（如文本改下拉）：历史值按文本兜底展示（el-select 原生支持失配值文本兜底，无需额外代码）

### 9.6 关键坑点

1. **H2 JSON 列双重编码**：H2 测试环境读取 JSON 列返回字符串包裹 JSON，`parseSchema` 需 `readTree` 检测 `isTextual()` 后二次 `readTree(node.asText())` 解析（与 `FormSchemaParser` 同一兜底模式）
2. **base 漂移导致 PR 反向 diff**：任务分支长期开发期间 main 合入其他 PR，推送前必须 `rebase origin/main`，用 `git diff origin/main..HEAD -- <已合入文件>` 验证是否出现反向 diff
3. **hybrid 默认 false**：仅在需要 fallback + 动态 schema 共存的 scope 显式传 true，行为零变化

### 9.7 相关文档

- `specs/040-project-form-custom-fields/` — 完整 spec/plan/tasks/research/data-model/contracts/quickstart
- `specs/040-project-form-custom-fields/quickstart.md` §6 — T034 走查结果记录（§1-§5 验证结论 + E2E 失败根因分析）
- `docs/lessons/lessons-learned.md` §91-§96 — 6 条工程教训（§96: E2E 测试失败三类根因模式）
- `e2e/project-form-custom-fields.spec.js` — 9 用例 E2E 全链路验证（**当前 9 个全失败，根因为测试代码数据污染+角色权限不匹配+后端 OOM，非产品代码缺陷，待后续任务修复**）

### 9.8 E2E 测试失败根因与判别流程（T034 走查发现）

T034 走查时 E2E 9 个全部失败，但手动 API 验证证明 CO-601 产品代码正常。三类根因（均非产品代码缺陷）：

1. **测试数据污染**：`beforeEach` 未清理上一次运行的表单定义残留字段，累积导致 PUT 报 "key 重复"
2. **角色权限不匹配**：测试用 `bid-Team` 创建项目，但 `POST /api/projects` 要求 `ADMIN/MANAGER`，被 Access Denied
3. **后端 OOM 崩溃**：E2E 并发请求触发 `exit code: 137`（SIGKILL），后续测试全部 `ECONNREFUSED`

**判别流程**：E2E 失败时，先用 admin 手动跑通同一 API 链路（curl 即可），若手动通过则属测试代码问题，不阻塞产品代码合入。详见 `docs/lessons/lessons-learned.md` §96。
