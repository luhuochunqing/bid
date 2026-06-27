---
title: 动态表单自定义引擎
space: engineering
category: feature
tags: [表单, 动态表单, 自定义, schema, 字段可见性, 跨字段验证, 多租户]
sources:
  - backend/src/main/java/com/xiyu/bid/formengine/
  - backend/src/main/resources/db/migration-mysql/V140__dynamic_form_engine.sql
  - backend/src/main/resources/db/migration-mysql/V141__cross_field_validation.sql
  - backend/src/main/resources/db/migration-mysql/V142__tenant_form_override.sql
  - backend/src/main/resources/db/migration-mysql/V143__form_submission_audit.sql
  - docs/artifacts/dynamic-form-engine-v1-20260524.html
  - docs/testing/form-engine-uat.md
backlinks:
  - _index
created: 2026-05-24
updated: 2026-06-21
health_checked: 2026-06-27
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
