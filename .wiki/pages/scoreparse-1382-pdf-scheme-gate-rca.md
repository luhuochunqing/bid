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

1. **F1 sidecar 章节归属**：新增 `_pdf_chapter_heading()`（章级标题识别，拒绝目录行）与 `_pdf_page_chapter_heading_positions()`（利用 `extract_text_lines()` 的行几何）；页面循环改用 `find_tables()` 拿到每张表的 `bbox.top`，按"最近前置章标题"逐表写入 `sourceSection`，同页无锚点的表格继承上一页的章。继承由 `_pdf_page_unresolved_section_break_tops()` + `_pdf_table_section()` 约束：本页出现章级行（`_PDF_SECTION_BREAK`：`第X章 / 第X部分 / 第X编 / 第X篇`）却无法转成锚点时**掐断继承**——否则该页表格会被挂到上一章，而后端对 `sourceSection` 命中方案的表格判 `IN_SCHEME` 并据此采纳，等于静默采纳外章评分项；掐断只是转为人工复核，方向安全。掐断**随页传递**（页循环的 `current_section_cut` → `_pdf_table_section()` 的 `inherited_cut`）：不可命名的章一直延续到下一个章标题为止——第 4 轮自审发现，若断点只在本页生效，下一页的续表会因"继承到空串且自身没有断点"被判定为可桥接并合并回前一章锚点（F-1 的错误采纳路径从同页挪到了跨页），故断点必须活到下一个锚点。**第 5 轮修正了"活到下一个锚点"的判定处**：页末状态原先写的是"本页出现过断点就清空"，于是同页**先断点、后锚点**时（第四章已经被点名）页末仍按断点清空，下一页无标题的合法续表继承到空串 → 丢归属转人工（第 4 轮评审 F-1；探针实测该续表 `section='' cut=True` 且不并表）。现在逐表查询与页末状态**共用同一处定义** `_pdf_last_chapter_event()`（"最后一个章级事件"，同行并列仍取断点），页末取本页最后一个章级事件：是锚点则命名该章并清除 cut，是断点才置空并保留 cut。这套"两处各写一遍"正是缺陷来源，合并不是为了省代码而是为了让两条规则不可能再漂移；`inherited_cut` 同时改为**仅关键字、无默认值**（第 4 轮评审 F-2：漏传会静默重新打开上述错误采纳路径，不报错）。**第 4 轮把掐断改为按位置生效**：归属交给"最近的在前章级事件"决定（可能是锚点，也可能是断点），只有**断点下方**的表格置空并带 `sectionBoundaryCut` 标记，断点上方的表格仍继承上一页的章——页级一刀切会把断点上方的表格也清空，白白丢掉本可成立的归属。锚点与断点**同一行**（`top` 相等）时顺序不可判，取断点（`>=`）——与"宁可转人工也不冒错误采纳的险"一贯，代价是并列时该表及其后本页表格转人工。`_merge_pdf_score_table_continuations()` 现在三处不合并：两表章节均已知且不一致、**任一侧带 `sectionBoundaryCut`**（合并会让续行沿用前一张表的 `sourceSection`，等于把章节被清空的外章行重新挂回本方案——第 3 轮评审 F-1 指出的错误采纳路径；`_same_or_unknown_section` 容许空章节桥接是为"续页重复标题是例外而非常态"，所以"边界已断开"必须是随表传递的独立信号，不能靠空字符串表达）。`PDF_TABLE_EXTRACTION_VERSION` 升至 `pdf-table-v2`（消费方按版本区分语义）。**第 2 轮评审 F-1 补全**：守卫式 `_PDF_SECTION_BREAK` 与锚点式 `_PDF_CHAPTER_HEADING` 是两个不同谓词，前者的识别面必须**严格宽于**后者。第 1 轮把锚点式的窄字符集（`[一二三四五六七八九十百\d]` + 连续的「第X章」）照抄给守卫，差集因此退化成"窄字符集之内的章标题"：「第3 章」（数字与「章」间有空白）、「第 十一 章」、「第一百零一章」（"零"不在字符集内）三个判定全不命中 → 仍然继承上一章、外章表格被错误采纳。补全后守卫三点放宽：数字类放宽到常见中文数字（含 `零/〇/两` 与大写）与各类数字；容器词扩到 `章/部分/编/篇/卷/册`；并对**去空白形态**（`_pdf_heading_form`）匹配（PDF 排版常把「第 三 章」提取成带空格的行）。**第 3 轮评审 F-2 补全**：守卫数字类**不设长度上限**（原量词 `{1,6}` 让「第一千二百三十四章」= 7 个数字字符既不成锚点也不掐断 → 漏判窗口），并把「亿」补进字符集；判据是"中文数字可能用到的字符"，不是"我们想象得到的章号规模"。**第 3 轮评审 F-3 补全（回退第 2 轮的收紧）**：锚点数字类恢复 Unicode `\d`。第 2 轮曾按"后端 `SOURCE_HEADING` 只认 ASCII"收紧成 `0-9` 并声称"零功能变化"——**该主张不成立**：后端把方案标题与 `sourceSection` 做**字符串**比对，而方案标题既可由正文标题建立（通用 Markdown 标题分支 `^\s*#{1,6}\s*(.+?)\s*$` 不看数字形态），也可由表格自身的 `sourceSection` 依 `isValidSchemeRoot` 建立（`schemeTitleForTable`）；收紧后「第３章 评审办法」这类表格的 `sourceSection` 变空、`!section.isBlank()` 分支不成立 → 丢掉本来成立的方案归属。锚点的职责是**原样**写出文档自己的章标题，不是替后端猜它能解析什么；该跨层契约由 `OriginalScoringContextThreeDefectsTest.fullWidthChapterNumberSectionStillOwnsItsTable` 锁住。**第 3 轮评审 F-4（有意保留的过判面）**：守卫对整行 `match` 且无行长上限，正文里以「第X部分/第X卷…」开头的普通句子也会被当成新章信号 → 该行**下方**的表格转人工；第 4 轮已把掐断收窄到"断点下方"，上方表格不再受牵连，未再加行长/字体启发式——任何"短行/页首"式收窄都会重新打开漏判窗口（漏判 = 错误采纳，比过判 = 转人工危险）。第 5 轮又把**页末状态**从"本页出现过断点"改为"本页最后一个章级事件"（见上），故"断点之后的后续页一律转人工"这一残留窗口收窄为"页内最后一个章级事件是断点时才转人工"。
2. **F2 目录行排除**：`headings()` 在两种标题模式之后统一执行目录行检查，命中即整行不作标题。目录行有两类形状：点线 + 尾页码（`TOC_TRAILER`），以及**无点线、页码排在右侧独立列**（真实 PDF 极常见，由 `TOC_ENTRY` 在此之上放宽 `|\s\d{1,3}\s*$`）。两常量故意不等：`schemeCore()` 比较前只剥 `TOC_TRAILER`（点线形），若把裸尾号也纳入剥离，两个只差尾号的正文标题会被并成同一方案，把另一章评分项并进来——误判方向比漏检危险。该不等关系由 `trailingNumberOnABodyHeadingIsNeverStrippedForComparison` 锁住，且 `TOC_ENTRY` 由 `TOC_TRAILER.pattern()` 拼接而成，保证"跳过 ⊇ 剥离"不靠人记住。
3. **F3 真实形状金标**：`case-project-1382-pdf-real-shape.{md,expected.json}` 复刻真实 PDF 形状（目录点线行、纯文本章标题、多表同方案、跨章非评分表），回放用例断言 1 个方案、无 `ORIGINAL_SCHEME_AMBIGUOUS`、16 条合格事实、叶子合计 100、`autoApprovable`。
4. **F4 事实身份**：`modelFactId()` 判别因子增加 `dimension + weight + quotedText`（NUL 分隔，与既有 `logicalKey` 同风格）；`OriginalScoreEvidencePolicy.verifyModel()` 的 `ScoreEvidenceClaim.id`、父子图（`parentByFact`）、`parentKeys`、`eligibleClaimIds`、`leafSum` 全部由 `logicalKey(...)` 改为 `modelFactId(f)`。父图尤其关键：按 `logicalKey` 建键时空 code 兄弟互相覆盖父链接，被覆盖的父项失去子项、自身反被当叶子计入小计 → `TABLE_SUBTOTAL_MISMATCH`（或反向放过）。`TextPrimaryScoreFactMapper` 的逐条问题过滤同步改为按 `modelFactId` 匹配，否则来源侧问题挂不回事实、`ScoreEvidence.explanation` 里的诊断信息整体丢失（本类自有 `of()` 问题用 `sectionTitle+"/"+code`，比事实身份粗，故意不匹配）。**第 2 轮评审 F-2 补全**：判别因子再加入**父项引用** `parentCode`（按 `MultimodalFactParentCodeNormalizer.compact` 去空白，与父项查找的比较形态一致）——空 code 兄弟若只在"父亲是谁"上不同（同维度、同分值、同引文，父项分别是 `1.1`/`1.2`），缺该因子就会撞成同一 id，父图后写覆盖前写、被覆盖的父项变叶子。残余撞键（连父项引用也相同、只有来源单元格不同）不再靠加因子消除：来源定位是模型产出、跨次重解析不稳定，写进身份会让身份在两次运行间漂移；改为**显式拒绝**——`verifyModel` 统计 id 重复，命中即追加 `ORIGINAL_FACT_IDENTITY_AMBIGUOUS` 阻塞问题，`publishable()` 为假 → 转人工复核（`MultimodalScoreFactValidator` 会把该问题映射成 violation 供界面展示）。合并两条不可区分的事实会让父图错配，任选其一更糟，拒绝是唯一的全量安全动作。
5. **D5 判重守卫（范围修订）**：`validateDuplicates` 跳过 `code` 为空的事实——空 code 不具备判重语义；有 code 事实的既有去重行为不变。

## 验证

- RED-first（第 1 轮，pristine base 源码 + 本分支测试，`mvn -o test -Dtest=OriginalScoreFactIdentityTest,GoldenCasesReplayTest,TextPrimaryScoreFactMapperTest`）→ exit 1，`48 run / 13 failures + 1 error`：`OriginalScoreFactIdentityTest` 11/11（目录行成为方案根、扩展点线字符集失守、裸尾号目录行、factId 碰撞、`DUPLICATE_CODE`、资格串台、方案比较不对称、空 code 兄弟父链接互相覆盖）、`GoldenCasesReplayTest` 2（目录行污染出第二个方案、16 条事实的 modelFactId 不唯一）、`TextPrimaryScoreFactMapperTest` 1 error（来源侧问题挂不回事实）。
- sidecar RED（第 1 轮）：改动前 `converter.py` 无 `_pdf_chapter_heading` / `_pdf_page_has_unresolved_section_break` / `_pdf_page_chapter_heading_positions` → 测试导入失败与守卫缺失（无守卫时三页表格均继承「第三章 评审办法」，有守卫时 `[(1,'第三章 评审办法'),(2,''),(3,'')]`）。
- **第 3 轮 RED（第 2 轮评审 F-1/F-2）**：源码冻结在 `8cbfefa99`（scratch worktree `/private/tmp/red-1382-r2`）+ 本轮测试 → 后端 `OriginalScoreFactIdentityTest` exit 1，`13 run / 2 failures`：`blankCodeSiblingsDifferingOnlyByParentKeepDistinctIdentities:174 expected: not equal but was: <fact-30f3b832-cefc-3fd1-85df-ba198375c6ca>`（父项因子缺失导致撞键）、`factsThatShareOneIdentityAreNeverAutoApproved:207 expected: <false> but was: <true>`（撞键未被拒绝，文档仍可发布）；sidecar `7 failed / 20 passed`，除既有 tesseract 环境失败外恰为本轮 6 个新用例（break 判定表、4 个继承形状参数化 `第3 章 / 第一百零一章 / 第３章 / 第五卷`、锚点不得越层产出）。
- **第 4 轮 RED（第 3 轮评审 F-1/F-2/F-3）**：把 pre-fix 源码（`fa457b4f0` 的 `converter.py`）放进 scratch 目录 `/tmp/red-1382-r4` + 本轮测试。① 探针（同一脚本换源码目录跑两遍，`/tmp/red-1382-r4/probe.py`）在 pre-fix 上输出：跨断点续表 → `(1, ['第三章 评审办法'])`（1 张表：断点清空的表格被并回前一章并继承章节 → F-1 的错误采纳路径）；断点上方的表格 → `(2, ['第三章 评审办法', ''])`（页级一刀切把上方表格也清空）；`第一千二百三十四章` 守卫 → `False`（F-2 漏判）；全角锚点 → `''`（F-3 丢归属）。修复后同脚本输出 `(2, ['第三章 评审办法', ''])`、`(2, ['第三章 评审办法', '第三章 评审办法'])`、`[100.0]`、`'第３章 合同履约考核'`。② 本轮新测试在 pre-fix 上连收集都过不去（`ImportError: _pdf_page_unresolved_section_break_tops / _pdf_table_section`），故行为层 RED 以上述探针为准。
- 最终 GREEN（第 4 轮，`mvn -o test -Dtest='GoldenCasesReplayTest,OriginalScoreFactIdentityTest,TextPrimaryScoreFactMapperTest,OriginalScoringContextThreeDefectsTest,ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest'`）→ exit 0，`111 tests / 0 failures`；相比第 3 轮的 93 例多出的 18 例为 `OriginalScoringContextThreeDefectsTest`（含本轮新增的跨层契约测试 `fullWidthChapterNumberSectionStillOwnsItsTable`）。
- sidecar 全量 `pytest -q` → exit 1，`151 passed / 1 failed`（第 3 轮为 146 passed；第 4 轮的 5 个新增/改名用例 + 1 个跨页端到端用例）；该 1 例为**改动前既有**的环境失败（`test_pdf_artifact_emits_ocr_text_with_physical_page_and_region`：本机 tesseract 对该扫描件段落置信度 63.9 < 阈值 70），用同解释器 + pristine `converter.py` 复跑得到完全相同的失败。定向 `tests/test_pdf_table_artifact.py tests/test_extract_tables_v2.py` → `54 passed / 1 failed`（同一条环境失败）。
- 相邻全量 `mvn -o test -Dtest='com.xiyu.bid.scoreparse.**.*Test'` → exit 1，`1079 tests / 7 failures / 2 skipped`（比第 3 轮多 1 例，即本轮新增的后端契约测试）；7 条为存量失败，在 pristine base（`/tmp/xiyu-1382-base`，base `26571d030`）上跑同一子集得到 `24 run / 7 failures`，类名与行号逐一相同（`PdfEvidencePipelineTest:196/76`、`ScorePartialOverwritePersistenceTest:341/315`、`StructuredTableExtractionMarkCompletedTest:140/50/87`）。本轮**未改后端生产代码**（只加测试），失败集合与第 3 轮完全一致。
- **回归纠偏（S5 base 对照发现）**：收口 `_pdf_page_tables()` 时一度把整段回退删除，导致既有测试 `test_extract_tables_v2.py::test_pdf_continuation_cells_expose_physical_page`（替身只实现 `extract_tables()`）由绿转红；pristine 对照确认是新引入回归后恢复「缺 finder」分支，现两侧各有测试。
- **第 5 轮 RED/GREEN（第 4 轮评审 F-1 的页末事件顺序）**：探针 `/tmp/red-1382-r4-pageend/probe_page_end_event_order.py`（p1 = 第三章 + 带表头评分表；p2 = 页首断点「第三部分 合同履约考核」→ 其后锚点「第四章 评分办法」→ 第四章带表头评分表；p3 = 第四章无表头的续表形状）。对 pre-fix 源码（HEAD `3df7859fe`）→ p3 `section='' cut=True`、产物 **3** 张表、该表未并表（RED）；对本轮修复后的树 → p3 `section='第四章 评分办法' cut=False`、产物 **2** 张表、第四章那张表合并为 **3** 行（GREEN）。sidecar 全量 `pytest -q` → exit 1，`153 passed / 1 failed`（比第 4 轮的 151 多 2 例，即本轮新增的下列两个用例；唯一失败仍是既有 tesseract 环境失败）；定向 `tests/test_pdf_table_artifact.py tests/test_extract_tables_v2.py` → `56 passed / 1 failed`（同一条）。**本轮未改后端**（只改 sidecar），后端数字沿用第 4 轮。
- 真实客户文件（`办公用品.pdf`）端到端重解析未在本任务执行（不入库、不部署测试站），验收需部署后单独进行。

## 残留风险

- **D6 未修（明确排除，需另行授权）**：`ChildAggregation.SUM` 的 `supportedByOriginalText` 用共享上限关键词（`合计最高|共.*最高|…`）扫描父项 + 全部子项拼接文本，故父项自身行含「合计最高得 3 分」时会被判 `CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE`。真实 1382 文件 B3 行即属此类 → **即使 F1/F2/F4/D5 全修复，真实文件重解析仍可能 `REVIEW_REQUIRED`**。正确修复方向（聚合证据只看父项自身行）需单独授权。
- **F1 的覆盖边界**：金标回放的 `section.sourceSection` 来自 `expected.json` 夹具，不经过 sidecar，因此**金标无法发现 F1 的章节归属错误**；F1 只由 sidecar 合成 PDF 测试覆盖（`_make_sectioned_pdf` 绘制线框表 + 章标题 + 目录页，逐表断言 `sourceSection` 与页码）。
- **页级兜底归属已删除；表格抽取回退按"契约是否可达"收口**：原"`extract_text_lines()` 不可用时退化为页级首个章标题"的路径被删——页内首标题忽略表格与标题的相对位置，且会把"无几何"页的所有表挂到本页首章，方向不安全；现在无几何的页不产生锚点，也不再产出"本页首章"式的兜底归属。**注意这一条不是"转人工"**（第 4 轮复核时发现本文此前把方向写反了）：`_pdf_page_lines()` 在 `extract_text_lines()` 缺失或抛错时返回 `[]`，于是这类页**既无锚点也无断点**（`break_tops` 为空 → 页级守卫不会开火），`_pdf_table_section(..., table_top=None, inherited=current_section)` 落到"无锚点"分支并**继承 `current_section`**——即上一页最后一章，或"尚未遇到任何章"时的空串。也就是说：**文本行抽取失败会退化为继承上一章，而不是掐断**，这是本轮之后残留的最窄一处方向不安全边界（页级兜底归属删除后，跨页继承只剩这一条来源）。边界之所以窄：继承起点必须是"此前成功归属过的章"，故错误上限是"把某章后续本已同章的表格继续算作该章"；真正的危险形态（跨到**外章**）仍由 `_PDF_SECTION_BREAK` 守卫拦下，只是拦不拦得动取决于该行能不能被抽出来。`_pdf_page_tables()` 同步收口为两条分支：**没有 finder 属性**（测试替身、以及任何无 `find_tables` 的 pdfplumber）→ 仍返回表格但 `top=None`（缺坐标，不是失败路径）；**finder 存在**→ 直接调用且**不再吞异常**（原先 `except Exception: found_tables = []` + "finder 返回空则回退 `extract_tables()`" 会把抽取失败伪装成"本页无表格"，静默丢掉整页评分项）。收口过程由 S5 的 base 对照实验纠偏一次：最初把整个回退删掉时，既有测试 `test_extract_tables_v2.py::test_pdf_continuation_cells_expose_physical_page`（替身只有 `extract_tables()`）由绿转红，对照 pristine 代码确认属新引入回归 → 恢复为上述"缺 finder"分支，并补 `test_pdf_page_tables_without_a_finder_keep_tables_without_geometry` 与 `test_pdf_page_tables_propagates_table_finder_failure` 两侧锁住。
- **有意偏向（两侧固化）**：标题以裸 1–3 位数字收尾的正文行（如「第三章 附件 3」）当目录行拒掉 → 该章转人工；点线结尾但无页码的行同样不认（页码 `\d{0,4}` 允许缺省）。换取的是目录行不再成为方案根造成的文档级方案歧义。
- **继承守卫的覆盖边界（第 2 轮评审后收窄）**：守卫的识别面已**严格宽于**锚点式——数字类覆盖常见中文数字（含 `零/〇/两` 与大写）与各类数字、容器词覆盖 `章/部分/编/篇/卷/册`、并按去空白形态匹配，故凡"看起来是 `第X<容器>` 的新一章"必掐断继承。剩下的窗口只有**完全不含「第X<容器>」**的章级编号风格（例如以「4 评审办法」「四、评审办法」编号）：该行既不转锚点也不触发掐断 → 仍会继承上一章的 `sourceSection`。当前判断：这类风格下全文通常无任何锚点（`_PDF_CHAPTER_HEADING` 同样不认），继承链起点为空 → 全部转人工，窗口极窄；未再扩宽是因为扩宽到"任意 `第X…` 行"会把页首的「第一节 / 第十条」（章内层级）误判为分节，反而丢掉大量合法的延续页归属。
- **`modelFactId` 的近似碰撞窗口（已加显式守卫）**：判别因子的并集为「方案 + 状态 + 章节 + code + dimension + 分值 + 引文 + 父项引用」。两条在这些维度上**完全一致**的兄弟行仍共享同一 id，但不再被静默归并——`verifyModel` 检测到 id 重复即追加 `ORIGINAL_FACT_IDENTITY_AMBIGUOUS` 阻塞问题（`publishable()` 为假）→ 转人工复核。方向安全，且不依赖"能否穷举出所有撞键来源"。
- **OCR 路径的连带变化（需知情）**：`_PDF_TOC_LEADER` 被 sidecar 两条路径共用（PDF 表格路径 + OCR textBlocks 路径），放宽后 `_pdf_section_heading` 也不再接受"裸尾页码"行作章锚点。方向安全（漏锚点 → 转人工）且与表格路径口径一致，但这是 D6 排除范围（扫描件路径）之外的一处连带行为变化，已由 `test_pdf_section_heading_rejects_toc_lines_without_dot_leaders` 固化。
- `_pdf_section_heading` 第二模式（评审办法家族 fullmatch）本身仍偏宽（页文本含「评分标准」表头行即命中），属既有行为，本次未改。
- **`sectionBoundaryCut` 是 sidecar 内部信号，产物形状未变（第 4 轮新增）**：它只随 `extracted_tables` 在页循环与合并步骤之间传递，落到 `DocumentArtifact` 的构建器按显式键取值，故**不会**出现在产物 JSON 里，`PDF_TABLE_EXTRACTION_VERSION` 保持 `pdf-table-v2`。代价是：**金标回放（`expected.json` 夹具）不经过 sidecar**，F-1 的合并语义（任一侧带断点即不合并）只能由 sidecar 测试覆盖——与「F1 的覆盖边界」同因，不是新增缺口，但两条一起说明"金标绿"不等于"归属与合并全对"。
- **F-3 的跨层契约是"原样透传 + 字符串比对"，两侧都能单方面破坏（第 4 轮新增）**：sidecar 承诺把文档自己的章标题**逐字**写进 `sourceSection`，后端承诺按字符串相等建立归属（`tableBelongsTo` / `schemeTitleForTable`）。本次已用 `fullWidthChapterNumberSectionStillOwnsItsTable` 在后端锁住"全角章标题仍能建立并拥有方案"，但**没有**任何测试能把两侧契约一起焊死（sidecar 侧不依赖后端）。若将来任一侧把比较改成归一化（全角→半角、中文数字→阿拉伯数字）而另一侧不做，就会出现"锚点写了但认不出"或"认出了但两份标题不再相等"的静默退化。**判据**：改 `_PDF_CHAPTER_HEADING` 的字符类或后端的方案标题比较时，必须同时看这张表。

## 相关知识

- [[scoreparse-multimodal-primary]]：文字主链路、原文证据、跨表父子与发布保护契约。
- [[scoreparse-1384-docx-scheme-contract-rca]]：同类问题的 DOCX 入口版本（方案清单为空、`SOURCE_SCHEME_NOT_VERIFIED`）；本次是 PDF 入口版本，两者共同点是"入口侧缺章节锚点 + 下游资格门直接拒绝"。
