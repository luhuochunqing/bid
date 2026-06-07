# formengine 模块（动态表单自定义引擎）

一旦我所属的文件夹有所变化，请更新我。

## 职责

动态表单自定义引擎负责"表单定义注册、字段可见性规则、条件逻辑、跨字段验证、角色权限过滤、租户字段覆盖和提交审计"。本模块是运行时引擎，解析表单 schema 并根据用户角色、租户配置和业务规则动态生成表单界面（M1 基础设施 + M2 运行时 + M5 高级特性）。

## 边界

- `domain/` 是纯核心：表单字段定义、可见性规则、跨字段验证规则、解析结果、提交结果和验证结果。
- `application/` 是编排层：表单解析、可见性应用、条件评估、跨字段验证、角色权限过滤、租户覆盖和审计日志编排。
- `infrastructure/` 是副作用层：JPA 持久化（Entity + Repository）和 HTTP 接口适配。

## 文件清单

| 文件 | 功能 |
|------|------|
| `domain/` | CrossFieldValidationRule、FieldVisibility、FormFieldCondition、ResolvedField、ResolvedForm、SubmitResult、ValidationResult |
| `application/` | AdaptiveFormService、ConditionEvaluator、CrossFieldValidator、FormSchemaParser、RoleBasedFieldFilter、TenantOverrideService、UserRoleResolver、FormSubmissionAuditService、FormDefinitionAdminService |
| `infrastructure/` | FormDefinitionController（运行时 API）、FormDefinitionAdminController（管理 API）、Entity + Repository |

## 核心 API

| Method | Path | 用途 |
|--------|------|------|
| `GET` | `/api/form-definitions/{scope}/active` | 获取解析后的表单（含可见性规则） |
| `POST` | `/api/form-definitions/{scope}/validate` | 验证表单数据（含跨字段验证） |
| `POST` | `/api/form-definitions/{scope}/submit` | 提交表单并记录审计日志 |

## M5 高级特性

### M5.1 跨字段验证

支持操作符：`less_than` / `greater_than` / `equals` / `not_equals` / `sum_equals` / `one_filled` / `both_filled` / `not_after`。
规则存储于 `cross_field_validation_rule` 表。

### M5.2 角色权限过滤

`RoleBasedFieldFilter` 根据 `FormFieldVisibility.rolePattern` 和 `UserRoleResolver` 解析的用户角色，动态决定字段的 hidden / readonly 状态。
临时实现基于用户名模式推断角色，最终应从 JWT 声明或 UserService 获取。

### M5.3 租户字段覆盖

`TenantOverrideService` 支持租户管理员覆盖字段的 label / required / default_value / options / hidden / readonly。
覆盖存储于 `tenant_form_field_override` 表，按 definition_id + org_id 唯一索引。

### M5.5 提交审计

`FormSubmissionAuditService` 记录每次提交的 SUCCESS / VALIDATION_FAILED / PROCESSING_ERROR，包含 SHA-256 数据哈希和完整 JSON 快照。
使用 `REQUIRES_NEW` 传播级别，确保审计记录在主事务失败时仍可持久化。

## 数据表

- `form_definition_registry` — 表单定义注册（scope + orgId 多租户）
- `form_field_visibility` — 字段可见性规则（含 rolePattern 和 orgId 过滤）
- `form_field_condition` — 字段条件逻辑（show / hide / readonly）
- `cross_field_validation_rule` — 跨字段验证规则（M5.1）
- `tenant_form_field_override` — 租户字段覆盖（M5.3）
- `form_submission_audit` — 提交审计日志（M5.5）

## 前端和交付文档

- 配置页：`src/views/System/WorkflowFormDesigner.vue`（含验证规则 Tab、可见性规则 Tab、角色模拟预览）
- 运行态表单组件：`src/components/common/DynamicFormRenderer.vue`（含 readonly 样式）
- 前端 API：`src/api/modules/workflowForm.js`
- 设计文档：`docs/artifacts/dynamic-form-engine-v1-20260524.html`

## 测试

### 单元测试（`backend/src/test/java/com/xiyu/bid/formengine/`）

| 测试类 | 覆盖范围 |
|--------|-----------|
| `CrossFieldValidatorTest` | 8 种跨字段验证操作符（less_than / greater_than / equals / not_equals / sum_equals / one_filled / both_filled / not_after） |
| `RoleBasedFieldFilterTest` | 角色模式匹配（精确 / 通配符 / 模糊）、优先级、状态覆盖 |
| `ConditionEvaluatorTest` | 10 种条件操作符（eq / neq / in / not_in / gt / gte / lt / lte / contains / not_contains） |
| `AdaptiveFormServiceTest` | resolve / validate / submit 流程、租户覆盖、角色过滤 |

### 集成测试（`backend/src/test/java/com/xiyu/bid/formengine/integration/`）

| 测试类 | 覆盖范围 |
|--------|-----------|
| `FormDefinitionIntegrationTest` | 运行时 API（GET/POST/submit）、管理端 API（CRUD）、认证/授权 |

### E2E 测试（`e2e/form-engine-adaptive-flow.spec.js`）

| 模块 | 测试用例 |
|------|----------|
| M1 | 表单渲染降级兼容、字段类型、设计器 |
| M2 | Schema 加载、验证、提交 |
| M3 | Tender entry 集成 |
| M4 | Project 表单集成 |
| M5 | 跨字段验证、角色过滤、租户覆盖、审计日志 |
| M6 | Admin CRUD、权限控制、缓存失效 |

### 运行测试

```bash
# 单元测试
cd backend
mvn test -Dtest="CrossFieldValidatorTest,RoleBasedFieldFilterTest,ConditionEvaluatorTest,AdaptiveFormServiceTest"

# 集成测试（需要 MySQL 运行）
mvn test -Dtest="FormDefinitionIntegrationTest" -Dspring.profiles.active=test

# 架构测试（确保模块边界合规）
mvn test -Dtest="FPJavaArchitectureTest" -q

# E2E（需要后端和前端运行）
npx playwright test e2e/form-engine-adaptive-flow.spec.js
```

## 数据库迁移

| 版本 | 内容 | 回滚脚本 |
|------|------|----------|
| V140 | form_definition_registry、form_field_visibility、form_field_condition + 种子数据 | `U140__form_customization_schema.sql` |
| V141 | cross_field_validation_rule（8 种操作符） | `U141__cross_field_validation.sql` |
| V142 | tenant_form_field_override（label/required/default_value/options/hidden/readonly） | `U142__tenant_form_override.sql` |
| V143 | form_submission_audit（SUCCESS/VALIDATION_FAILED/PROCESSING_ERROR） | `U143__form_submission_audit.sql` |

回滚脚本位于 `backend/src/main/resources/db/rollback/migration-mysql/`。

## 开发注意事项

1. **Redis 缓存**：表单定义按 `form:def:{scope}:{orgId}` 缓存 5 分钟；发布时调用 `AdaptiveFormService.invalidateCache()` 清除缓存。
2. **纯核心约束**：`domain/` 和 `application/` 层不含框架依赖（JPA/Spring/DataRedis）；`infrastructure/` 层处理所有副作用。
3. **租户隔离**：查询时优先租户定义（`org_id` 非空），兜底全局定义（`org_id` 为空）。
4. **审计事务**：`FormSubmissionAuditService` 使用 `REQUIRES_NEW` 传播级别，确保主事务失败时审计仍可记录。
