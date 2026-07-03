# Data Model: 防御性 Collection 与优雅降级治理

**Date**: 2026-07-03

## 概述

本 feature 不涉及 DB schema 变更，无新实体。纯代码治理任务。

## 现有实体影响

本 feature 修改的 `Collectors.toMap` 调用涉及以下现有实体的查询结果：

| 实体 | toMap 调用点 | key 字段 | 风险 |
|---|---|---|---|
| Project | TenderQueryService, ProjectQueryService, ProjectExportService 等 | tenderId (外键), id (主键) | tenderId 高风险（一对多） |
| Tender | ProjectQueryService, TenderFavoriteService 等 | id (主键) | 低风险 |
| User | TenderQueryService, TaskBoardService 等 | id (主键) | 低风险 |
| TenderAssignmentRecord | TenderQueryService | tenderId (外键) | 高风险（一对多） |
| ProjectInitiationDetails | ProjectQueryService | projectId (外键) | 高风险（一对多） |
| TenderEvaluation | ProjectQueryService | tenderId (外键) | 高风险（一对多） |
| ProjectResult | ProjectQueryService | projectId (外键) | 高风险（一对多） |
| DocumentSectionAssignment | DocumentSectionTreeService | sectionId (外键) | 高风险（一对多） |
| DocumentSectionLock | DocumentSectionTreeService | sectionId (外键) | 高风险（一对多） |
| WorkflowFormTemplateVersionMaxRow | JpaWorkflowFormAdminStore | templateCode (业务字段) | 高风险 |

**无 schema 变更**：所有修复均在应用层（Java 代码），不修改 DB 表结构、不新增迁移脚本。
