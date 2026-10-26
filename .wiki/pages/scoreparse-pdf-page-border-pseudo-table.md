---
title: PDF 全页装饰边框伪表格过滤与跨页续表合并契约
space: engineering
category: architecture
tags: [PDF, Sidecar, 跨页续表, 评分解析, 伪表格过滤]
sources:
  - document-converter-sidecar/converter.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/CellAnchoredScoreExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreSubtotalColumnPolicy.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-10-26
updated: 2026-10-26
health_checked: 2026-10-26
archive: false
---

# PDF 全页装饰边框伪表格过滤与跨页续表合并契约

## 业务痛点与技术现场

在标书 PDF 解析中，部分招标机构排版使用了全页边框装饰线（Margin Box / Page Border Frame）。pdfplumber 将其识别为覆盖整页（宽高 $\ge 90\%$）、仅 1 列的“伪表格”。
当这种伪表格插在跨页正文评分表中间时，会造成严重后果：
1. **跨页续表截断**：续表合并机制检测到前后表格列数不匹配（4列 vs 1列），导致续表在第 1 页后直接截断；
2. **正文行被吞入伪表**：正文行因 Y 坐标处于边框内，被误判为“表内行”而丢失章节和标题锚点；
3. **长文本延续行断链**：首格为空且无独立分值的描述性续行以及表尾“注：/说明：”行被续表检测拒绝。

## 核心架构契约

### 1. Sidecar 边框伪表格过滤 (`_is_pdf_page_border_table`)
- **几何规则**：表格宽 $\ge 0.90 \times \text{page.width}$ 且高 $\ge 0.90 \times \text{page.height}$，且最大列数 $\le 1$。
- **作用域**：在 `_pdf_page_tables`（返回候选表格）与 `_pdf_line_inside_table`（判断行是否在表内）中一律过滤。

### 2. 跨页续表合并宽容化 (`_is_pdf_score_table_continuation`)
- 放宽对纯文本描述行的要求：首列为空但后续列包含文本的延续行，允许并入前表。
- 放宽对表尾注释行的要求：以 `注：`、`说明：`、`需要补充的其他内容`、`附：` 开头的首列文本允许作为注释延续行并入主表。

### 3. Java 侧中括号与半开闭区间适配
- `ScoreParseConstants.BRACKET_WEIGHT_PATTERN` 统一定义全半角与中括号 `[（(\\[【] ... [）)\\]】]`。
- `CellAnchoredScoreExtractor` 支持 `[8-5）分` 等半开半闭区间，提取区间上限分值。

## 验证基线

- `document-converter-sidecar/tests/test_pdf_table_artifact.py`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/grid/CellAnchoredScoreExtractorTest.java`
- `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreSubtotalColumnPolicyTest.java`
