# 动态表单引擎 UAT 测试用例

> 本文档描述西域数智化投标管理平台动态表单自定义引擎的 UAT（用户验收测试）用例。
> 覆盖范围：M1–M6 所有功能模块。

---

## 测试环境前置条件

| 项目 | 说明 |
|------|------|
| **数据库** | MySQL 8.0，已执行 V140–V143 迁移 |
| **测试账户** | `admin` / `XiyuAdmin2026!`（ADMIN 角色）<br>`staff` / `Test@123`（STAFF 角色） |
| **后端** | `http://127.0.0.1:18080` |
| **前端** | `http://127.0.0.1:1314` |
| **种子数据** | 4 个 scope：`tender.entry`、`project.basic`、`resource.expense`、`knowledge.case` |

---

## M1：动态表单渲染

### TC-M1-001：20种字段类型渲染

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M1-001 |
| **用例名称** | 20种字段类型在 DynamicFormRenderer 中正确渲染 |
| **前置条件** | 系统已登录（admin 账户），后端 API 正常运行 |
| **测试步骤** | 1. 进入投标管理 → 招标项目 → 新建<br>2. 打开 ManualTenderDialog<br>3. 验证所有字段类型是否正常显示 |
| **预期结果** | - 动态表单正常渲染，显示所有字段<br>- TEXT、SELECT、CURRENCY、ADDRESS、DATE、PHONE、TEXTAREA、NUMBER、PERCENT、EMAIL 等类型均可交互<br>- 降级兼容：若 API 不可用，原有硬编码表单正常显示 |
| **测试数据** | `scope=tender.entry` |
| **优先级** | P0 |

### TC-M1-002：WorkflowFormDesigner 字段类型选择

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M1-002 |
| **用例名称** | 表单设计器支持所有 20 种字段类型 |
| **前置条件** | admin 账户，进入表单设计器 |
| **测试步骤** | 1. 进入管理后台 → 表单定义<br>2. 选择一个 scope，点击"编辑"<br>3. 添加字段，选择每种字段类型 |
| **预期结果** | - 字段类型下拉框包含所有 20 种类型<br>- 选择后字段配置面板正确显示对应属性 |
| **测试数据** | `scope=tender.entry` |
| **优先级** | P1 |

### TC-M1-003：4个种子 scope 已加载

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M1-003 |
| **用例名称** | 4个业务域种子数据正确初始化 |
| **前置条件** | 数据库迁移已完成 |
| **测试步骤** | 1. 进入管理后台 → 表单定义列表<br>2. 确认以下 scope 存在 |
| **预期结果** | `tender.entry`（标讯手工录入）<br>`project.basic`（项目基本信息）<br>`resource.expense`（费用申请）<br>`knowledge.case`（案例建档） |
| **测试数据** | 4 条 seed 记录 |
| **优先级** | P0 |

---

## M2：表单 Schema 加载与验证

### TC-M2-001：表单 Schema 正确加载

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M2-001 |
| **用例名称** | `GET /api/form-definitions/{scope}/active` 返回正确 schema |
| **前置条件** | 后端运行，seed 数据存在 |
| **测试步骤** | 1. 调用 `GET /api/form-definitions/tender.entry/active`<br>2. 验证返回结构 |
| **预期结果** | ```json<br>{<br>  "success": true,<br>  "data": {<br>    "scope": "tender.entry",<br>    "scopeLabel": "标讯手工录入",<br>    "fields": [...],<br>    "version": 1<br>  }<br>}<br>``` |
| **测试数据** | `scope=tender.entry` |
| **优先级** | P0 |

### TC-M2-002：必填字段验证

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M2-002 |
| **用例名称** | 必填字段缺失时验证失败 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/validate`（不传 `deadline`）<br>2. 验证返回结果 |
| **预期结果** | `valid=false`，`errors` 包含必填提示 |
| **测试数据** | `formData={"title": "测试"}` |
| **优先级** | P0 |

### TC-M2-003：有效数据验证通过

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M2-003 |
| **用例名称** | 有效数据验证返回成功 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/validate`（传入所有必填字段）<br>2. 验证返回结果 |
| **预期结果** | `valid=true`，`errors=[]` |
| **测试数据** | `formData={"title": "测试标讯", "deadline": "2026-12-31"}` |
| **优先级** | P0 |

### TC-M2-004：表单提交成功

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M2-004 |
| **用例名称** | `POST /api/form-definitions/{scope}/submit` 成功提交 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/submit` |
| **预期结果** | `success=true`，审计日志已写入 |
| **测试数据** | `formData={"title": "测试标讯", "deadline": "2026-12-31"}` |
| **优先级** | P0 |

### TC-M2-005：表单提交验证失败

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M2-005 |
| **用例名称** | 验证失败时提交返回 400 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/submit`（缺少必填字段） |
| **预期结果** | HTTP 400，`success=false`，`message` 包含验证错误 |
| **测试数据** | `formData={"source": "bidding"}`（缺少 `deadline`） |
| **优先级** | P1 |

### TC-M2-006：未认证访问返回 401

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M2-006 |
| **用例名称** | 未认证用户无法访问运行时 API |
| **前置条件** | 无 session token |
| **测试步骤** | 1. 调用 `GET /api/form-definitions/tender.entry/active`（无 Authorization header） |
| **预期结果** | HTTP 401 |
| **优先级** | P0 |

---

## M3：Tender Entry 集成

### TC-M3-001：投标录入页面加载动态表单

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M3-001 |
| **用例名称** | 投标手工录入 Dialog 加载 `tender.entry` schema |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 进入投标管理 → 招标项目列表<br>2. 点击"新建"<br>3. 打开 ManualTenderDialog |
| **预期结果** | - Dialog 正常显示<br>- 动态表单字段（标题、来源、预算等）可见 |
| **测试数据** | `scope=tender.entry` |
| **优先级** | P0 |

### TC-M3-002：TenderEvaluationFormAdaptive 集成

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M3-002 |
| **用例名称** | 评标表单加载动态 schema |
| **前置条件** | admin 已登录，存在已录入的投标 |
| **测试步骤** | 1. 进入投标管理 → 招标项目列表<br>2. 选择一个投标进入详情<br>3. 进入评标阶段 |
| **预期结果** | 评标表单使用动态 schema 渲染 |
| **优先级** | P1 |

---

## M4：Project 表单集成

### TC-M4-001：项目基本信息表单加载动态 schema

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M4-001 |
| **用例名称** | 项目基本信息页加载 `project.basic` schema |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 进入项目列表<br>2. 新建项目 → 基本信息页 |
| **预期结果** | - 表单使用动态 schema 渲染<br>- 字段：项目名称、项目经理、团队成员、开始/结束日期、项目预算、行业、描述 |
| **测试数据** | `scope=project.basic` |
| **优先级** | P1 |

### TC-M4-002：DetailStep 动态表单

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M4-002 |
| **用例名称** | 项目详情步骤页加载动态 schema |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 新建项目 → 进入详情步骤 |
| **预期结果** | 详情步骤表单使用动态 schema |
| **优先级** | P1 |

### TC-M4-003：InitiationStage 动态表单

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M4-003 |
| **用例名称** | 项目立项阶段表单加载动态 schema |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 新建项目 → 进入立项阶段 |
| **预期结果** | 立项阶段表单使用动态 schema |
| **优先级** | P1 |

---

## M5：高级功能

### TC-M5-001：跨字段验证 less_than

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-001 |
| **用例名称** | `less_than` 验证：budget < estimated_cost |
| **前置条件** | 后端运行，`tender.entry` 定义已加载 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/validate`<br>2. `budget_amount=100`, `estimated_cost=200` |
| **预期结果** | `valid=true`（100 < 200） |
| **测试数据** | `formData={"title": "Test", "deadline": "2026-12-31", "budget_amount": 100, "estimated_cost": 200}` |
| **优先级** | P0 |

### TC-M5-002：跨字段验证 less_than 失败

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-002 |
| **用例名称** | `less_than` 验证失败：budget >= estimated_cost |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/validate`<br>2. `budget_amount=300`, `estimated_cost=200` |
| **预期结果** | `valid=false`，`errors` 包含跨字段验证错误 |
| **测试数据** | `formData={"title": "Test", "deadline": "2026-12-31", "budget_amount": 300, "estimated_cost": 200}` |
| **优先级** | P0 |

### TC-M5-003：跨字段验证 greater_than

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-003 |
| **用例名称** | `greater_than` 验证 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `fieldA=300`, `fieldB=200` |
| **预期结果** | `valid=true`（300 > 200） |
| **优先级** | P1 |

### TC-M5-004：跨字段验证 equals

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-004 |
| **用例名称** | `equals` 验证 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `status="draft"`, target=`"draft"` |
| **预期结果** | `valid=true` |
| **优先级** | P1 |

### TC-M5-005：跨字段验证 not_equals

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-005 |
| **用例名称** | `not_equals` 验证 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `status="draft"`, target=`"published"` |
| **预期结果** | `valid=true` |
| **优先级** | P1 |

### TC-M5-006：跨字段验证 sum_equals

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-006 |
| **用例名称** | `sum_equals` 验证 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `budget_amount=100`, `estimated_cost=100`, target=`200` |
| **预期结果** | `valid=true`（100 + 100 = 200） |
| **优先级** | P1 |

### TC-M5-007：跨字段验证 one_filled

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-007 |
| **用例名称** | `one_filled` 验证：至少填写一个 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `fieldA="有值"`, `fieldB=null` |
| **预期结果** | `valid=true` |
| **优先级** | P1 |

### TC-M5-008：跨字段验证 both_filled

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-008 |
| **用例名称** | `both_filled` 验证：必须同时填写 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `fieldA="有值"`, `fieldB="有值"` |
| **预期结果** | `valid=true` |
| **优先级** | P1 |

### TC-M5-009：跨字段验证 not_after

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-009 |
| **用例名称** | `not_after` 日期验证 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `start_date="2026-01-01"`, `end_date="2026-12-31"` |
| **预期结果** | `valid=true`（开始不晚于结束） |
| **优先级** | P1 |

### TC-M5-010：跨字段验证 not_after 失败

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-010 |
| **用例名称** | `not_after` 日期验证失败 |
| **前置条件** | 后端运行 |
| **测试步骤** | 1. `start_date="2026-12-31"`, `end_date="2026-01-01"` |
| **预期结果** | `valid=false`，错误信息包含"开始日期不能晚于结束日期" |
| **优先级** | P0 |

### TC-M5-011：角色过滤—admin 看到全部字段

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-011 |
| **用例名称** | admin 角色不应用字段隐藏规则 |
| **前置条件** | admin 已登录，visibility 规则存在 |
| **测试步骤** | 1. 调用 `GET /api/form-definitions/tender.entry/active`（admin 用户） |
| **预期结果** | 全部字段可见 |
| **优先级** | P1 |

### TC-M5-012：角色过滤—staff 字段隐藏

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-012 |
| **用例名称** | staff 角色特定字段被隐藏 |
| **前置条件** | staff 已登录，visibility 规则配置了 `staff` 隐藏 `budget` |
| **测试步骤** | 1. 调用 `GET /api/form-definitions/tender.entry/active`（staff 用户） |
| **预期结果** | `budget` 字段 `hidden=true` |
| **优先级** | P1 |

### TC-M5-013：租户覆盖全局字段 label

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-013 |
| **用例名称** | 租户可覆盖全局字段的 label |
| **前置条件** | 后端运行，租户覆盖配置存在 |
| **测试步骤** | 1. 调用 `GET /api/form-definitions/tender.entry/active?orgId=100` |
| **预期结果** | 被覆盖的字段 label 显示租户自定义值 |
| **优先级** | P2 |

### TC-M5-014：角色预览功能

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-014 |
| **用例名称** | 设计器支持角色预览 |
| **前置条件** | admin 已登录，进入表单设计器 |
| **测试步骤** | 1. 进入管理后台 → 表单定义<br>2. 选择一个 scope → 编辑<br>3. 切换到"角色预览" tab |
| **预期结果** | - 可选择不同角色预览<br>- 预览视图正确显示隐藏/只读字段 |
| **优先级** | P2 |

### TC-M5-015：提交审计日志记录

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M5-015 |
| **用例名称** | 每次表单提交生成审计日志 |
| **前置条件** | 后端运行，数据库正常 |
| **测试步骤** | 1. 调用 `POST /api/form-definitions/tender.entry/submit`<br>2. 查询 `form_submission_audit` 表 |
| **预期结果** | - 审计记录已创建<br>- `status=SUCCESS` 或 `VALIDATION_FAILED`<br>- `form_data_hash` 存在 |
| **优先级** | P0 |

---

## M6：管理端功能

### TC-M6-001：表单定义分页列表

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-001 |
| **用例名称** | `GET /api/admin/form-definitions` 返回分页列表 |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 调用 `GET /api/admin/form-definitions` |
| **预期结果** | - HTTP 200<br>- `data.content` 为数组<br>- `data.totalElements >= 4` |
| **优先级** | P0 |

### TC-M6-002：新建表单定义

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-002 |
| **用例名称** | `POST /api/admin/form-definitions` 创建新定义 |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 调用 `POST /api/admin/form-definitions`<br>2. 传入 `scope`、`scopeLabel`、`schemaJson`、`enabled` |
| **预期结果** | - HTTP 201<br>- 返回实体包含 `id`、`version=1` |
| **测试数据** | `scope=test.create`, `scopeLabel=测试创建` |
| **优先级** | P0 |

### TC-M6-003：重复 scope 创建失败

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-003 |
| **用例名称** | 重复 scope 返回 400 |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 调用 `POST /api/admin/form-definitions`（使用已存在的 `tender.entry`） |
| **预期结果** | HTTP 400，`success=false` |
| **优先级** | P1 |

### TC-M6-004：发布表单定义

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-004 |
| **用例名称** | `POST /api/admin/form-definitions/{id}/publish` 递增版本号 |
| **前置条件** | admin 已登录，存在未发布的定义 |
| **测试步骤** | 1. 创建测试定义<br>2. 调用 `POST /api/admin/form-definitions/{id}/publish` |
| **预期结果** | `version` 从 1 递增到 2 |
| **优先级** | P0 |

### TC-M6-005：删除表单定义

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-005 |
| **用例名称** | `DELETE /api/admin/form-definitions/{id}` 删除定义 |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 创建测试定义<br>2. 调用 `DELETE /api/admin/form-definitions/{id}` |
| **预期结果** | HTTP 200，再次访问 404 |
| **优先级** | P1 |

### TC-M6-006：保存可见性规则

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-006 |
| **用例名称** | `POST /api/admin/form-definitions/{id}/visibility` 保存字段规则 |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 创建测试定义<br>2. 调用保存可见性规则 API |
| **预期结果** | HTTP 200，规则已持久化 |
| **测试数据** | `[{"fieldKey":"title","rolePattern":"staff","hidden":true}]` |
| **优先级** | P1 |

### TC-M6-007：保存条件规则

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-007 |
| **用例名称** | `POST /api/admin/form-definitions/{id}/conditions` 保存条件规则 |
| **前置条件** | admin 已登录 |
| **测试步骤** | 1. 创建测试定义<br>2. 调用保存条件规则 API |
| **预期结果** | HTTP 200，规则已持久化 |
| **测试数据** | `[{"sourceField":"category","operator":"eq","targetValue":"other","action":"hide","targetField":"description"}]` |
| **优先级** | P1 |

### TC-M6-008：非 admin 用户无法访问管理端

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-008 |
| **用例名称** | STAFF 角色访问管理端返回 403 |
| **前置条件** | staff 已登录 |
| **测试步骤** | 1. staff 用户调用 `GET /api/admin/form-definitions` |
| **预期结果** | HTTP 403 |
| **优先级** | P0 |

### TC-M6-009：Redis 缓存生效

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-009 |
| **用例名称** | 相同 scope 第二次请求使用缓存 |
| **前置条件** | Redis 运行，后端缓存开启 |
| **测试步骤** | 1. 调用 `GET /api/form-definitions/tender.entry/active` 两次<br>2. 观察日志 |
| **预期结果** | 第二次请求日志显示 `Cache hit` |
| **优先级** | P2 |

### TC-M6-010：发布时缓存失效

| 项目 | 内容 |
|------|------|
| **用例ID** | TC-M6-010 |
| **用例名称** | 发布后缓存被清除 |
| **前置条件** | Redis 运行 |
| **测试步骤** | 1. 请求一次表单定义（触发缓存）<br>2. 发布该定义<br>3. 再次请求 |
| **预期结果** | 第三次请求不是从缓存返回（缓存已失效） |
| **优先级** | P2 |

---

## 测试结果汇总表

| 用例ID | 模块 | 优先级 | 测试结果 | 备注 |
|--------|------|--------|----------|------|
| TC-M1-001 | M1 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M1-002 | M1 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M1-003 | M1 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M2-001 | M2 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M2-002 | M2 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M2-003 | M2 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M2-004 | M2 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M2-005 | M2 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M2-006 | M2 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M3-001 | M3 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M3-002 | M3 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M4-001 | M4 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M4-002 | M4 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M4-003 | M4 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M5-001 | M5 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M5-002 | M5 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M5-003~010 | M5 | P1 | ☐ 通过 ☐ 失败 | 8 个跨字段验证用例 |
| TC-M5-011~014 | M5 | P1-2 | ☐ 通过 ☐ 失败 | 角色过滤、租户覆盖、预览 |
| TC-M5-015 | M5 | P0 | ☐ 通过 ☐ 失败 | 审计日志 |
| TC-M6-001 | M6 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M6-002 | M6 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M6-003 | M6 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M6-004 | M6 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M6-005 | M6 | P1 | ☐ 通过 ☐ 失败 | |
| TC-M6-006~007 | M6 | P1 | ☐ 通过 ☐ 失败 | 规则保存 |
| TC-M6-008 | M6 | P0 | ☐ 通过 ☐ 失败 | |
| TC-M6-009~010 | M6 | P2 | ☐ 通过 ☐ 失败 | 缓存 |

---

## 执行记录

| 日期 | 测试人员 | 环境 | 通过率 | 备注 |
|------|----------|------|--------|------|
| 2026-05-24 | Agent (M6) | dev | 待执行 | 自动化测试执行 |
