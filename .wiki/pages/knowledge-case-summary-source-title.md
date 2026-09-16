---
title: 案例库应答片段摘要可读性 + 标书原文来源标题定位（WF 0915-01）
space: engineering
category: guide
tags: [案例库, knowledge_case, 摘要截断, SummaryTruncator, sourceSectionTitle, provenance, V1215, 回填脚本]
created: 2026-09-16
updated: 2026-09-16
health_checked: 2026-09-16
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
  - case-library-prd-refine
  - case-source-actions-and-import-pitfalls
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
3. **迁移**：`V1215__add_source_provenance_to_knowledge_case`（五列可空、无索引、不改旧列）+ `U1215` 逆序回滚；`docs/generated/db-schema.md` 已由脚本刷新。**V1215 版本号不得复用**（曾有跑偏分支新建冲突的 `V1215__add_bid_section_title...`，已作废）。
4. **凭证**：导入/回填脚本一律 `--db-pass` 或 `XIYU_DB_PASS`，无明文默认；缺凭证安全失败且不回显。
5. **前端渲染**：`CaseDetailDialog` 的 `preprocessPlainText` 先 `escapeHtml` 再插 `<br>`/`<ol><li>`；markdown 结构行（表格/标题/引用/围栏代码/无序列表）**整块透传**不插 `<br>`；序号前缀剥离避免 `<ol>` 双编号；安全渲染仍走 `markdownSanitizer` 白名单（未改）。

## 验证证据（WF 0915-01）

- 后端：ArchUnit 8/8；11 个测试类 119/119；脚本 self-test 16/16 与 6/6；四项专项（跨章节冲突/短前缀/标题冒充/硬切序号）实跑通过。
- 前端：4 个 spec 50/50；`e2e/knowledge-case-source-title.spec.js` 静态 6 用例；AC-01~08、S1~S5 与来源标题 7 项断言全绿。
- 评审：两份独立复审 0 阻断（后端 3 低、前端 3 中/4 低均为 tech-debt）。
- 未覆盖（合并前置）：开发宿主浏览器 E2E、真实 MySQL migrate/回滚、真实数据回填 `--dry-run/--apply`、真实 HTTP 权限、五字段真实 DB 持久化。
