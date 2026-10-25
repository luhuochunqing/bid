---
title: 评分解析分表完整性范围误判 + 部分通过覆盖正式结果双修复
space: engineering
category: guide
tags: [评分解析, 分表完整性, 数量启发式, 项目级全量替换, parseTaskId 隔离, spec062]
created: 2026-09-19
updated: 2026-09-19
health_checked: 2026-10-25
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreCompletenessPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/StructuredTableExtractionHandler.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemPersistenceService.java
backlinks:
  - _index
  - score-parse-service
  - spec059-score-parse-evidence-pipeline
---

# 评分解析分表完整性范围误判 + 部分通过覆盖正式结果双修复

> **一句话**：spec 062 多表拆分后留下两个裂缝——①完整性数量启发式用"整份文档正文长度"
> 否决"单张独立表"，2 项、声明自洽的商务表永远被误判漏项；②"任一表通过即落库"策略
> 组合 `persistItems` 的项目级 `deleteByProjectId`，部分候选把正式评分项和实际打分洗掉。
> 修复：数量启发式限定文档级兜底；正式发布门禁 = 文档级 AUTO_APPROVED 且候选非空；
> 事实删除范围按 parseTaskId 隔离。

## 问题一：分表完整性误判（ScoreCompletenessPolicy）

**现象**：技术表（3 项 30/20/10，声明 60）+ 商务表（2 项 20/20，声明 40）都自洽，
但商务表被判 `ABNORMALLY_LOW_COUNT` → REVIEW_REQUIRED。权重失配场景的主判因也被
数量缺陷抢占（`resolvePrimaryFallback` 按 defects 顺序返回）。

**根因**：`ScoreTableSplitPolicy.split()` 把整份 `tenderSectionText` 传给每张独立表的
`evaluateCompleteness`，而 `leafFacts.size() < 3 && textLength > 300` 的数量启发式是为
"文档级单表兜底"设计的防漏抽护栏。拆分后校验对象是"表"，`textLength` 却还是"文档"。

**修复**：数量启发式增加范围条件 `declaredTotalResolution.source() != STRUCTURED_SUMMARY_ROW`——
有本表结构化总分声明的独立表以"声明总分 vs 末级权重合计 + 证据"判漏抽（漏抽必然
合计 < 声明，已有更强判据）；无声明（TENDER_TEXT/DEFAULT_100 兜底）的文档级校验保持原护栏。

**关键边界（守卫测试固定）**：
- 真正漏项（声明 40 只抽到 20）→ 仍 `WEIGHT_SUM_MISMATCH`（fallbackReason 不再被数量缺陷抢占）
- 缺证据（合计正确但某项无单元格/分值定位）→ 仍 `MISSING_EVIDENCE`
- TENDER_TEXT 兜底 + 项数少 + 长正文 → 仍 `ABNORMALLY_LOW_COUNT`（护栏不放松）

## 问题二：部分通过覆盖正式结果（markEvidencePipelineCompleted + persistFactsAndEvidence）

**现象**：项目已有 5 项正式评分项（30/20/10/20/20）+ 关联实际打分。新解析"技术表通过、
商务表缺项"→ 文档级 REVIEW_REQUIRED，但编排层把 3 项通过表候选送进 `persistItems`
→ `deleteByProjectId` 项目级全量替换 → 正式结果剩 3 项、打分被清、预计得分按残缺结果重跑。

**根因**：spec 062 引入"任一表通过即落库通过表"，落库条件只查 `!candidates.isEmpty()`，
没查文档级决策；legacy 路径（`ScoreParseAppService`）已有 task2 收敛纪律
（仅 AUTO_APPROVED 覆盖旧 score_item），Evidence Pipeline 路径没对齐同一契约。

**修复（三点）**：
1. `markEvidencePipelineCompleted` 正式发布门禁：仅"文档级 AUTO_APPROVED 且候选非空"
   才 `persistItems` + `estimateForProject`；REVIEW_REQUIRED/STRUCTURE_INCOMPLETE/PARSE_FAILED
   一律不进发布路径。部分通过只留复核候选，`decisionStatus` 保持 REVIEW_REQUIRED
   （执行状态 COMPLETED 不改写业务决策）。
2. `persistFactsAndEvidence` 事实删除范围 `deleteByProjectId` → `deleteByParseTaskId`
   （与 evidence 删除口径一致）：同任务重复保存幂等，历史任务证据链不被洗掉，
   跨任务/跨项目不串。
3. 复核候选按 `parseTaskId` 读取（`getReviewSnapshot` 现状），同项目连续两个复核任务互不串。

## 回归场景与测试落点

| 场景 | 测试 |
|---|---|
| A. 300/301/10000 字无关正文 → 两表通过、5 项、100 分（逐项断言编号/分值/来源） | `ScoreFactShadowPipelineIndependentTableTextLengthTest` |
| B. 声明 40 只抽到 20 → 复核 | 同上 + `ScoreCompletenessPolicyTest` |
| C. 合计正确但缺证据 → 复核 | `ScoreCompletenessPolicyTest` |
| D. 部分通过不破坏旧正式结果（ID/打分/旧证据不变，真实持久化） | `ScorePartialOverwritePersistenceTest` |
| E. 无旧结果时部分通过也不发布 | 同上 |
| F. 全通过正常发布 5 项 100 分 + 触发预计得分 | 同上 + `StructuredTableExtractionMarkCompletedTest` |
| G. 候选按任务隔离 + 同任务重复保存幂等 + 跨项目不受影响 | `ScorePartialOverwritePersistenceTest` |

## 陷阱备忘

- `shadow.completeness()`（文档级聚合）取的是**最差决策那张表**的明细，
  其 `leafWeightSum` 是单表值，不是全表合计——跨表断言合计要自己汇总 leafFacts。
- `@DataJpaTest` 不含 Jackson 自动配置，`ScoreItemPersistenceService` 的 5 参构造器
  注入 ObjectMapper 需要 `@TestConfiguration` 补 bean。
- 纯源码 worktree（agent-start-task 拉的临时 worktree）无 node_modules，eslint/
  test:unit 门禁只能在开发宿主 trae 验证。
- 金标门禁（SCOREPARSE_GATE）针对"新版式修复"；决策策略/落库门禁修复无版式变更，
  金标回放全绿后可用 `SCOREPARSE_GATE_SKIP=1` 并在报告中说明。
