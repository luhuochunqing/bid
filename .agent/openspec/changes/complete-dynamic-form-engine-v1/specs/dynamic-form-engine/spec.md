## ADDED Requirements

### Requirement: Frontend Condition Evaluator (REQ-DFE-001)
前端 SHALL 提供 `useFormConditions` composable，接收条件规则数组和表单数据，返回每个字段的 hidden/readonly 状态。该 composable 实现 10 种操作符（eq/neq/in/notIn/contains/gt/lt/gte/lte/exists），且为纯函数可单元测试。

#### Scenario: Field hidden by condition
- **WHEN** a field has condition `{sourceField: "type", operator: "eq", targetValue: "A", action: "hide", targetField: "detail"}`
- **AND** user sets `type` to value `"A"`
- **THEN** the `detail` field is hidden in the form renderer

#### Scenario: Field shown by condition
- **WHEN** a field has condition `{sourceField: "category", operator: "neq", targetValue: "NONE", action: "show", targetField: "subcategory"}`
- **AND** user sets `category` to value `"OFFICIAL"`
- **THEN** the `subcategory` field becomes visible

#### Scenario: Field required by condition
- **WHEN** a field has condition `{sourceField: "hasContract", operator: "eq", targetValue: "true", action: "require", targetField: "contractNo"}`
- **AND** user sets `hasContract` to `"true"`
- **THEN** the `contractNo` field becomes required

### Requirement: Frontend Field Visibility (REQ-DFE-002)
前端 SHALL 提供 `useFieldVisibility` composable，接收可见性规则数组（含 rolePattern 和 orgId）和当前用户身份，返回每个字段的 hidden/readonly/readonlyText 状态。rolePattern 为空或 `*` 表示所有角色可见。

#### Scenario: Admin-only field hidden from staff
- **WHEN** a field has visibility rule `{rolePattern: "ADMIN", hidden: false, readonly: false}`
- **AND** the current user has role "STAFF"
- **THEN** the field is hidden from the form renderer

#### Scenario: Manager sees readonly field
- **WHEN** a field has visibility rule `{rolePattern: "STAFF", hidden: false, readonly: true}`
- **AND** the current user has role "STAFF"
- **THEN** the field is rendered in readonly text mode

### Requirement: AdaptiveFormPage Automatic Submission (REQ-DFE-003)
`AdaptiveFormPage.vue` SHALL 在用户触发 submit 事件时自动调用 `POST /api/form-definitions/{scope}/submit`，传入 resolved schema 和用户填写的 formData，并将后端返回结果通过事件向上传递。

#### Scenario: Successful form submission
- **WHEN** user fills all required fields and clicks submit
- **AND** the page has `scope="tender.entry"`
- **THEN** the component calls `POST /api/form-definitions/tender.entry/submit`
- **AND** emits `submit-success` event with the response data

#### Scenario: Submission with validation error
- **WHEN** user submits with invalid data
- **AND** backend returns validation errors
- **THEN** the component emits `submit-error` event with the error messages
- **AND** displays the error messages near the relevant fields

### Requirement: Admin Publish Invalidation (REQ-DFE-004)
Admin 在表单设计器中点击"发布"时，后端 SHALL 调用 `AdaptiveFormService.invalidateCache()` 使 Redis 中该 scope 的缓存立即失效，确保其他用户下次打开时获取最新 schema。

#### Scenario: Publish invalidates cache
- **WHEN** admin calls `POST /api/admin/form-definitions/{id}/publish`
- **THEN** the Redis key `form:def:{scope}:{orgId}` is deleted
- **AND** subsequent `GET /api/form-definitions/{scope}/active` returns the newly published schema

### Requirement: Full Scope Routing (REQ-DFE-005)
`FormSubmissionRouter` SHALL 支持全部 8 个业务 scope 的路由：`tender.entry`、`tender.evaluation`、`project.basic`、`project.evaluation`、`resource.expense`、`resource.ca`、`resource.contract`、`knowledge.case`、`knowledge.qual`。不支持的 scope 返回结构化错误，不静默失败。

#### Scenario: Expense submission routes to handler
- **WHEN** user submits a form with `scope="resource.expense"`
- **THEN** the `ExpenseFormSubmissionHandler` processes the submission
- **AND** returns a success result

#### Scenario: Unsupported scope returns error
- **WHEN** user submits a form with an unsupported `scope="unknown.scope"`
- **THEN** the system returns `Result.failure(new UnsupportedScopeException("unknown.scope"))`
- **AND** does NOT silently swallow the error

### Requirement: Tenant Override Logic (REQ-DFE-006)
`AdaptiveFormService.resolve()` SHALL 优先返回当前租户（orgId）专属的 schema，orgId 为 null 时降级到全局模板。`submit()` 方法从 JWT 解析当前用户 orgId 并传入路由逻辑。

#### Scenario: Tenant schema takes precedence
- **WHEN** there exists both a global definition (orgId=null) and a tenant definition (orgId=100) for scope "tender.entry"
- **AND** current user's orgId is 100
- **THEN** `resolve()` returns the tenant definition (orgId=100)

#### Scenario: Global fallback when no tenant definition
- **WHEN** there exists only a global definition (orgId=null) for scope "tender.entry"
- **AND** current user's orgId is 200
- **THEN** `resolve()` returns the global definition

### Requirement: Validation Error Message Fidelity (REQ-DFE-007)
后端 `FormFieldValidator` SHALL 使用字段定义的 `validation.errorMessage` 作为校验失败消息，无 errorMessage 时才使用硬编码格式。前端 `DynamicFormRenderer` SHALL 渲染 `minLength` / `maxLength` / `min` / `max` 校验并显示对应 errorMessage。

#### Scenario: Custom error message displayed
- **WHEN** a field has `validation: {minLength: 5, errorMessage: "标题长度应在5-200字之间"}`
- **AND** user submits with fewer than 5 characters
- **THEN** the error message displayed is "标题长度应在5-200字之间"

#### Scenario: Fallback to hardcoded format
- **WHEN** a field has `validation: {minLength: 5}` with no errorMessage
- **AND** user submits with fewer than 5 characters
- **THEN** the error message displayed is "[fieldLabel] 最小长度为 5"

### Requirement: End-to-End Form Designer Flow (REQ-DFE-008)
系统 SHALL 提供完整的端到端表单设计流程：Admin 在设计器中修改字段定义→发布→用户端实时读取最新 schema→填写并提交。E2E 测试覆盖这一完整链路。

#### Scenario: Admin modifies field, user sees changes
- **WHEN** admin changes field label from "标题" to "项目标题" in the designer
- **AND** admin publishes the form definition
- **THEN** a regular user opening the form sees "项目标题" as the field label
- **AND** the change is reflected without any code deployment

## MODIFIED Requirements

### Requirement: ConditionEvaluator (Existing — V140)
The system SHALL retain and continue providing the `ConditionEvaluator` that evaluates 10 operator types against form data. This requirement is retained and extended by REQ-DFE-001, which wraps it in a Vue composable for frontend use.

#### Scenario: All 10 operators evaluate correctly
- **WHEN** a condition with any of the 10 operators (eq/neq/in/notIn/contains/gt/lt/gte/lte/exists) is evaluated against matching form data
- **THEN** the evaluator returns true when the condition is satisfied
- **AND** returns false when the condition is not satisfied
