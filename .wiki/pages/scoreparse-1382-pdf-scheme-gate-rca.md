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

1. **F1 sidecar 章节归属**：新增 `_pdf_chapter_heading()`（章级标题识别，拒绝目录行）与 `_pdf_page_chapter_heading_positions()`（利用 `extract_text_lines()` 的行几何）；页面循环改用 `find_tables()` 拿到每张表的 `bbox.top`，按"最近前置章标题"逐表写入 `sourceSection`，同页无锚点的表格继承上一页的章。继承受 `_pdf_page_has_unresolved_section_break()` 约束：本页出现章级行（`_PDF_SECTION_BREAK`：`第X章 / 第X部分 / 第X编 / 第X篇`）却无法转成锚点时**掐断继承**（`current_section` 置空）——否则该页表格会被挂到上一章，而后端对 `sourceSection` 命中方案的表格判 `IN_SCHEME` 并据此采纳，等于静默采纳外章评分项；掐断只是转为人工复核，方向安全。`_merge_pdf_score_table_continuations()` 在两表章节均已知且不一致时不再合并。`PDF_TABLE_EXTRACTION_VERSION` 升至 `pdf-table-v2`（消费方按版本区分语义）。
2. **F2 目录行排除**：`headings()` 在两种标题模式之后统一执行目录行检查，命中即整行不作标题。目录行有两类形状：点线 + 尾页码（`TOC_TRAILER`），以及**无点线、页码排在右侧独立列**（真实 PDF 极常见，由 `TOC_ENTRY` 在此之上放宽 `|\s\d{1,3}\s*$`）。两常量故意不等：`schemeCore()` 比较前只剥 `TOC_TRAILER`（点线形），若把裸尾号也纳入剥离，两个只差尾号的正文标题会被并成同一方案，把另一章评分项并进来——误判方向比漏检危险。该不等关系由 `trailingNumberOnABodyHeadingIsNeverStrippedForComparison` 锁住，且 `TOC_ENTRY` 由 `TOC_TRAILER.pattern()` 拼接而成，保证"跳过 ⊇ 剥离"不靠人记住。
3. **F3 真实形状金标**：`case-project-1382-pdf-real-shape.{md,expected.json}` 复刻真实 PDF 形状（目录点线行、纯文本章标题、多表同方案、跨章非评分表），回放用例断言 1 个方案、无 `ORIGINAL_SCHEME_AMBIGUOUS`、16 条合格事实、叶子合计 100、`autoApprovable`。
4. **F4 事实身份**：`modelFactId()` 判别因子增加 `dimension + weight + quotedText`（NUL 分隔，与既有 `logicalKey` 同风格）；`OriginalScoreEvidencePolicy.verifyModel()` 的 `ScoreEvidenceClaim.id`、父子图（`parentByFact`）、`parentKeys`、`eligibleClaimIds`、`leafSum` 全部由 `logicalKey(...)` 改为 `modelFactId(f)`。父图尤其关键：按 `logicalKey` 建键时空 code 兄弟互相覆盖父链接，被覆盖的父项失去子项、自身反被当叶子计入小计 → `TABLE_SUBTOTAL_MISMATCH`（或反向放过）。`TextPrimaryScoreFactMapper` 的逐条问题过滤同步改为按 `modelFactId` 匹配，否则来源侧问题挂不回事实、`ScoreEvidence.explanation` 里的诊断信息整体丢失（本类自有 `of()` 问题用 `sectionTitle+"/"+code`，比事实身份粗，故意不匹配）。
5. **D5 判重守卫（范围修订）**：`validateDuplicates` 跳过 `code` 为空的事实——空 code 不具备判重语义；有 code 事实的既有去重行为不变。

## 验证

- RED-first：改动前 `GoldenCasesReplayTest#project1382RealPdfShapeReplayKeepsTocLinesOutOfSchemeEvidence` + `OriginalScoreFactIdentityTest` 6/6 失败（目录行污染出第二个方案、factId 碰撞、空 code 触发 `DUPLICATE_CODE`）；sidecar 测试因 `_pdf_chapter_heading` 未定义导入失败。
- GREEN：`mvn test -Dtest='GoldenCasesReplayTest,OriginalScoreFactIdentityTest'` 33 + 5 全绿；`ArchitectureTest`/`FPJavaArchitectureTest`/`MaintainabilityArchitectureTest` 共 81 全绿。
- sidecar：全量 `pytest` 128 passed / 1 failed，该 1 例为改动前既有失败（本机 tesseract 对该扫描件段落置信度 63.9 < 阈值 70），用 pristine 代码 + pristine 测试复跑结果完全相同。
- 相邻全量 `com.xiyu.bid.scoreparse.**.*Test`：1068 tests / 7 failures，7 条均为存量失败（回退 5 个 main 文件后复跑同一子集，失败用例名与行号逐一相同）。
- 真实客户文件端到端重解析未在本任务执行（不入库、不部署测试站），验收需部署后单独进行。

## 残留风险

- **D6 未修（明确排除，需另行授权）**：`ChildAggregation.SUM` 的 `supportedByOriginalText` 用共享上限关键词（`合计最高|共.*最高|…`）扫描父项 + 全部子项拼接文本，故父项自身行含「合计最高得 3 分」时会被判 `CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE`。真实 1382 文件 B3 行即属此类 → **即使 F1/F2/F4/D5 全修复，真实文件重解析仍可能 `REVIEW_REQUIRED`**。正确修复方向（聚合证据只看父项自身行）需单独授权。
- **F1 的覆盖边界**：金标回放的 `section.sourceSection` 来自 `expected.json` 夹具，不经过 sidecar，因此**金标无法发现 F1 的章节归属错误**；F1 只由 sidecar 合成 PDF 测试覆盖（`_make_sectioned_pdf` 绘制线框表 + 章标题 + 目录页，逐表断言 `sourceSection` 与页码）。
- **页级兜底归属已删除；表格抽取回退按"契约是否可达"收口**：原"`extract_text_lines()` 不可用时退化为页级首个章标题"的路径被删——页内首标题忽略表格与标题的相对位置，且会把"无几何"页的所有表挂到本页首章，方向不安全；现在无几何的页不产生锚点，表格 `sourceSection` 为空 → 该表事实转人工复核。`_pdf_page_tables()` 同步收口为两条分支：**没有 finder 属性**（测试替身、以及任何无 `find_tables` 的 pdfplumber）→ 仍返回表格但 `top=None`（缺坐标，不是失败路径）；**finder 存在**→ 直接调用且**不再吞异常**（原先 `except Exception: found_tables = []` + "finder 返回空则回退 `extract_tables()`" 会把抽取失败伪装成"本页无表格"，静默丢掉整页评分项）。收口过程由 S5 的 base 对照实验纠偏一次：最初把整个回退删掉时，既有测试 `test_extract_tables_v2.py::test_pdf_continuation_cells_expose_physical_page`（替身只有 `extract_tables()`）由绿转红，对照 pristine 代码确认属新引入回归 → 恢复为上述"缺 finder"分支，并补 `test_pdf_page_tables_without_a_finder_keep_tables_without_geometry` 与 `test_pdf_page_tables_propagates_table_finder_failure` 两侧锁住。
- **有意偏向（两侧固化）**：标题以裸 1–3 位数字收尾的正文行（如「第三章 附件 3」）当目录行拒掉 → 该章转人工；点线结尾但无页码的行同样不认（页码 `\d{0,4}` 允许缺省）。换取的是目录行不再成为方案根造成的文档级方案歧义。
- **继承守卫的覆盖边界**：`_PDF_SECTION_BREAK` 认「第X章 / 第X部分 / 第X编 / 第X篇」。若某文档的章级标题**完全不含「第X<容器>」**（例如以「4 评审办法」「四、评审办法」编号），该行既不转锚点也不触发掐断 → 仍会继承上一章的 `sourceSection`，存在把非方案章表格挂进上一方案的可能。当前判断：这类编号风格下全文通常无任何锚点（`_PDF_CHAPTER_HEADING` 同样不认），继承链起点为空 → 全部转人工，窗口极窄；未再扩宽是因为扩宽到"任意 `第X…` 行"会把页首的「第一节 / 第十条」（章内层级）误判为分节，反而丢掉大量合法的延续页归属。
- **`modelFactId` 的近似碰撞窗口**：判别因子的并集为「方案 + 状态 + 章节 + code + dimension + 分值 + 引文」。两条在这些维度上**完全一致**的兄弟行仍共享同一 id（即语义上不可区分的重复行）→ 计分时被归并 → `WEIGHT_SUM_MISMATCH` → 转人工（方向安全，不会虚高采纳）。
- **OCR 路径的连带变化（需知情）**：`_PDF_TOC_LEADER` 被 sidecar 两条路径共用（PDF 表格路径 + OCR textBlocks 路径），放宽后 `_pdf_section_heading` 也不再接受"裸尾页码"行作章锚点。方向安全（漏锚点 → 转人工）且与表格路径口径一致，但这是 D6 排除范围（扫描件路径）之外的一处连带行为变化，已由 `test_pdf_section_heading_rejects_toc_lines_without_dot_leaders` 固化。
- `_pdf_section_heading` 第二模式（评审办法家族 fullmatch）本身仍偏宽（页文本含「评分标准」表头行即命中），属既有行为，本次未改。

## 相关知识

- [[scoreparse-multimodal-primary]]：文字主链路、原文证据、跨表父子与发布保护契约。
- [[scoreparse-1384-docx-scheme-contract-rca]]：同类问题的 DOCX 入口版本（方案清单为空、`SOURCE_SCHEME_NOT_VERIFIED`）；本次是 PDF 入口版本，两者共同点是"入口侧缺章节锚点 + 下游资格门直接拒绝"。
