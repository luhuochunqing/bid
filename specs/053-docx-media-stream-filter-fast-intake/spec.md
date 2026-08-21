# Feature Specification: Docx 媒体流前置瘦身与超大标书秒级解析优化 (053)

## 业务背景与问题定义

在投标业务实践中，标书（`.docx`）常因包含大量业绩截图、产品样图、企业资质等图片而膨胀至数百 MB 甚至上 GB（如项目 1351 的 1.66GB 标书）。
经深度剖析，这类超大 docx 文件的文本内容（`word/document.xml`）通常仅占 100KB ~ 2MB，而 99.9% 的体积均由 `word/media/` 中的图片二进制占用。

当向 Sidecar `/convert` 传输 1.66GB 原始文件时：
1. **HTTP 传输超时与超限**：1.66GB 本地/网络传输消耗数十秒，且触发 Sidecar 512MB (`MAX_UPLOAD_SIZE_BYTES`) 安全限制（HTTP 413）；
2. **业务中断**：Java 端因 Sidecar 拒绝而返回空文本，触发同步前置校验拦截（HTTP 422），导致 AI 标书质量核查与得分点解析不可用。

## 用户故事与使用场景

### User Story 1: 超大 Docx 标书无缝支持 (P1)
**作为** 投标专员或风控管理员，
**我希望** 上传数十 MB 至数 GB 且包含海量图片的 `.docx` 标书时，AI 标书质量核查与得分点解析能正常秒级完成，
**以便于** 无论标书图片多少，系统均能稳定产出核查报告，不发生 413、422 或 500 异常。

### User Story 2: 极速网络与存储节约 (P1)
**作为** 系统架构师与运维工程师，
**我希望** Java 后端在向 Sidecar 发送文档转换请求前，对超大 `.docx` 进行轻量流式去图瘦身（仅去除 `word/media/`），
**以便于** 发送给 Sidecar 的 HTTP 请求体控制在 2MB 以内，避免百兆/千兆带宽吞吐浪费与 Sidecar 内存膨胀。

## 功能需求 (Functional Requirements)

- **FR-01 (Docx 媒体过滤)**：在 Java 端 `SidecarStreamDocumentAdapter` 发送文档至 Sidecar 前，若检测到输入文件为 `.docx`（不区分大小写）且物理文件大小超过阈值（`20MB`），必须流式剔除 ZIP 内 `word/media/` 目录下的所有媒体资源，生成轻量 docx 临时文件送入 Sidecar。
- **FR-02 (零外部依赖 & 零常驻内存)**：媒体过滤必须完全基于 JDK 内置 `java.util.zip.ZipInputStream` 与 `java.util.zip.ZipOutputStream` 流式处理，禁止将整个文件读入内存数组，确保流转过程内存占用 $< 1\text{MB}$。
- **FR-03 (临时资源原子清理)**：媒体过滤生成的临时文件必须在 HTTP 请求完成（无论成功、失败或异常）后被 `finally` 可靠删除，禁止临时文件残留。
- **FR-04 (SHA-256 唯一性与哈希一致性)**：文档的 `fileHash` 计算必须始终基于原始物理文件的完整内容进行 SHA-256 摘要，确保缓存命中与审计一致性不受精简过滤影响。
- **FR-05 (透明向后兼容)**：对于 $\le 20\text{MB}$ 的 docx 或非 docx 文件（如 `.pdf`, `.txt`），直接透传原始文件，不产生额外 I/O 开销。

## 验收条件与成功指标

1. **大文件核查成功率**：1.66GB 真实 docx 标书（项目 1351）能成功完成 Sidecar 转换与 AI 标书质量核查；
2. **传输与解析耗时**：1.66GB 标书的前置瘦身 + Sidecar 转换总耗时 $< 5$ 秒；
3. **HTTP Payload 缩小**：发送给 Sidecar 的 HTTP 请求体 $< 5\text{MB}$（相比 1.66GB 缩减 99.7% 以上）；
4. **单元与集成测试**：所有相关单元测试及全链路用例通过，无任何内存泄露或临时文件遗留。
