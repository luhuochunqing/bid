# workflowform 模块（流程表单中心）

一旦我所属的文件夹有所变化，请更新我。

## 职责

流程表单中心负责“表单模板配置、草稿发布、版本快照、表单附件上传、表单实例提交、触发 OA、接收 OA 结果、审批通过后应用业务动作”。本模块不实现本地审批流，审批事实源来自 OA；本系统只保存表单快照、附件元数据、OA 关联号、审批结果和业务应用状态。

## 边界

- `domain/` 是纯核心：表单 schema 校验、OA 字段映射校验、OA payload 构造、提交值校验、附件值契约、状态流转策略、OA 结果是否允许应用业务。
- `application/` 是编排层：保存草稿、发布模板、绑定 OA、上传附件、提交表单、启动 OA、处理 OA 回调、调用业务应用端口。
- `infrastructure/` 是副作用层：JPA 持久化、模板版本投影、附件存储适配、OA Gateway 占位实现、资质借阅适配、项目权限守卫。
- `controller/` 和 `dto/` 只负责 HTTP 边界转换，不承载业务规则。

## 文件清单

| 文件 | 功能 |
|------|------|
| `domain/` | 表单 schema、附件值、提交值、状态流转和 OA 结果应用策略 |
| `application/` | 模板配置、发布、OA 绑定、附件上传、提交表单、触发 OA、处理回调、应用业务动作的用例编排 |
| `controller/` | 流程表单运行态接口、管理员配置接口和泛微 OA 回调入口 |
| `dto/` | HTTP 请求和响应对象 |
| `infrastructure/` | JPA 持久化、附件存储、OA Gateway、项目权限守卫和资质借阅适配 |

## 管理端配置

管理员通过 `/api/admin/workflow-forms` 保存模板草稿、配置字段 schema、绑定泛微 OA 流程编码和字段映射、发布版本，并可执行试提交预览。发布会写入 `workflow_form_template_versions` 历史版本表，同时同步 `workflow_form_templates` 作为运行态当前发布版投影；正式提交实例会保存 schema、OA 绑定和 OA payload 快照，避免后续模板修改影响历史审批单。

管理端接口：

| Method | Path | 用途 |
|---|---|---|
| `GET` | `/api/admin/workflow-forms/business-types` | 获取可配置业务类型 |
| `GET` | `/api/admin/workflow-forms/templates` | 获取模板草稿列表，包含 `oaBinding` |
| `POST` | `/api/admin/workflow-forms/templates` | 创建模板草稿 |
| `PUT` | `/api/admin/workflow-forms/templates/{templateCode}/draft` | 更新模板草稿 |
| `PUT` | `/api/admin/workflow-forms/templates/{templateCode}/oa-binding` | 保存 OA 流程绑定和字段映射 |
| `POST` | `/api/admin/workflow-forms/templates/{templateCode}/oa/test-submit` | 进入 OA Gateway 测试模式，返回测试 OA 实例号和 payload |
| `POST` | `/api/admin/workflow-forms/templates/{templateCode}/publish` | 发布模板版本 |

配置错误统一抛出 `WorkflowFormConfigException`，HTTP 响应包含 `WORKFLOW_FORM_CONFIG_INVALID`，方便前端配置页展示清晰错误。

## 运行态 API

| Method | Path | 用途 |
|---|---|---|
| `GET` | `/api/workflow-forms/templates/{templateCode}/active` | 获取当前发布版 schema |
| `POST` | `/api/workflow-forms/attachments` | 上传流程表单附件并返回结构化附件元数据 |
| `POST` | `/api/workflow-forms/instances` | 提交表单并触发 OA |
| `GET` | `/api/workflow-forms/instances/{id}` | 查看实例状态 |
| `POST` | `/api/integrations/oa/weaver/callback` | 泛微 OA 回调入口 |

正式提交使用 `WorkflowFormOaPayloadPolicy.buildPayload(..., false)`，试提交使用 `trial=true`。两条路径共用纯核心映射规则，但正式实例不会携带测试语义。

## 数据快照

表单实例必须保存当时的：

- `template_version`
- `schema_snapshot_json`
- `oa_binding_snapshot_json`
- `oa_payload_json`

这些快照是后续审计、OA 回溯和业务补偿的事实依据。管理员后续修改草稿、发布新版本或调整 OA 映射，不得影响已提交实例。

## 资质借阅

资质借阅表单提交后状态进入 `OA_APPROVING`，不会立即借出。只有 OA 回调为 `APPROVED` 且状态仍允许流转时，才通过 `QualificationBorrowApplyPort` 调用现有资质借阅应用服务；OA 驳回、重复回调或业务应用失败都不会重复创建借阅记录。

## 附件字段

附件字段值统一保存为结构化数组，每项至少包含 `fileName`，并包含 `storagePath` 或 `fileUrl` 之一，可携带 `contentType` 和 `size`。上传入口复用 DocInsight 存储适配，提交时由 active template schema 驱动纯核心校验，OA command 保留结构化附件 payload。

## 安全与幂等

- 管理端接口仅 `ADMIN` 可用。
- 业务提交必须通过 `WorkflowFormAccessGuard` 校验项目访问权。
- 泛微回调由 `OaCallbackVerifier` 校验 secret、时间窗和签名。
- OA 回调事件以 `eventId` 去重。
- OA 审批通过时使用数据库条件更新守住并发，只有从 `OA_APPROVING` 成功切换到 `OA_APPROVED` 的事务才能应用业务。
- 业务应用失败会保留失败原因，等待后续管理员重试台补偿。

## 前端和交付文档

- 配置页：`src/views/System/WorkflowFormDesigner.vue`
- 前端 API：`src/api/modules/workflowForm.js`
- 运行态表单组件：`src/components/common/DynamicWorkflowForm.vue`
- 仓库级说明：`docs/WORKFLOW_FORM_CENTER.md`
- Wiki 页面：`.wiki/pages/workflow-form-center.md`
