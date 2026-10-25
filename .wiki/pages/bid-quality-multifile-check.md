---
title: AI 标书质量核查多册合并（价格/商务/技术分册 + Loader 边界合并 + Sidecar 空册陷阱）
space: engineering
category: guide
tags: [质量核查, 多文件, DocumentContent, sidecar, 合并, spec047]
created: 2026-09-02
updated: 2026-10-25
health_checked: 2026-10-25
sources:
  - backend/src/main/java/com/xiyu/bid/compliance/service/ComplianceTargetLoader.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/DocumentContent.java
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java
backlinks:
  - _index
---
# AI 标书质量核查多册合并

> 真实项目常按**价格册/商务册/技术册**分三份上传投标文件，旧实现只核查"最后上传的一份"（spec 047 明确写"最新投标文件"，单份是设计简化假设），其余册完全不查但报告照常出——用户误以为都查过了。
> 修复采用第一性原理结论：**核查引擎消费的是 `DocumentContent`（文本+页+哈希）而非"文件"，合并在 Loader 边界完成，引擎/证据/报告/前端零改动**。

## 1. 合并契约（DocumentContent.merge 纯函数）

| 维度 | 规则 | 原因 |
|---|---|---|
| 册序 | id 升序（自增主键=上传顺序） | createdAt 同秒会撞序；单测实体未落库时 createdAt 为 null |
| 页码 | 跨册连续重编号 | 证据 `pageNumber` 契约不变；quote 原文仍是主锚点 |
| 全文 | `\n\n` 拼接，**不注入文件名** | 文件名含"价格/金额"等字样会误触正则规则 |
| 哈希 | 各册哈希按册序 `join("\|")` 后整体 SHA-256 | 同序确定性保缓存命中稳定；任一册重传即缓存失效 |
| 单册 | 原样返回同一实例 | 聚合哈希≠原始哈希，identity 保住既有单文件项目缓存 |
| 文件名 | 顿号连接，超 255 经 `capMergedFileName` 截断（码点安全）+"等N册" | 对齐 bid_file_name 列宽；**merge 与 trigger joinBidFileNames 必须共用同一截断函数**，否则异步 `updateFileMetadata` 覆写会绕过 trigger 侧截断导致 data-too-long |

## 2. ⚠️ Sidecar 空册陷阱（本任务评审揪出的 P0）

`SidecarStreamDocumentAdapter.convertDocument` 内部 `catch (Throwable)` **全吞异常**，失败时静默返回 `new DocumentContent(fileName, hash, "", [])`（空文本+空页），**从不抛异常**。

后果：任何"解析失败→跳过/告警"的上层设计都是不可达的死代码；扫描件/损坏册会以空册身份静默进入下游。多册场景下空册并入合并文档 = 用 2/3 内容出"合格"报告。

**正确姿势**：调用侧（`ComplianceTargetLoader.loadBidDocument`）每册解析后显式校验 `fullText().isBlank() && pages().isEmpty()` → 指名该册抛 BusinessException。**宁可整体失败，不可部分假查**。单测 mock 该适配器时禁止用 `thenThrow` 验证失败语义（假绿），必须 mock 真实失败形态（返回空册）。

> **2026-10 复核**：spec 066 已将失败语义显式化——`convertDocument` 返回 `SidecarParseOutcome` 五分类（OK/TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE，仍永不抛异常），调用侧对非 OK 的册指名抛 422（`ComplianceTargetLoader.parseFailureMessage`），「空册静默进入下游」的窗口已被分类契约关闭；§1 合并契约（`DocumentContent.merge`）不变。单测失败语义改为 mock 非 OK 的 outcome（见 [[bid-quality-check-parse-timeout-fix]]）。

## 3. 顺手清除的假查路径

`findLatestBidDocument` 旧逻辑在没有 BID 文件时**兜底取全类别最新文档**——只上传过招标文件的项目会把招标文件当投标文件核查。已删除：`findAllBidDocuments` 只认 `documentCategory='BID'` 且 fileUrl 非空（脏记录 log.warn 指名后跳过）。

**行为变更提示**：历史上从未打 BID 类目标的老项目，核查从"能查（可能查错对象）"变为 422"请先上传投标文件"。

## 4. 关键文件

- `compliance/domain/DocumentContent.java` — `merge` / `capMergedFileName` / `aggregateFileHash` 纯函数
- `compliance/service/ComplianceTargetLoader.java` — `findAllBidDocuments`（id 升序）/ `loadBidDocument`（逐册取流解析+空册拦截+合并）
- `compliance/application/BidQualityCheckTaskAppService.java` — trigger 校验列表判空；`joinBidFileNames`
- 测试：`DocumentContentMergeTest`（12 例）、`ComplianceTargetLoaderStorageDispatchTest`（13 例）

## 5. 续篇：PDF 大文件空册的根因是 OOM 不是坏文件（项目 1373，2026-09-10）

§2 的空册拦截上线后，428MB 投标 PDF 触发指名 422——文件可下载查看，但三次核查全败。查证为空册的另一条生产路径：`converter.py` 的 pdfplumber 单次 open 全量解析，内存膨胀约 7 倍，以 ~2.98GB RSS 撞 3GB 容器上限被 OOM-kill（dmesg 实证）；Java 侧 60s 超时先撞墙，重试再撞 503。

根治三件套（输出字节零变化）：Sidecar 按 10 页分块重开（`SIDECAR_PDF_CHUNK_PAGES`，峰值 O(块)，100 页 fixture 189MB→74MB）→ Java 超时按 >50MB 分档 300s（`resolveTimeoutSeconds` 纯函数，永不抛契约不变）→ 容器 3g→4g（双宿主余量已测算，见 `remote-deploy.sh` 注释）。教训：小 fixture 全绿≠大文件安全，解析库必须配峰值基准测试（`test_pdf_memory_guard.py`）。

相关：[[bid-quality-check-realization]]（spec 049 落地）、[[bid-quality-check-pagination-fix]]（\f 分页契约）
