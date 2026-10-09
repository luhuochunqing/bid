---
title: 评分小计单项满分语义规则与案例来源快照守卫
space: engineering
category: reference
tags: [scoreparse, subtotal, provenance, concurrency, data-integrity]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreSubtotalColumnPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - backend/src/main/java/com/xiyu/bid/casework/infrastructure/KnowledgeCaseRepository.java
  - backend/src/main/java/com/xiyu/bid/casework/application/port/CaseProvenanceBackfillPort.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/domain/Project1359ScoringContractTest.java
  - backend/src/test/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapterMysqlTest.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-09
updated: 2026-10-09
health_checked: 2026-10-09
archive: false
---

# 评分小计单项满分语义规则与案例来源快照守卫

## 规则一：单项分值陈述永不是整表小计证据

- 判定位置：`ScoreSubtotalColumnPolicy.hasTableTotalEvidence`（语义规则，非关键词紧邻排除）。
- 满分/总分匹配仅当所在分句（按 ，。；; 与换行划分）左侧无单项量词/主体时，才算整表满分声明。
- 单项语义模式：`单项|每一?项|每一?条|每一?处|每一?篇|每一?个|本项(?!目)|此项`。
- 陷阱史：
  - 负向后行断言只挡紧邻（`单项满分`），`本表单项 满分10分`、`每项最高满分10分`、`本表每项的满分10分`曾误产 `Subtotal=10`。
  - `每一项`/`每一条`（"一"插入）旧模式搜不到；`本项`曾误杀`本项目`（整表主体，现用 `(?!目)` 放行）。
- 不确定时保持 `TABLE_SUBTOTAL_COLUMN_UNRESOLVED`，绝不猜测；同文法贯穿列选择、冲突检测、totals 循环、legacy 无表头路径。
- 列识别仍需满分证据（既有契约）：`总分` 混合格保持未决，整表总量由正文总分决议。

## 规则二：来源回填必须原子校验扫描快照

- `CaseProvenanceBackfillPort.Update` 附带 `expectedResponseText/expectedScoringPointTitle/expectedSourceProjectId`。
- `KnowledgeCaseRepository.updateProvenanceIfActive` 为原生条件更新：`WHERE id + ACTIVE + 三业务字段`，字符串用 `BINARY`（库默认 `_ci` 校对忽略大小写/尾随空格，普通 `=` 会误判相同）。
- 不满足返回 0 行，`CaseProvenanceBackfillAdapter` 抛异常回滚项目批次（`REQUIRES_NEW` 保留）。
- 陷阱史：旧查询只校验 id+ACTIVE，扫描后正文 A→B 仍写入 A 的来源（SQL 级复现确认）。

## 副作用（Cross-Module Impact）

- `[[scoreparse-1359-stale-200-total-rca]]`：同域前序根因，本页为其语义收紧跟进。
- `OriginalScoringContext` totals 循环新增跳过分支；`OriginalScoringContext.java` 保持 300 行预算，压缩靠注释合并与纯表达式内联。
- pre-push `scoreparse 金标门禁`：本变更属非版式逻辑收紧，走 `SCOREPARSE_GATE_SKIP=1` + PR 理由（无新版式夹具可加）。
- 回填 Update 记录 6→9 元：全库 `new Update(` 调用点已穷举（生产 2 处+测试 16 处），record 位置构造错位属静默错误，改契约时必须重跑穷举。
