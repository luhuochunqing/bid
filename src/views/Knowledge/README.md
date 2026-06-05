# Knowledge 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
知识模块负责案例、资质、模板等知识资产页面。
该目录主要提供可复用资产的查询、展示、使用和维护入口。
页面内容以真实知识数据和业务操作为主，不承载通用组件实现。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `Case.vue` | View | 案例列表页 |
| `CaseDetail.vue` | View | 案例详情页 |
| `Qualification.vue` | View | 资质文件页编排层，复用 `components/qualification/` 下的列表、借阅记录与对话框组件 |
| `Template.vue` | View | 模板库页 |
| `components/case/` | 子目录 | 案例列表、详情头部、表单与页面状态 composable |

## 最近更新

- 2026-04-19: 资质页拆分为页面编排层 + `components/qualification/` 子组件，并移除页面内硬编码借阅记录。
- 2026-04-19: 案例页拆分为列表/搜索/表单/详情头部组件与 composable，案例列表改为参数驱动查询并移除本地 mock 主路径。
