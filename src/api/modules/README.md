一旦我所属的文件夹有所变化，请更新我。

# API 模块目录

这里按业务域拆分前端 API module，是页面、组件、store 获取业务数据的唯一入口。
所有模块均只被允许向真实的后端微服务发起 HTTP 通信，不允许隐式绕行任何本地静态源兜底。

| 文件 | 地位 | 功能 |
|------|------|------|
| `ai.js` | API 模块 | AI 评分、竞争、ROI、合规等智能分析调用 |
| `approval.js` | API 模块 | 审批流和审批记录相关调用 |
| `audit.js` | API 模块 | 审计日志与个人操作日志调用，沿用 `/api/audit` 兼容路径并新增 `/api/audit/my` |
| `auth.js` | API 模块 | 登录、登出、当前用户和鉴权相关调用 |
| `bidAgent.js` | API 模块 | 项目标书写作 Agent 运行、状态、写入和审查调用 |
| `bidMatchScoring.js` | API 模块 | 投标匹配评分模型、模型激活和标讯评分结果调用 |
| `collaboration.js` | API 模块 | 协作线程、评论、版本、文档协同调用 |
| `customerOpportunity.js` | API 模块 | 客户机会中心的真实接口、响应规范和转项目闭环 |
| `dashboard.js` | API 模块 | Dashboard 总览、统计、任务和日历调用 |
| `export.js` | API 模块 | 导出任务、导出状态和格式枚举 |
| `fees.js` | API 模块 | 费用申请、审批、退还等调用 |
| `knowledge.js` | API 模块 | 资质、案例、模板等知识资产调用 |
| `permissionMatrix.js` | API 模块 | 管理员只读接口权限矩阵调用和响应标准化 |
| `qualification.js` | API 模块 | 资质 CRUD 与借阅记录/借阅申请接线，供知识页和 store 复用 |
| `projectGroups.js` | API 模块 | 项目组正式领域模型的管理、删除与项目绑定配置 |
| `projectTenderBreakdown.js` | API 模块 | 项目级招标文件解析、最新解析快照复用、已上传文件复用、解析配置 readiness 和上传解析调用 |
| `projects.js` | API 模块 | 项目列表、详情、任务拆解、评分、结果录入调用 |
| `resources.js` | API 模块 | 平台账号、BAR、证书、资源能力调用 |
| `settings.js` | API 模块 | 系统设置页的数据权限与组织树读写 |
| `systemIntegration.js` | API 模块 | 系统集成页真实接口，包含企业微信与组织架构运维操作 |
| `taskStatusDict.js` | API 模块 | 项目任务状态字典查询（启用状态列表），供动态任务看板驱动列配置 |
| `taskActivity.js` | API 模块 | 任务评论与历史动态真实 API，供 TaskForm 动态 Tab 读取和发表评论 |
| `taskExtendedField.js` | API 模块 | 项目任务扩展字段 schema 读取（启用字段列表），供 TaskForm 动态渲染 |
| `taskExtendedFieldAdmin.js` | API 模块 | 管理员对任务扩展字段 schema 的 CRUD、启停与排序调用 |
| `tenders.js` | API 模块 | 标讯列表、详情、入项、上传任务和人工录入文档识别调用 |
| `users.js` | API 模块 | 用户搜索与任务负责人候选人查询，候选人来自后端组织归属/数据权限过滤；搜索和候选人返回值统一标准化为 UserPicker 可消费字段 |
| `workflowForm.js` | API 模块 | 流程表单运行态模板读取、附件上传、实例提交，以及管理员模板配置、发布、OA 绑定和试提交接口 |

`auth.js` 返回的用户快照会保留会话级权限字段，例如 `allowedProjectIds` 和 `allowedDepts`，供 store 和路由恢复使用。

- 2026-04-19: 新增 `qualification.js`，把资质 CRUD 从 `knowledge.js` 拆出，并为借阅接口未接入场景提供统一的前端未接入态响应。
- 2026-04-19: `knowledge.js` 的案例列表改为携带查询参数请求真实接口，并在模块内统一做分页/筛选整形，供案例页和详情页复用。
- 2026-04-22: 新增 `bidAgent.js`，通过真实项目 API 接入标书写作 Agent 的 run/status/apply/review 生命周期。
- 2026-04-24: 新增 `bidMatchScoring.js`，接入自定义投标匹配评分模型和标讯评分结果真实 API。
- 2026-04-27: `projects.js` 新增项目任务拆解接口，供项目详情页按真实 API 生成任务看板。
- 2026-04-27: 新增 `projectTenderBreakdown.js`，项目详情页可独立解析招标文件，解析结果供任务拆解和 AI 生成初稿复用。
- 2026-05-01: `projectTenderBreakdown.js` 新增最新解析快照查询和已上传文件复用接口，项目详情页优先复用已解析招标文件，再复用项目文档中的真实上传文件，缺少可用来源时才进入上传解析。
- 2026-04-27: 新增 `permissionMatrix.js`，接入管理员只读接口入口权限矩阵，帮助核对菜单权限与后端接口入口层授权。
- 2026-04-27: `tenders.js` 新增人工录入标讯附件的 `/api/doc-insight/parse` 真实 API 调用，使用 `TENDER_INTAKE` profile。
- 2026-04-29: 新增 `workflowForm.js`，资质借阅申请改为通过流程表单中心提交并触发 OA；扩展管理员流程表单配置、发布、OA 绑定、试提交和附件上传 API。
- 2026-05-01: 新增 `taskStatusDict.js`，接入启用状态的任务状态字典查询 API，供动态任务看板按后端字典驱动列配置。
- 2026-05-03: 新增 `taskExtendedField.js` 与 `taskExtendedFieldAdmin.js`，接入任务扩展字段 schema 的公开读取与管理员 CRUD/启停/排序 API，供 TaskForm 动态渲染扩展字段。
- 2026-05-03: `users.js` 接入 `/api/tasks/assignment-candidates`，任务负责人改为从组织归属候选人中选择，不再手写姓名。
- 2026-05-04: 新增 `taskActivity.js`，接入 `/api/tasks/{id}/activity` 与 `/api/tasks/{id}/comments`，任务动态仅走真实后端 API。
- 2026-05-15: 新增 `systemIntegration.js` 组织架构运维接口，系统集成页可通过真实后端查看状态、触发窗口对账和单人/单部门重同步。
