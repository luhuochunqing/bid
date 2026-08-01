# Data Model: 项目三表单自定义字段扩展

**Date**: 2026-07-31 | **Branch**: `agent/claude/co601-project-form-custom-fields`

## 1. 存储变更（Flyway 迁移）

### projects 表（既有，64 字段）

| 新增列 | 类型 | NULL | 说明 |
|---|---|---|---|
| `custom_fields` | JSON | YES | 自定义字段值，scope 命名空间两级结构 |

承载 scope：`project.basic` + `project.detail`（创建向导一次提交）。

### project_initiation_details 表（既有，含 customer_info_json 先例）

| 新增列 | 类型 | NULL | 说明 |
|---|---|---|---|
| `custom_fields` | JSON | YES | 自定义字段值，scope 命名空间结构 |

承载 scope：`project.initiation`。

### JSON 值结构（两表统一）

```json
{
  "project.basic": { "budgetLevel": "重点客户", "visitDate": "2026-08-01" },
  "project.detail": { "siteVisitDone": true }
}
```

- 一级键 = 表单 scope（`project.basic` / `project.detail` / `project.initiation`）
- 二级键 = 自定义字段 key（管理员在设计器中定义）
- 值 = 用户填写值（string / number / boolean / array，由字段类型决定）
- 历史项目该列为 NULL，读取侧按空 Map 处理（FR-010）
- 被删除的自定义字段键值保留不清理（FR-011）

### 实体映射（沿用 customerInfoJson 模式）

```java
// Project.java / ProjectInitiationDetails.java 各加：
@Column(name = "custom_fields", columnDefinition = "JSON")
private String customFields;   // DB JSON 列 ↔ Java String，序列化由 Service/Mapper 编排
```

## 2. 传输模型（DTO）

| DTO | 新增字段 | 方向 |
|---|---|---|
| `ProjectRequest` | `Map<String, Object> customFields` | 创建项目入参（basic+detail 两 scope 分组） |
| `InitiationDto` | `Map<String, Object> customFields` | 立项保存/提交入参 |
| `ProjectDTO`（详情返回） | `Map<String, Object> customFields` | 项目详情/回显出参 |
| `InitiationViewDto` | `Map<String, Object> customFields` | 立项详情出参 |

序列化规则：入参 Map → `objectMapper.writeValueAsString` → String 列；出参 String 列 → `readValue` → Map；解析失败降级空 Map + `log.warn`（Constitution VII）。

> ⚠️ 实现期核对：`ProjectDTO` 存在两个（`project/dto/ProjectDTO.java` 与 `dto/ProjectDTO.java`），需确认项目创建返回与详情返回各自使用哪一个，两个都加还是只加一个，tasks.md 单列核对任务。

## 3. 配置模型（表单引擎，既有结构不变）

`form_definition_registry.schema_json` 字段清单 = 系统预置字段 + 自定义字段，不加 DB 列。区分方式：

- **预置字段**：key 命中前端 `PROJECT_LOCKED_FIELD_KEYS[scope]` 清单 + 后端 `CustomFieldsSchemaPolicy` 内嵌同一清单（单一来源：后端清单由迁移/常量定义，前端清单从设计器 core 导出，两边在 tasks 中要求一致性注释互指）
- **自定义字段**：不在清单内的字段

### 校验规则（FR-006，保存/发布时执行）

| 规则 | 触发点 | 结果 |
|---|---|---|
| 自定义字段 key 不得与预置清单重复 | 前端设计器保存前 + 后端 `FormDefinitionAdminService` 保存/发布兜底 | 拒绝 + 明确提示 |
| 自定义字段 key 之间不得重复 | 同上 | 拒绝 + 明确提示 |
| 预置字段 key/type 不得被修改、不得删除 | 前端设计器字段行禁用（机制拦截，无入口） | UI 层 100% 拦截（SC-003） |

## 4. 状态流转

本特性不新增业务状态。表单定义沿用既有 草稿→发布 流转（`FormDefinitionAdminService.publish`）；自定义字段值随项目创建/立项的既有状态机流转，无独立生命周期。

## 5. 关键关系图

```text
表单设计器 (WorkflowFormDesigner)
   │ 保存/发布 schema（含预置+自定义字段）
   ▼
form_definition_registry.schema_json  ──校验──►  CustomFieldsSchemaPolicy（纯函数）
   │ AdaptiveFormPage 拉取 schema 渲染
   ▼
业务页 model（DynamicFormRenderer: localValue[field.key]）
   │ 提交：useCustomFields.collect → customFields[scope]
   ▼
POST /api/projects  |  POST /api/projects/{id}/initiation
   │ Service 序列化
   ▼
projects.custom_fields  |  project_initiation_details.custom_fields
   │ 详情/回显：useCustomFields.merge → model 顶层摊平
   ▼
GET /api/projects/{id}  |  GET /api/projects/{id}/initiation
```
