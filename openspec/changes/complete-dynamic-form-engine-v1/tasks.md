## 1. 前端 Composables

- [ ] 1.1 实现 `src/composables/useFormConditions.ts` — 接收 `conditions: FormCondition[]` + `formData: Ref<Record<string, unknown>>`，求值 10 种操作符，返回 `computed<Record<string, FieldState>>`（含 `hidden` / `readonly` 属性）
- [ ] 1.2 实现 `src/composables/useFieldVisibility.ts` — 接收 `visibilities: FormFieldVisibility[]` + 当前用户 `role`/`orgId`，返回 `computed<Record<string, FieldState>>`
- [ ] 1.3 `AdaptiveFormPage.vue` 集成 `useFormConditions` + `useFieldVisibility`，将条件/权限状态透传给 `DynamicFormRenderer`
- [ ] 1.4 `AdaptiveFormPage.vue` 的 `handleSubmit()` 调用 `POST /api/form-definitions/{scope}/submit`，传入 resolved schema + formData

## 2. 后端 Scope 路由补全

- [ ] 2.1 `FormSubmissionRouter` 新增 `resource.expense` → `ExpenseFormSubmissionHandler`
- [ ] 2.2 `FormSubmissionRouter` 新增 `resource.ca` → `CaCertificateFormSubmissionHandler`
- [ ] 2.3 `FormSubmissionRouter` 新增 `resource.contract` → `ContractFormSubmissionHandler`
- [ ] 2.4 `FormSubmissionRouter` 新增 `knowledge.case` → `CaseFormSubmissionHandler`
- [ ] 2.5 `FormSubmissionRouter` 新增 `knowledge.qual` → `QualificationFormSubmissionHandler`
- [ ] 2.6 `FormSubmissionRouter.default` 分支改为返回 `Result.failure(new UnsupportedScopeException(scope))`，而不是静默失败

## 3. Admin 缓存失效

- [ ] 3.1 `AdminController` 的 `publish()` 方法调用 `adaptiveFormService.invalidateCache(definitionId)`
- [ ] 3.2 补充单元测试：`AdminControllerPublishTest` 验证 publish 后 Redis key 被删除

## 4. 租户 orgId 修复

- [ ] 4.1 `AdaptiveFormService.submit()` 从 `SecurityContext` 或 JWT 解析当前用户 `orgId`
- [ ] 4.2 `AdaptiveFormService.resolve()` 优先返回 `orgId` 非空的租户级定义，`orgId` 为 null 时降级到全局定义

## 5. 前后端验证规则对齐

- [ ] 5.1 后端 `FormFieldValidator` 在校验失败时使用字段的 `errorMessage`，无 errorMessage 时才 fallback 到硬编码格式
- [ ] 5.2 前端 `DynamicFormRenderer.vue` 的 `validate()` 函数补充 `minLength` / `maxLength` / `min` / `max` 校验
- [ ] 5.3 前端 `DynamicFormRenderer.vue` 渲染校验错误时显示字段的 `validation.errorMessage`，不显示硬编码格式

## 6. E2E 测试

- [ ] 6.1 E2E：`Admin` 登录 → 打开表单设计器 → 修改字段 label → 发布
- [ ] 6.2 E2E：新窗口/隐身模式以普通用户登录 → 打开对应表单 → 验证字段 label 已更新
- [ ] 6.3 E2E：条件逻辑测试 — 选择 A 字段值触发 B 字段显示/隐藏
- [ ] 6.4 E2E：scope 路由测试 — 提交 `resource.expense` 表单，验证正确路由到 expense handler

## 7. 架构验证

- [ ] 7.1 `mvn test -Dtest=ArchitectureTest` 全绿
- [ ] 7.2 `npm run build` 成功
- [ ] 7.3 `openspec validate complete-dynamic-form-engine-v1 --strict` 通过
