# 流程表单配置页面

一旦我所属的文件夹有所变化，请更新我。

本目录承载系统设置下的“流程表单配置”前端能力：管理员可以维护模板草稿、字段列表、泛微 OA 流程编码和字段映射，并通过预览/试提交确认配置。

## 边界

- `WorkflowFormDesigner.vue` 是页面壳和交互编排，所有数据通过 `workflowFormApi` 访问真实后端。
- 已加入历史版本入口：管理员可查看某模板发布历史，并可一键回滚到目标版本（回滚会形成新发布版本）。
- `workflowFormDesignerCore.js` 是前端纯 helper，负责默认模板、字段增删排序、OA 映射生成、既有模板绑定恢复和错误文案提取。
- `workflow-form-designer.css` 只承载页面样式，避免 Vue 单文件超过 300 行。

## 约束

- 试提交调用 `/api/admin/workflow-forms/templates/{templateCode}/oa/test-submit`，进入 OA Gateway 的测试模式，不复用正式实例提交接口。
- 编辑既有模板时必须使用后端返回的 `oaBinding` 恢复 Provider、流程编码和字段映射，不能自动覆盖管理员已有映射。
- 预览使用运行态 `DynamicWorkflowForm`，确保配置页看到的 schema 与业务入口渲染方式一致。
- 第一版不做自由拖拽，使用添加、删除、上移、下移保证稳定性和可测试性。
