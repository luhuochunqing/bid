---
title: 案例库应答片段摘要可读性 + 标书原文来源标题定位（WF 0915-01）
space: engineering
category: guide
tags: [案例库, knowledge_case, 摘要截断, SummaryTruncator, sourceSectionTitle, provenance, V1215, 回填脚本]
created: 2026-09-16
updated: 2026-09-17
health_checked: 2026-10-25
sources:
  - backend/src/main/java/com/xiyu/bid/casework/application/util/SummaryTruncator.java
  - backend/src/main/java/com/xiyu/bid/casework/application/MarkdownSourceSectionIndex.java
  - backend/src/main/java/com/xiyu/bid/casework/application/MarkdownHeadingParser.java
  - backend/src/main/java/com/xiyu/bid/casework/application/BidDocumentLoader.java
  - backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java
  - backend/src/main/resources/db/migration-mysql/V1215__add_source_provenance_to_knowledge_case.sql
  - scripts/backfill-knowledge-case-source-provenance.py
  - scripts/backfill_knowledge_case_source_provenance_matching.py
  - src/views/Knowledge/views/components/CaseDetailDialog.vue
backlinks:
  - _index
---
# 案例库摘要可读性 + 来源标题定位

> **结论**：`/knowledge/case` 的应答片段摘要由“硬截断一大段文本”升级为「语义截断 + 前端分段/换行/序号渲染」，并在详情中新增「原文所在标题」与定位提示；来源五字段仅在可证明时写入，跨章节冲突一律保持 NULL。

## 关键约束（后续改动必须遵守）

1. **截断语义**（`SummaryTruncator`）
   - 断点策略：取窗口内**最后一个安全断点**（段落 `\n\n` > 换行 `\n` > 句子 `。！？!?`），无断点则硬切；
   - **序号安全**：行首序号（`\d+\.`、`一、`、`①-⑩`、`（1）`/`(1)`）不得被截断成裸序号；硬切路径会回退到序号起点之前（`SEQUENCE_MARKER` 无锚定 + `cutBeforeTruncatedSequence`）；
   - 与 `common/util/StringUtils.truncate`（按字符数硬截断）**行为不同**，属必要新增（已在方法上以 `// REUSE-OK:` 标注）。
2. **来源字段唯一命中才写**（`KnowledgeCase` 五列：`source_section_title/path/document_name/page_number/section_index`）
   - Java 侧 `MarkdownSourceSectionIndex.locate`：枚举原文全部命中；跨多个章节 → `null`；命中内容等于评分项标题 → `null`；
   - Python 回填脚本同口径：跨章节冲突 → `cross_section_conflict`；短前缀（>200 字只命中前缀）与标题冒充 → `insufficient_evidence`；两者均**禁止写库**；
   - 列表接口 `KnowledgeCaseResponse` **不透出**来源字段；详情与推荐透出。
3. **原文所在标题展示规范（PR #2668 细化，2026-09-17 交互收敛）**：
   - **纯文本展示与独立操作职责分离**：详情弹窗中的「原文所在标题」仅作为线索元信息纯文本展示（不作为可点击的下载链接、不带 hover 下划线与手型指针）；原版标书查阅下载功能统一由右上角操作栏中的「标书原文」独立按钮承担，交互意图清晰单一。
   - **不展示来源文档名称**：详情弹窗仅展示「原文所在标题」与「页码」，隐藏「来源文档：xxx」，保持视觉简洁聚焦。
   - **完整保留原始章节序号**：来源标题与层级路径（`source_section_path` / `source_section_title`）必须带上标书原文原始章节序号（例如 `二、 仓库管理`、`1. 商品安全溯源`、`（一） 供货业绩`、`第六章 评标办法 > 6.3 技术评分 > 1. 纸张检测报告`），严禁剥离序号导致用户无法在原文快速定位。
4. **前端排版与分段规范**：
   - 容器样式：`.response-box` 设置 `white-space: normal; word-break: break-word;`，段落通过 `:deep(p) { margin: 0 0 10px 0; line-height: 1.7; text-align: justify; }` 提供舒适清晰的段间距。
   - 预处理管道：`splitInlineFormats` 对行内序号（多级数字、汉字大写、括号、字母、项目符号）前自动切分换行，单行表格补全分隔行，句号多句长文本智能断行，并经 `processPlainTextBlock` 转为标准 `<ol><li>` 列表与 `<p>` 段落。
5. **存量数据回填与大纲映射**：
   - 采用 `scripts/backfill-case-provenance.py` 与 `scripts/slice_provenance_results.json`，精准还原 300 条切片与 49 条项目案例（共 349 条）的大纲原始序号，测试库回填率达 100%。

## 验证证据（WF 0915-01 / PR #2668）

- 后端：ArchUnit 8/8；11 个测试类 119/119；脚本 self-test 16/16 与 6/6；四项专项（跨章节冲突/短前缀/标题冒充/硬切序号）实跑通过。
- 前端：4 个 spec 62/62 全绿；行数预算严格控制在 228 行（预算 300 行）；无新增 Hardcoded Token；生产构建与 chunk 内联检查全部通过。
- 数据库回填：测试 RDS（`winbid-01.test.rds.ehsy.com`）349 条记录来源标题与大纲完整回填，带原始序号比例 100.0%。
- 门禁通过：Pre-push Gate 门禁各项检查完全通过。
