---
title: 案例库按 PRD 精简重构（筛选区三字段 / 排序固定 / 详情返回招标主体）
space: engineering
category: guide
tags: [案例库, casework, PRD, 筛选区, 招标主体, ownerUnit, sortBy死参数]
created: 2026-08-24
updated: 2026-08-24
health_checked: 2026-09-14
sources:
  - src/views/Knowledge/views/CaseGrid.vue
  - src/views/Knowledge/views/components/CaseDetailDialog.vue
  - backend/src/main/java/com/xiyu/bid/casework/application/service/KnowledgeCaseQueryAppService.java
backlinks:
  - _index
---
# 案例库按 PRD 精简重构

> **结论**：案例库页面/详情按飞书 PRD（BQvZdb6lDowPpyxdgeDczpcWnCg §2.1/2.3/5.4）精简，列表排序语义与详情接口契约发生变化，后续改动须遵守以下约束。

## 新业务规则

1. **筛选区只剩三个字段**：关键词（`搜索评分项/要素...`）、客户类型（单选）、项目类型（单选）。评分项类别/项目状态/上传时间/结项时间/排序切换全部下线；后端查询参数保留但前端不再传递。
2. **列表固定按创建时间倒序**：`KnowledgeCaseQueryAppService.queryCases` 的 `sortBy` 参数已删除（前端 → `getGridList` → 控制器 → 服务整条链）。**唯一保留点**是导出路径 `CaseExportQuery.sortBy`（`CaseExportAppService.resolveSort` 仍在消费），不要把导出排序也删了。
3. **置顶/下架能力下线（仅 UI）**：卡片不再显示置顶角标与复用次数；详情弹窗（原抽屉，已改为居中 el-dialog，组件 `CaseDetailDialog.vue`）删除置顶/下架/相似案例/复用记录。后端 `/api/cases/{id}/pin|unpin|off-shelf` 端点保留未删，仅前端不再调用。
4. **详情接口契约变更**：`GET /api/cases/{id}` 不再返回 `KnowledgeCase` 实体，改返回 `KnowledgeCaseDetailResponse`（casework/dto）。新增 `ownerUnit`（招标主体）字段，通过 `source_project_id → project_initiation_details.owner_unit` 关联，关联不到为 `null`，前端展示 `-`。

## 陷阱与边界

- **`v-loading` 不能挂在 `v-if` 为假的元素上**：`CaseDetailDialog.vue` 加载期 `caseData=null`，若 `v-loading` 与 `v-if="caseData"` 同元素，加载动画永不渲染（弹窗白屏无反馈）。正确做法：`v-loading` 挂常驻容器、`v-if` 收窄到内容本体（见 commit a47c49949）。
- **客户类型字典有旧数据兼容键**：`CUSTOMER_TYPE_LABELS` 里 `STATE_OWNED`/`FOREIGN` 仅用于展示映射（兼容旧数据），筛选项必须用派生的 `CUSTOMER_TYPE_OPTIONS`（已过滤这两个键），否则下拉会出现重复文案。
- **casework → project.repository 依赖**：`KnowledgeCaseQueryAppService` 直接注入 `ProjectInitiationDetailsRepository`，与模块既有惯例一致（`ProjectArchiveDetailService` 等 4 处先例），ArchUnit 允许；但已在 `project-access-guard-baseline.txt` 声明其仅做展示字段关联、不以 projectId 做访问控制。
- **`KnowledgeAccessSecurityTest` 的 mock 签名**：`queryCases` 增减参数时必须同步该测试中 `any()` 的个数（当前 11 参：9 个 `any()` + 2 个 `anyInt()`），且 spec 039 后 archive 下载/预览用例已改为 mock `ArchiveFileResponseFactory.build(...)`，不要再反射设置 `configuredUploadDir`。

## 验证基线

ArchUnit 32/32、`KnowledgeCaseQueryAppServiceTest` 3/3、`KnowledgeAccessSecurityTest` 27/27、`knowledge.spec.js` 11/11、E2E spec 已改名 `case-grid-reuse-detail-flow.spec.js`（原 `-offshelf-` 命名随下架下线废弃）。
