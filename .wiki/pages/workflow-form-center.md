---
title: 流程表单中心（OA 对接已取消）
space: engineering
category: integration
tags: [workflow-form, approval, configuration]
sources:
  - docs/research/WORKFLOW_FORM_CENTER.md
  - backend/src/main/java/com/xiyu/bid/workflowform/README.md
  - src/views/System/workflow-form-designer/README.md
  - src/api/modules/workflowForm.js
  - backend/src/main/java/com/xiyu/bid/workflowform/
backlinks:
  - _index
  - integration-wecom
  - modules
created: 2026-04-29
updated: 2026-05-31
health_checked: 2026-06-05
---
# 流程表单中心

> **OA 流程对接已取消**（2026-05-28 确认），不再纳入本系统范围。
> 本文档保留做参考，流程表单功能不再对接 OA 审批。

流程表单中心提供“管理员配置表单 -> 发布模板 -> 业务用户提交 -> 触发泛微 OA -> 回收审批结果 -> 审批通过后应用业务”的产品化底座。

## 交付口径

- 本系统创建和渲染表单，保存模板版本、实例快照、OA 关联号和业务应用状态。
- 泛微 OA 是审批事实源，负责审批流、审批节点、审批人和审批结论。
- 本系统不实现本地审批流，不在资质借阅等业务模块里硬编码 OA 规则。
- 资质借阅第一版已接入：OA 通过前不借出，OA 驳回不创建借阅记录。

## 产品入口

| 角色 | 入口 | 能力 |
|---|---|---|
| 管理员 | 系统设置 -> 流程表单配置 | 新建模板、字段配置、OA 绑定、预览、试提交、发布 |
| 业务用户 | 资质借阅等业务入口 | 填写已发布表单并提交 OA |
| OA 系统 | `/api/integrations/oa/weaver/callback` | 回传审批结果 |

## 管理员配置闭环

1. 新建表单模板：模板编码、表单名称、业务类型、启用状态。
2. 配置字段：文本、日期、数字、下拉、人员、项目、附件、说明文本等。
3. 绑定 OA：配置泛微 `workflowCode` 和字段映射。
4. 预览表单：复用运行态 `DynamicWorkflowForm`，确保配置页和业务页一致。
5. 试提交：调用 `/api/admin/workflow-forms/templates/{templateCode}/oa/test-submit`，进入 OA Gateway 测试模式。
6. 发布模板：写入历史版本并刷新运行态投影。

## 版本和快照

发布会写入：

- 草稿：`workflow_form_template_drafts`
- 历史版本：`workflow_form_template_versions`
- 当前发布投影：`workflow_form_templates`

实例提交会写入：

- `template_version`
- `schema_snapshot_json`
- `oa_binding_snapshot_json`
- `oa_payload_json`

这保证了“已提交表单实例必须保留当时 schema 快照”，后续管理员修改字段或 OA 映射不会影响历史审批单。

## 架构边界

| 层 | 位置 | 职责 |
|---|---|---|
| 纯核心 | `backend/src/main/java/com/xiyu/bid/workflowform/domain` | schema、字段类型、OA 映射、payload、状态流转、OA 结果应用策略 |
| 应用编排 | `backend/src/main/java/com/xiyu/bid/workflowform/application` | 保存草稿、发布模板、提交实例、触发 OA、处理回调、业务应用 |
| 副作用 | `backend/src/main/java/com/xiyu/bid/workflowform/infrastructure` | JPA、OA Gateway、项目权限、资质借阅适配 |
| 前端配置 | `src/views/System/WorkflowFormDesigner.vue` | 配置页面交互 |
| 前端 API | `src/api/modules/workflowForm.js` | 唯一真实 API 入口 |

## OA 状态和业务生效

正式提交时 `WorkflowFormOaPayloadPolicy.buildPayload(..., false)` 生成 OA payload；试提交时 `trial=true`。两者共用映射规则，但正式实例不会携带测试语义。

资质借阅生效时点：

1. 业务用户提交表单。
2. 表单实例进入 `OA_APPROVING`。
3. OA 回调 `APPROVED`。
4. 系统通过状态条件更新拿到业务应用权。
5. 调用 `QualificationBorrowApplyPort` 创建借阅记录。
6. 实例状态变为 `BUSINESS_APPLIED`。

OA 驳回、OA 发起失败、重复回调、业务应用失败都不会重复创建借阅记录。

## 安全与运维

- 管理端接口仅 `ADMIN`。
- 运行态提交必须通过项目权限守卫。
- OA 回调校验 secret、时间窗和签名。
- OA 事件以 `eventId` 幂等。
- 业务应用失败保留失败原因，后续由“流程表单实例运维重试台”补齐运营入口。

## 关联页面

- [[modules]]
- [[integration-wecom]]
- [[api-openapi]]
- [[data-permission-hardening]]
- [[business-process]]
