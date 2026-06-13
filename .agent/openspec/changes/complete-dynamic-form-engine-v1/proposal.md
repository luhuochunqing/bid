# Change: complete-dynamic-form-engine-v1

## Why

西域数智化投标管理平台现有表单均为硬编码，每次调整字段都要改代码——效率低、风险高、无法产品化。agent/gemini-init 分支在 V140-V143 迁移中完成了后端基础设施（数据库表、AdaptiveFormService、ConditionEvaluator、CrossFieldValidator、FormSubmissionRouter、24种字段类型渲染、Redis缓存），但核心的前端条件逻辑、后端 scope 路由完整性、publish 缓存失效等关键路径尚未打通，导致整个动态表单引擎处于"可渲染但不可用"状态。

## What Changes

### A. 已完成部分（保留，无需重建）

- `form_definition_registry` / `form_field_visibility` / `form_field_condition` 三张表（V140）
- `AdaptiveFormService` 含 `resolve()` / `validate()` / `submit()` / `invalidateCache()`（V140-V143）
- `ConditionEvaluator` — 纯函数，10 种操作符（eq/neq/in/notIn/contains/gt/lt/gte/lte/exists）
- `CrossFieldValidator` — 8 种跨字段验证规则
- `FormSubmissionRouter` — 部分实现（tender.entry / tender.evaluation / project.basic / project.evaluation）
- `DynamicFormRenderer.vue` — 24 种字段类型渲染
- `AdaptiveFormPage.vue` — 包装组件，含 fallback
- `WorkflowFormDesigner.vue` — 字段配置 / 可见性规则 / 验证规则 Tab 切换
- Redis 缓存（5min TTL，key: `form:def:{scope}:{orgId}`）
- Seed 数据 4 个 scope

### B. 新增部分（本次 change 要完成）

- **前端 `useFormConditions` composable** — 订阅 formData，求值条件表达式，注入 hidden/readonly 状态
- **前端 `useFieldVisibility` composable** — 基于 rolePattern + orgId 过滤字段，注入 hidden/readonly/readonly-text 状态
- **AdminController.publish() 缓存失效** — `/api/admin/form-definitions/{id}/publish` 调用 `AdaptiveFormService.invalidateCache()`
- **AdaptiveFormPage 自动调用后端 submit API** — `handleSubmit()` 调用 `POST /api/form-definitions/{scope}/submit`
- **FormSubmissionRouter 8 scope 全覆盖** — 补全 `resource.expense` / `resource.ca` / `resource.contract` / `knowledge.case` / `knowledge.qual` handler，修复 `default` 分支为友好错误
- **AdaptiveFormService.submit() orgId 修复** — 从 JWT 解析当前用户 orgId，触发租户覆盖逻辑
- **前后端验证规则对齐** — 后端 `FormFieldValidator` 支持 `errorMessage` 字段；前端 `DynamicFormRenderer` 支持 `minLength` / `maxLength` / `min` / `max` 校验
- **E2E 覆盖** — 补全 Admin 设计器修改→发布→用户端实时验证的端到端测试

## Impact

- **新增前端**：`src/composables/useFormConditions.ts`、`src/composables/useFieldVisibility.ts`
- **修改前端**：`AdaptiveFormPage.vue`（集成 composable + 调用 submit API）、`DynamicFormRenderer.vue`（增强验证规则展示）
- **新增后端**：`FormSubmissionRouter` 新增 5 个 scope handler（resource.* / knowledge.*）
- **修改后端**：`AdminController`（publish 调用 invalidateCache）、`AdaptiveFormService.submit()`（JWT 解析 orgId）、`FormFieldValidator`（支持 errorMessage）
- **测试**：新增 E2E 场景（Admin 设计器→发布→用户端读取→提交）

## Related

- PRD 文档：`docs/artifacts/dynamic-form-engine-v1-20260524.html`
- 根因分析报告：本文档的上一层 context
- 已完成 PR（agent/gemini-init）：V140-V143 迁移 + 后端基础设施
