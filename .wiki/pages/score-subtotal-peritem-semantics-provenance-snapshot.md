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

## 遗漏收敛：正文与兜底入口（外部复审 P1/P2，2026-10-09）

- 表格路径先行加上单项语义后，正文入口曾是盲区：`parseText()` 主解析与 `DOCUMENT_TOTAL` 兜底均未过滤，导致「每项 满分10分，总分100分」误报 `ORIGINAL_TOTAL_CONFLICT`、「本项目每项总分10分」被兜底认作项目总分。
- 修复：主解析在 matcher.find() 后即查 `isSingleItemMention`（早于 categoryTitle 判定）；兜底查「总分」关键词所在分句的左侧（`DOCUMENT_TOTAL` 增加 `(总分)` 捕获组），与主解析真正同一文法；跨行窗口用 `normalizeWindow` 保留 `\n` 作分句边界，同时 `DOCUMENT_TOTAL` gap 去掉 `\n` 保跨行断句匹配（二者耦合，见下节）。
- `select()` 的 `hasValue()` 改调 `hasTableTotalEvidence()`：明确「最高分」表头下合计格只写单项说明时保持 `TABLE_SUBTOTAL_COLUMN_UNRESOLVED`（此前会误判已验证）。
- 教训：**语义规则必须覆盖全部解析入口**（表格/正文/兜底/验证），加在一条路径上会制造新的不一致；后续新增入口时以「是否复用 `ScoreSubtotalColumnPolicy` 文法」为检查项。

## 已知取舍与后续项（round 6 审计登记）

- 量词表未覆盖「各项/各条」：正文出现「各项满分10分，总分100分」仍可能误报冲突（fail-open 方向）。未纳入本次范围，避免再放大误杀面；如需收窄按同族模式追加并配 pinning。
- `本项(?!目)` 会误杀「本项工程评分总分100分」类整表句（fail-closed：该句总量可能 UNKNOWN）。取舍：宁可未决不可猜错。
- `DOCUMENT_TOTAL` 的 lazy gap `[^。；;\n]{0,80}?` 可跨逗号，故不能查整段匹配 span（会毒掉逗号后的干净分句）。**R8 起 gap 已去掉 `\n`（最终 `[^。；;]{0,80}?`），改由「总分关键词分句」判据，见下节。**
- **后续项（未修，登记）**：`ScoreTableSplitPolicy` 自带同型 `DOCUMENT_TOTAL`（`(?:本项目|本标包)[^\n]{0,40}(?:总分|满分)…`）无单项语义，属同型盲区；不在本次授权范围，需单独收敛（同为「总分关键词分句」判据）。

## 行边界真相记录（round 7/8 审计，2026-10-09）

- round 7 曾把跨行窗口的 `join(" ")` 改回 `join("\n")`，**评审实测为证明性空转**：`normalizeTextLine` 的字符类含 `\n`，会把它压成空格，两种 join 输出的 normalized 串逐字节相同。教训：改流向必须沿「输入 → 归一化 → 匹配」全链验证，不能只改一端。
- 真实修复（round 8，commit `e20a02099`）三处同步：`DOCUMENT_TOTAL` gap 去掉 `\n`（保住跨行断句匹配）、新增 `normalizeWindow`（保留 `\n` 作分句边界）、删除 `parseText` 冗余的 `HEADING` 手工计数分支（`OriginalScoringTextScopePolicy.headings` 已覆盖，306→296 行；round 9 再清理死常量后 295 行）。
- 固化用例纪律：pinning 必须先在旧实现下 RED 再 GREEN；round 7 的用例因主解析截胡恒绿，已按其性质在测试内注明「防回归 pin 而非 RED-GREEN 对」。
