---
title: 评分标准候选采纳与解析任务一致性
space: engineering
category: lessons
tags: [scoreparse, adoption, concurrency, flyway]
sources:
  - specs/068-score-adoption-stale-review/spec.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseTaskStateService.java
  - backend/src/main/resources/db/migration-mysql/V1216__widen_score_parse_evidence_explanation.sql
  - backend/src/main/resources/db/rollback/migration-mysql/U1216__widen_score_parse_evidence_explanation.sql
  - src/composables/projectDetail/useScoreCandidateAdoption.js
  - src/composables/projectDetail/useScoreParseDrawer.js
backlinks:
  - .wiki/pages/_index.md
  - .wiki/log.md
created: 2026-09-22
updated: 2026-09-22
health_checked: 2026-09-22
---

# 评分标准候选采纳与解析任务一致性

## 故障链（项目 1385）

1. `score_parse_evidence.explanation` 原为 `VARCHAR(255)`；长说明插入失败，最新解析任务变为 `FAILED` 且候选项为空。
2. 页面仍持有上一已完成任务的候选项；采纳请求未携带解析任务身份，后端却读取最新任务，造成候选与任务错位。
3. 候选为空抛出的状态异常由全局处理映射为笼统的 409“系统状态冲突”，用户无法判断该刷新还是重新解析。

## 当前契约

- V1216 将说明列扩为 `TEXT`，实体映射一致。U1216 在缩列前建立 255 字 `CHECK`：存在超长数据时回滚失败且不截断；成功缩列后移除临时约束，不修改连接级 `sql_mode`。
- 采纳请求携带 `parseTaskId`，后端只接受与最新 `COMPLETED` 的 PARSE 任务一致的候选；失败任务、过期页面及已 `MANUAL_APPROVED` 的重复请求均在正式项写入前拒绝，并返回具体业务原因。
- PARSE 任务创建和采纳共用 `projects` 行锁；采纳拿锁后以 `FOR UPDATE` 当前读最新任务，避免“校验后新任务插入”的 TOCTOU。SCORING 任务创建不占用这把锁。
- 最新解析失败时前端清空旧复核快照；采纳失败先显示原始错误再刷新，刷新本身失败也不恢复旧候选。

## 副作用与验收边界

- 采纳事务内的预计得分计算会延长同项目 PARSE 创建等待时间；若出现耗时问题，应评估提交后计算，但不可移走任务身份校验与正式项写入之间的锁。
- `TEXT` 扩容属于数据库迁移；仅代码合并不等于测试服/生产库已生效。需部署 V1216 后重新解析，再核对最新任务、候选和正式项；不得以本地测试替代线上验收。
- 回滚前必须处置所有超过 255 字的说明；U1216 会明确拒绝不安全缩列。回滚 DDL 失败后须按迁移运维流程处理残留状态，不得继续假定已恢复旧列。

## 回归入口

- 后端：`ScoreParseReviewAppServiceTest`、`ScoreParseControllerTest`、`ScoreParseTaskStateServiceTest`。
- MySQL：`ScoreParseEvidenceMigrationMysqlTest`、`ScoreParseTaskLockMysqlTest`；Docker 不可用时它们会跳过，必须明确报告。
- 前端：`useScoreCandidateAdoption.spec.js`、`useScoreParseDrawer.spec.js`。
