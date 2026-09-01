---
title: 标书质量核查分页丢失修复（\f 契约断裂 + 虚拟分页兜底 + 证据定位治理）
space: engineering
category: guide
tags: [质量核查, 分页, sidecar, 虚拟分页, 证据定位, spec049]
sources:
  - backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/DocumentContent.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SemanticPageRetriever.java
  - document-converter-sidecar/converter.py
backlinks:
  - _index
  - bid-quality-check-realization
  - score-parse-service
created: 2026-09-01
updated: 2026-09-01
---

# 标书质量核查分页丢失修复

> **一句话**：核查引擎按"物理分页"设计，但 Sidecar 输出的 markdown 从来不含 `\f`
> 分页符——整篇 134 页标书被当成 1 页处理，页数误报、证据全错位、LLM 语义项大面积
> 转人工。修复：结构化 pages 字段 + Java 虚拟分页兜底 + 证据围绕关键词定位。

## 现场（winbid-test project/1352）

134+ 页 docx 标书核查结果：`DOCUMENT_COMPLETENESS` 误报"页数少于3页"；
所有确定性证据 `pageNumber=1` 且 quote 恒为封面文本（营业执照项的证据里没有营业执照）；
`PRICE_CONSISTENCY`/`BID_OPENING_TABLE` 误报；6/7 语义项 `[LOW_CONFIDENCE] AI 返回结果异常`。

## 根因链（单一根因连锁）

```
markitdown/mammoth docx 转换不输出 \f（Word 自动分页只在渲染层）
        ↓
SidecarStreamDocumentAdapter 仅按 \f split → 整篇 = 1 页
        ↓ 三重失效
① pages<3 → 完整性误报 FAIL
② findFirstPageContaining 恒命中唯一页 + snippet 取页首 → 证据全错位
③ SemanticPageRetriever 超预算分支 selected.isEmpty() 短路 → 单页全量塞 prompt
   （60k 预算失效）→ LLM 输出异常 → 语义项集体转人工
```

## 修复分层（优先级即分页来源优先级）

1. **结构化 pages（治本，PDF）**：`/convert` 响应新增 `pages: [{pageNumber, text}]`；
   `converter.convert_pdf_document` 单次 pdfplumber 解析同时产出
   合并 markdown（scoreparse 消费，字节级兼容，golden 测试锚定）与逐页切片。
2. **虚拟分页兜底（docx）**：`DocumentContent.virtualPages`——无 `\f` 且 >4k 字符时
   按 markdown 标题切块、行累积断块、单段 6k 硬切；docx 的 `pageNumber` 语义为
   "内容块序号"（Word 自动分页物理上不可得）。
3. **护栏**：`SemanticPageRetriever` 首页超预算硬截断（非正预算优雅保底）；
   确定性证据 `snippetAround` 围绕最早命中关键词取窗；`BID_OPENING_TABLE`
   词表补齐"投标报价表"等现场用词；`PRICE_CONSISTENCY` 逐页未命中时全文跨行回退
   （DOTALL + 300 字符窗口）。

## 已知限制

- docx 证据的"第 X 页"是内容块序号而非 Word 物理页码（物理页不可得，这是上限）。
- BID_OPENING_TABLE 词表扩充后首个命中可能落在目录条目（结论方向正确，证据精度受限）。
- scoreparse 的报价表跨页合并（`_merge_split_price_tables`）只作用于 markdown 全文，
  pages 切片不做合并——两消费方契约已用 golden 测试分别锚定。

## 关联

- 架构：[[bid-quality-check-realization]]（spec 049）
- scoreparse 兼容：[[score-parse-service]]
- 教训库：`docs/lessons/lessons-learned.md` #196（跨管线隐式契约无 golden 测试）
