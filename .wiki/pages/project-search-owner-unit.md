---
title: 项目搜索按招标主体（ownerUnit 数据链路与关键词过滤契约）
space: engineering
category: guide
tags: [项目搜索, 招标主体, ownerUnit, purchaser_name, 内存过滤, useProjectFilter]
created: 2026-08-31
updated: 2026-08-31
health_checked: 2026-09-14
sources:
  - backend/src/main/java/com/xiyu/bid/project/controller/ProjectController.java
  - backend/src/main/java/com/xiyu/bid/project/service/ProjectListEnrichmentSupport.java
  - src/views/Project/composables/useProjectFilter.js
backlinks:
  - _index
---
# 项目搜索按招标主体（ownerUnit 数据链路与关键词过滤契约）

> **一句话**：项目搜索框 placeholder"搜索项目名称、招标主体"长期只匹配项目名（文案超期承诺），
> 2026-08-31 补齐：`name` 关键词前后端均追加 `ownerUnit` 匹配。本页记录招标主体数据链路与过滤契约。

## 招标主体数据链路（关键事实）

- 招标主体真实存储在 **`tenders.purchaser_name`**（采购人名称），**不在 `projects` 表**。
- 关联方式：`projects.tender_id` **逻辑外键**（无 JPA 映射），应用层 join：
  `ProjectQueryService.enrichWithTenderAndDetails` 批量 `findAllById` →
  `ProjectListEnrichmentSupport.populateFromTender` 回填 DTO（`ownerUnit` 为空才回填）。
- 立项后 `project_initiation_details.owner_unit` 快照优先（提交后锁定，见 ProjectFieldLockPolicy）。
- 列表页"招标主体"列展示的就是 DTO 的 `ownerUnit` —— **搜到的 = 看到的**。

## 关键词过滤契约（name 参数）

| 层 | 位置 | name 匹配字段 |
|---|---|---|
| 后端列表 | `ProjectController.getAllProjects` | `name` ∪ `customer` ∪ **`ownerUnit`** |
| 后端导出 | `ProjectExportService` | 同上（注释级契约：与列表保持一致） |
| 前端过滤 | `useProjectFilter.js` | `p.name` ∪ `p.projectName` ∪ **`p.ownerUnit`** |

- 项目列表是**内存过滤**架构（`getAllProjects()` 全量 + Controller 流式 filter），
  过滤发生在 enrichment **之后**，所以 `ownerUnit` 已回填、加字段零 SQL 改动。
  标讯模块的 JPA Specification（`TenderSpecification`）是另一范式，勿混淆。
- 大小写不敏感：后端 `containsIgnoreCase`（null 安全）；前端 `.includes`（`|| ''` 兜底）。
- "招标主体"匹配**不含**代理机构 `tender_agency`——与列表列展示保持一致。

## 已知分歧（前后端契约非严格等价，勿误读上表）

| 维度 | 后端（`name` 参数） | 前端（`f.name`） |
|---|---|---|
| 大小写 | 不敏感（`containsIgnoreCase`） | **敏感**（`.includes`） |
| 匹配字段 | `name` ∪ `customer` ∪ `ownerUnit` | `p.name` ∪ `p.projectName` ∪ `p.ownerUnit`（无 `customer`） |

- `customer` 仅后端匹配 → UI 按客户名搜索列表不命中、但导出命中（导出走后端）。
- `projectName` 仅前端匹配 → DTO 无此字段，属 List.vue 显示回退的防御性延续。
- 中文主体名无感；英文/混合主体名大小写行为不一致。收敛决策待另开任务。

## 陷阱

1. **测试库（H2）`projects.tender_id` NOT NULL**：集成测试造数不能创建无标讯项目，
   每个项目必须关联 tender，用不同 `purchaserName` 区分正反例。
2. **`GET /api/projects/search?name=` 遗留接口只搜项目名**：前端无调用方，勿与列表接口混淆。
3. `searchForm.ownerUnit` 前端字段存在但 UI 未暴露——它是导出参数的历史遗留，非筛选入口。
