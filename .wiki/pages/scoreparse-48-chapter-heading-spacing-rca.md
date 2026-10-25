---
updated: 2026-10-25
health_checked: 2026-10-25
title: 项目48标书章节标题字间空格与同级截断根因分析
space: engineering
category: debugging
tags: [scoreparse, pdf, scope, scheme, spacing, diagnosis]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java
  - docs/lessons/lessons-learned.md
backlinks:
  - .wiki/pages/scoreparse-1387-chapter-list-scope-rca.md
---

# 项目48（河南神火）标书章节标题字间空格与同级截断根因分析

## 1. 现象与证据

- **现场表现**：项目 48（神火股份电子商城 2026 年电商平台供应商入围招标）多次触发评分标准解析均显示为“失败”；在 AI 评分标准解析弹窗中提示：
  - 「检测到待人工复核的候选评分标准（二、评分办法）（本方案内 0 项，合计 0 分）」
  - 「该方案包含多个物理表格但尚无可验证的方案总分，不能按表格分值相加」
  - 下方列出 37 项「技术部分方案外：无法确认属于当前评分方案」等方案外事实，无法采纳生效。
- **数据库诊断**：
  - 任务（Task 146）提取状态为 `COMPLETED`，大模型实际抽取出 37 个评分事实；
  - 但全部事实在 `score_parse_evidence` 中被判定为 `scopeStatus: OUT_OF_SCHEME`, `reason: ITEM_SCHEME_SCOPE_MISMATCH`；
  - 触发门禁 `ORIGINAL_ELIGIBLE_FACTS_MISSING` 与 `ORIGINAL_TOTAL_UNKNOWN`。

## 2. 根因分析

1. **章标题排版字间空格**：
   - 神火标书正文第四章标题为 `第四章 评 标 办 法`（字符间含有排版空格）；
   - `OriginalScoringSchemePolicy.SCHEME` 早期使用纯文字正则 `.*(?:评分办法|评审办法|评分方案|评标办法|评分标准|评审标准|综合评分法).*`，未能匹配字间空格，导致 `level = 1` 的章标题未能确立为方案根。
2. **二级小节被降级为根并遭遇同级截断**：
   - 算法向下找到二级小节 `二、评分办法`（`level = 2`），将其确立为方案根；
   - 下一个标题是 `三、评分因素及分值细则`（`level = 2`）；
   - `OriginalScoringTextScopePolicy.nextSchemeBoundary` 遇到同级且非方案根标题判定当前方案结束；
   - 导致紧随其后位于第 976 至 1050 行的大表格（技术 70 分、商务 30 分细则）被全部划为 `OUT_OF_SCHEME`。

## 3. 修复方案

1. **增强正则与去空白匹配**：
   - `OriginalScoringSchemePolicy.SCHEME` 模式拓展支持任意字间空白（`评\s*分\s*办\s*法` 等）并补充 `评标方法|评审方法`；
   - `isValidSchemeRoot`、`isSchemeTitle`、`isContractAuditTitle` 等判定方法前统一将入参标题去空白（`compact(title)`）。
2. **作用域与总分自然闭合**：
   - `第四章 评 标 办 法` 正常确立为一级方案根，覆盖全章直到第五章；
   - 表格与条款全部进入 `IN_SCHEME`，并与正文声明的 `满分为 100 分` 闭合。
