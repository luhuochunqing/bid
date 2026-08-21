# Implementation Plan: Docx 媒体流前置瘦身与超大标书秒级解析优化 (053)

## 1. 架构设计与改动范围

```
[原始超大标书 1.66GB]
        │
        ▼ (按原文件全量计算)
  SHA-256 哈希计算 ───► DocumentContent.fileHash
        │
        ▼ (判断: 是 .docx 且 > 20MB)
┌──────────────────────────────────────┐
│ DocxMediaFilter 流式过滤              │
│ 剔除 word/media/* 资源                │
└──────────────────────────────────────┘
        │
        ▼ (生成临时精简 docx, 体积 ~1.5MB, 耗时 < 0.5s)
[精简 docx 临时文件]
        │
        ▼ (HTTP Multipart POST, Payload < 2MB, 耗时 < 10ms)
Sidecar /convert ───► 解析 Markdown
        │
        ▼
[清理临时精简文件 (finally)]
```

## 2. 详细文件变更清单

1. **新建工具类**：`backend/src/main/java/com/xiyu/bid/compliance/service/DocxMediaFilter.java`
   - 实现纯流式过滤 `word/media/`；
   - 包含 4 行 doc governance 注释规范；
   - 纯 JDK 实现（`ZipInputStream` + `ZipOutputStream`），无任何外部三方依赖。
2. **改造适配器**：`backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java`
   - 在发送 HTTP 请求前，基于原文件计算 SHA-256；
   - 若符合条件，调用 `DocxMediaFilter.filterMediaToTempFile` 生成临时精简文件；
   - 将精简文件以 multipart 发送给 Sidecar；
   - `finally` 块可靠删除临时文件。
3. **单元测试**：`backend/src/test/java/com/xiyu/bid/compliance/service/DocxMediaFilterTest.java`
   - 测试含大量媒体条目的 docx 过滤后结构完整且媒体全数移除；
   - 测试损坏 zip 优雅 fallback；
   - 测试阈值判断。
4. **适配器测试**：更新 `SidecarStreamDocumentAdapterTest.java` 覆盖大 docx 过滤分支。

## 3. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 损坏或非标准 docx 导致 `ZipInputStream` 抛异常 | `DocxMediaFilter` 内部捕获 `Exception` 并返回原始文件，不阻断主流程 |
| 临时精简文件占用磁盘 | `try-finally` + `Files.deleteIfExists` 确保即刻释放 |
