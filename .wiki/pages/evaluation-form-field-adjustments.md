---
title: 评估表字段文案调整与 GAP 文本框下线
space: engineering
category: business-rules
tags: [tender-evaluation, initiation, gap, field-label, migration-v1191]
created: 2026-08-17
updated: 2026-08-17
health_checked: 2026-08-17
sources:
  - docs/prd-简报-评估表字段与联系人调整-2026-08-16.md
backlinks:
  - _index
  - dynamic-form-engine
---
# 评估表字段文案调整与 GAP 文本框下线（2026-08-17）

> 本页面记录客户需求 2026-08-17 批次（分支 `agent/qoder/eval-form-field-adjustments`）：
> 三项字段文案改名 + 【项目计划GAP】描述文本框下线（附件保留）。PRD：`docs/prd-简报-评估表字段与联系人调整-2026-08-16.md`。

## 需求清单

| # | 需求 | 落点 |
|---|------|------|
| 1 | 「电商MRO+办公流水金额（万）」→「客户电商年采购金额（万）」 | 评估表（静态 + 动态 fallback）/ 立项 / 商机表 |
| 2 | 「客户营收（亿）」→「客户年营收（亿）」 | 评估表 + **项目列表列**（表头 + 列选择器） |
| 3 | 删除【项目计划GAP】**描述文本框**，附件上传保留 | 前端双 GAP 组件 + 后端 DTO/Mapper 全链路停读写 |
| 4 | 「项目经理是否了解评标全流程」→「项目经理评标全流程概述」 | 评估表 + 立项 |

需求 5（联系人性质）、需求 6（座机号）已移出本期单独排期。

## 文案改名关键事实

- **纯文案改名**：字段 key（`mroOfficeFlowAmount` / `customerRevenue` / `processKnowledge`）与 DB 列名均不变，仅 label 与 DB 列注释更新。
- **双表单形态都要改**：静态表单（`BasicFieldsSection.vue` / `TenderEvaluationForm.vue`）+ 动态 fallback（`TenderEvaluationFormAdaptive.vue`）。`tender.evaluation` scope 无种子 schema，动态表单走 fallback 组件，**无需 schema 迁移**。
- **错误匹配文案同步**：`TenderEvaluationForm.vue` 的 `err.includes(...)` 与 `useTenderEvaluationForm.js` 错误文案用的是新文案字符串，改名时必须同步，否则提交错误无法高亮字段。

## GAP 文本框下线策略（核心决策）

**只停写、不删列、不动存量**：

| 层 | 处理 |
|----|------|
| 前端 UI | `ProjectPlanGapUpload.vue` / `AdaptiveGapUpload.vue` 删除 textarea，仅保留附件上传；立项侧 label 改「项目计划GAP附件」 |
| 前端透传 | `useTenderEvaluationForm.js` / `DetailPage.vue` / `useCrmOpportunitySelector.js` 移除 `projectPlanGap` 透传 |
| 前端立项带入 | `useInitiationStageActions.js` autoFill 移除 `projectPlanGap`（保留 `projectPlanGapFiles`）；`buildPayload` 显式 `projectPlanGap: null`（**防空串覆盖存量值**） |
| 后端 DTO | `EvaluationBasicDTO` 移除 `projectPlanGap` 组件（构造器 9参→8参） |
| 后端 Mapper | `TenderEvaluationSubmissionMapper` / `CrmEvaluationMapper` / `TenderEvaluationIntegrationService` / `EvaluationToInitiationMapper` 停写该字段 |
| 附件链路 | `projectPlanGapFiles`（CO-262，`project_documents` 表 linkedEntityType=EVALUATION_GAP）**完整保留，含 CRM 附件回填** |
| DB | 无 DDL，仅 V1191 更新列注释；实体字段保留（停写），存量数据不动 |

**刻意未动**（深耦合，留待后续决策）：`InitiationDto` / `InitiationViewDto` / `InitiationFieldPolicy.InitiationInput`（纯核心层 record）、`ProjectInitiationMapper`。立项侧字段链路保留，仅切断带入与提交。

## 迁移 V1191 注意点

- `scripts/new-migration.sh` 从 origin/main fetch 取 max+1，本次取到 **V1191**（预估 V1190 已被 main 占用）——**永远以脚本输出为准，不要手写版本号**。
- **MySQL MODIFY COLUMN 不带 COMMENT 会清空原注释**：每条 MODIFY 必须显式带 COMMENT。本次顺带补齐 V1139 曾清空的 `pm_understands_process` 注释。
- 回滚脚本 `U1191__*.sql` 头部必须含 `-- Input: migration-mysql/V1191__*.sql` 引用源迁移文件名，否则 `FlywayRollbackScriptCoverageTest` 门禁失败。

## 遗留事项

- `docs/generated/db-schema.md` 需在有 DB 的主工作区跑 `npm run db:generate-schema` 刷新（qoder worktree 不启动开发环境）。

## 关联

- 立项带入双链路（CO-323 教训）：后端 `EvaluationToInitiationMapper.applyEvaluationBasic` + 前端 `useInitiationStageActions.autoFillFromTender`，两侧必须同改。
- GAP 附件链路：CO-262，见 [[crm-integration-lessons]]。
- Flyway 纪律：见 [[flyway-migration-pitfalls]]。
