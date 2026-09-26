---
title: 项目 1382 PDF 评分方案资格门失效根因
space: engineering
category: debugging
tags: [scoreparse, pdf, sidecar, source-evidence, scheme, fact-identity]
sources:
  - document-converter-sidecar/converter.py
  - document-converter-sidecar/tests/test_pdf_table_artifact.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreQualificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidator.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java
  - backend/src/test/resources/scoreparse-golden/case-project-1382-pdf-real-shape.expected.json
backlinks:
  - .wiki/pages/_index.md
  - .wiki/pages/scoreparse-multimodal-primary.md
  - .wiki/pages/scoreparse-1384-docx-scheme-contract-rca.md
created: 2026-09-26
updated: 2026-09-26
health_checked: 2026-09-26
archive: false
---

# 项目 1382 PDF 评分方案资格门失效根因

## 状态与证据边界

- 现象：测试站 `/project/1382`（办公用品.pdf）"一直无法准确识别评分项"——页面 3 个陈旧评分项、合计 58 分、待人工复核。
- 诊断证据：`.runtime/root-cause/project-1382-score-parse-evidence.json`（H2a 证实 / H2b、H2c 证伪），数据源为 `/api/projects/1382/score-parse/review` 只读快照 + `task_result` 持久化产物。
- **召回不是瓶颈**：16/16 条事实齐全，叶子分值合计 100 = 原文声明的总分 100。失败全部发生在"方案资格门"之后的资格判定与采纳率上。
- 真实客户文件不入库；本页与金标夹具只保留脱敏后的结构形状（章标题、目录点线行、表格内分值列、空 code 兄弟事实）。
- 本文不含服务器 Sidecar 日志推断；PDF 表格归属部分由合成 PDF（`fitz` 绘制线框表 + 章标题 + 目录页）逐条复现。

## 必然性链

| 边界 | 当前输入/输出 | 结果 |
|---|---|---|
| PDF → Sidecar 表格产物 | `converter.py` 的表格分支对所有 PDF 表格硬编码 `"sourceSection": ""`（只有 OCR textBlocks 走 `_pdf_section_heading`） | 表格派生的模型事实没有任何章节锚点 |
| 方案资格判定 | `OriginalScoreQualificationPolicy.qualify()` 对无方案归属的事实返回 `sourceSchemeNotVerified` | 表格事实全部不可采纳 → 页面停在 3 条陈旧项 |
| 正文标题识别 | `OriginalScoringTextScopePolicy.headings()` 的 `SOURCE_HEADING` 命中目录行「第三章 评审办法..............................27」 | 目录行成为"方案根"，与正文同名章标题构成两个方案 |
| 方案同源比较 | `OriginalScoringSchemePolicy.schemeCore()` 只剥 `TABLE_SUFFIX`，不剥点线 + 尾页码 | `sameScheme("第三章 评审办法", "第三章 评审办法....27")` 为 false → 文档级 `ORIGINAL_SCHEME_AMBIGUOUS` 提前返回 |
| 事实身份 | `OriginalScoreQualificationPolicy.modelFactId()` 的判别因子只有 `logicalKey(schemeId, scopeStatus, sectionTitle, code)`；空 code 的同节叶子全部同 key | 兄弟事实 factId 碰撞 → 资格映射串台（一条合格 ⇒ 全组合格）、`leafTotal` 重复计入、`claim.id` 归并 |
| 判重守卫 | `MultimodalScoreFactValidator.validateDuplicates` 用 `(schemeId, scopeStatus, sectionTitle, code)` 去重，空 code 事实必然同键 | 真实运行快照中 13 个空 code 叶子产生 12 条 `DUPLICATE_CODE`，直接阻断 `AUTO_APPROVED` |

## 修复

1. **F1 sidecar 章节归属**：新增 `_pdf_chapter_heading()`（章级标题识别，拒绝含点线 + 尾页码的目录行）与 `_pdf_page_chapter_heading_positions()`（利用 `extract_text_lines()` 的行几何）；页面循环改用 `find_tables()` 拿到每张表的 `bbox.top`，按"最近前置章标题"逐表写入 `sourceSection`，跨页 carry-over；`extract_text_lines()` 不可用时退化为"页级首个章标题"。`_merge_pdf_score_table_continuations()` 在两表章节均已知且不一致时不再合并。`PDF_TABLE_EXTRACTION_VERSION` 升至 `pdf-table-v2`（消费方按版本区分语义）。
2. **F2 目录行排除**：`headings()` 在两种标题模式之后统一执行 `TOC_TRAILER` 检查（`(?:[.…·•‥∙．]\s*){2,}\d{0,4}\s*$`），命中即整行不作标题；`schemeCore()` 在比较前用同一常量剥掉尾部点线 + 页码，防御其它入口漏进带污染的标题。
3. **F3 真实形状金标**：`case-project-1382-pdf-real-shape.{md,expected.json}` 复刻真实 PDF 形状（目录点线行、纯文本章标题、多表同方案、跨章非评分表），回放用例断言 1 个方案、无 `ORIGINAL_SCHEME_AMBIGUOUS`、16 条合格事实、叶子合计 100、`autoApprovable`。
4. **F4 事实身份**：`modelFactId()` 判别因子增加 `dimension + weight + quotedText`（NUL 分隔，与既有 `logicalKey` 同风格）；`OriginalScoreEvidencePolicy.verifyModel()` 的 `ScoreEvidenceClaim.id` 由 `logicalKey(...)` 改为 `modelFactId(f)`，使 `eligibleClaimIds` 与 `leafSum` 的成员判定按真实事实身份而非逻辑键归并。
5. **D5 判重守卫（范围修订）**：`validateDuplicates` 跳过 `code` 为空的事实——空 code 不具备判重语义；有 code 事实的既有去重行为不变。

## 验证

- RED-first：改动前 `GoldenCasesReplayTest#project1382RealPdfShapeReplayKeepsTocLinesOutOfSchemeEvidence` + `OriginalScoreFactIdentityTest` 6/6 失败（目录行污染出第二个方案、factId 碰撞、空 code 触发 `DUPLICATE_CODE`）；sidecar 测试因 `_pdf_chapter_heading` 未定义导入失败。
- GREEN：`mvn test -Dtest='GoldenCasesReplayTest,OriginalScoreFactIdentityTest'` 33 + 5 全绿；`ArchitectureTest`/`FPJavaArchitectureTest`/`MaintainabilityArchitectureTest` 共 81 全绿。
- sidecar：全量 `pytest` 128 passed / 1 failed，该 1 例为改动前既有失败（本机 tesseract 对该扫描件段落置信度 63.9 < 阈值 70），用 pristine 代码 + pristine 测试复跑结果完全相同。
- 相邻全量 `com.xiyu.bid.scoreparse.**.*Test`：1068 tests / 7 failures，7 条均为存量失败（回退 5 个 main 文件后复跑同一子集，失败用例名与行号逐一相同）。
- 真实客户文件端到端重解析未在本任务执行（不入库、不部署测试站），验收需部署后单独进行。

## 残留风险

- **D6 未修（明确排除，需另行授权）**：`ChildAggregation.SUM` 的 `supportedByOriginalText` 用共享上限关键词（`合计最高|共.*最高|…`）扫描父项 + 全部子项拼接文本，故父项自身行含「合计最高得 3 分」时会被判 `CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE`。真实 1382 文件 B3 行即属此类 → **即使 F1/F2/F4/D5 全修复，真实文件重解析仍可能 `REVIEW_REQUIRED`**。正确修复方向（聚合证据只看父项自身行）需单独授权。
- 页级兜底归属在"表格位于章标题上方同页"时可能误归属到上一章；方向安全（误归属到非方案章 → 转人工），行为已由测试锁住。
- `_pdf_section_heading` 对 OCR textBlocks 的第二模式（评审办法家族 fullmatch）行为未变，仅新增目录行跳过；该模式对含「评分标准」表头行的页文本仍偏宽，属既有行为。

## 相关知识

- [[scoreparse-multimodal-primary]]：文字主链路、原文证据、跨表父子与发布保护契约。
- [[scoreparse-1384-docx-scheme-contract-rca]]：同类问题的 DOCX 入口版本（方案清单为空、`SOURCE_SCHEME_NOT_VERIFIED`）；本次是 PDF 入口版本，两者共同点是"入口侧缺章节锚点 + 下游资格门直接拒绝"。
