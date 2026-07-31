# API Contracts: 项目三表单自定义字段

**Date**: 2026-07-31 | 均为既有端点的字段扩展，无新增端点、无权限注解变更。

## 1. 创建项目（承载 project.basic + project.detail 自定义字段）

`POST /api/projects`

**请求新增字段**：

```json
{
  "name": "示例项目",
  "...": "既有 25 字段不变",
  "customFields": {
    "project.basic": { "budgetLevel": "重点客户" },
    "project.detail": { "siteVisitDone": true }
  }
}
```

- `customFields` 可选；缺省/null 时存 NULL
- 一级键必须是合法 scope（`project.basic` / `project.detail`）；未知 scope 键由 Service 过滤丢弃并 `log.warn`（防脏数据，不阻断主流程）
- 值类型不限（string/number/boolean/array），Jackson 原生绑定 `Map<String, Object>`

**响应**：创建成功的项目对象，含 `customFields`（原样返回已存值）。

## 2. 立项保存 / 提交（承载 project.initiation 自定义字段）

`POST /api/projects/{projectId}/initiation`

**请求新增字段**：

```json
{
  "...": "既有 33 字段不变",
  "customFields": {
    "project.initiation": { "internalReviewNote": "需法务会签" }
  }
}
```

- 保存草稿与正式提交同一端点（沿用现状），customFields 两种场景均持久化
- 更新语义：按 scope 键整体替换该 scope 分组（前端每次提交全量收集当前 schema 自定义字段），不触碰其他 scope 键

**响应**：保存后的立项视图，含 `customFields`。

## 3. 项目详情（回显）

`GET /api/projects/{id}`

**响应新增字段**：`customFields: { "project.basic": {...}, "project.detail": {...} }`，列 NULL 时返回空对象 `{}` 或省略（实现期与前端约定，推荐 `{}` 减少判空）。

## 4. 立项详情（回显）

`GET /api/projects/{projectId}/initiation`

**响应新增字段**：`customFields: { "project.initiation": {...} }`，同上约定。

## 5. 表单定义保存 / 发布（key 冲突校验挂载点）

- `PUT /api/admin/form-definitions/{id}`（保存草稿）
- `POST /api/admin/form-definitions/{id}/publish`（发布）

**新增服务端校验**（仅当 scope ∈ project.basic / project.initiation / project.detail）：

| 校验 | 失败响应 |
|---|---|
| 自定义字段 key 命中预置清单 | `400` + ApiResponse.msg 指出冲突 key |
| 自定义字段 key 重复 | `400` + ApiResponse.msg 指出重复 key |

预置字段的 key/type 保护以前端 UI 机制为主（无入口即无请求），后端不强制校验 schema  diff（避免老 schema 重保存被误杀——实现期如评估可行可作为增强，非本特性必需）。

## 6. 前端契约（composable 函数签名）

```js
// src/composables/useCustomFields.js
collectCustomFields(model, schemaFields, presetKeys, scope) → { [scope]: {key: value} }
mergeCustomFieldsIntoModel(model, customFields, scope) → void（原地摊平 customFields[scope] 进 model）
```

- `schemaFields`：AdaptiveFormPage 加载的 schema 字段数组
- `presetKeys`：当前 scope 预置清单（从 workflowFormDesignerCore 导出，单一来源）
- 自定义字段 key 集 = schemaFields.map(f=>f.key) − presetKeys − extension 特殊字段（如 tender.entry 的 pastedText/attachments 类业务扩展位，实现期确认三 scope 是否有此类字段）
