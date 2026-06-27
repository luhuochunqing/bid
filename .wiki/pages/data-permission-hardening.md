---
title: 项目数据权限修复收口
space: engineering
category: reference
tags: [数据权限, 项目隔离, 安全, 审计, RBAC]
sources:
  - docs/reports/data-permission-coverage-audit.md
  - backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java
  - backend/src/test/java/com/xiyu/bid/ProjectAccessGuardCoverageTest.java
backlinks:
  - _index
  - implementation/attachment4-gap-matrix
  - implementation/attachment6-function-list-trace
  - roles-and-permissions
  - workflow-form-center
created: 2026-04-25
updated: 2026-06-21
health_checked: 2026-06-27
---
# 项目数据权限修复收口

## 1. 结论

西域数智化投标管理平台已完成本轮项目数据权限 P0、P1、P2 修复收口。真实 API 单一路径下，已识别的项目关联高风险接口已经接入统一项目访问断言、可见项目集合过滤、管理角色收紧或显式豁免清单。

本结论不等同于“无需后续治理”。后续新增带 `projectId` 的接口、DTO、实体或服务时，必须继续通过项目权限门禁测试，或在豁免清单中写明非项目数据理由。

## 2. 统一口径

| 项 | 口径 |
|---|---|
| 唯一权限来源 | `ProjectAccessScopeService` |
| 纯核心策略 | `ProjectLinkedRecordVisibilityPolicy` 只根据 admin、allowedProjectIds、record.projectId 判断可见性 |
| 共享记录 | `projectId == null` 视为非项目/共享记录，保留可见 |
| 管理员 | 保持全量行为 |
| 普通用户 | 只能读取或修改当前用户可见项目关联记录 |
| 不可靠项目映射 | 先收紧角色或写入显式豁免，不新建并行权限体系 |

## 3. 修复范围

| 优先级 | 范围 | 状态 |
|---|---|---|
| P0 | 费用、资源费用台账、文档编辑/导出/版本、投标结果、标讯、任务、批量操作 | 已完成跨项目读写风险收口 |
| P1 | 统计看板、导出、AI/分析、审批/聚合、项目质量 | 已完成统计、导出、聚合泄露风险收口 |
| P2 | 日历、工作台、证书借阅、资质借阅、模板使用记录、告警历史 | 已完成证据补强 |

## 4. 已落地机制

- 创建、更新、归还、审批、导出等写操作先校验目标项目或目标记录所属项目。
- 列表、详情、统计、聚合、导出统一按当前用户可见项目集合过滤。
- 模板使用次数、导出 `recordCount` 等响应聚合字段按过滤后的数据计算。
- 告警历史因 `relatedId` 暂无可靠项目映射，已收紧为 `ADMIN/MANAGER` 可读。
- 工作台日程复用 `CalendarService` 的项目范围过滤，不另建权限体系。

## 5. 自动化门禁

后端新增 `ProjectAccessGuardCoverageTest`：

- 扫描带 `projectId` 或引用项目关联 DTO/实体的 Controller/Service。
- 要求命中 `ProjectAccessScopeService`、统一访问守卫、可见项目过滤策略或显式豁免。
- 存量暂不适用入口写入 `project-access-guard-baseline.txt`，后续整改时同步缩小基线。

## 6. 验证记录

| 验证 | 结果 |
|---|---|
| P2 权限测试集合 | 通过 |
| Review 补强测试：模板下载 useCount、资质 recordId 归还 | 通过 |
| `ProjectAccessGuardCoverageTest` | 通过 |
| `FPJavaArchitectureTest`、`MaintainabilityArchitectureTest` | 通过 |
| 前端 Vitest、前端数据边界、文档治理、行预算检查 | 通过 |

详细审计和验证命令见 `docs/reports/data-permission-coverage-audit.md`。
