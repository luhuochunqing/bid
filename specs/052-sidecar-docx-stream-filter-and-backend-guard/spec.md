# Feature Specification: Docx 大文件图文分离极速解析与 Java 堆外内存安全守卫 (052)

## 业务背景与问题定义

投标业务中投标文件常包含海量高清资质扫描件和彩页（如项目 1351 上传的 1.66GB docx 文件）。在对超大标书发起 AI 标书质量核查时存在两大问题：
1. **大文件解析性能与开销**：1.66GB 的 docx 中 99.9%（1.658GB）为 `word/media/` 图片，而核心文本 `word/document.xml` 仅不到 2MB。在不需要图片时处理全量媒体会导致解析缓慢甚至内存溢出。
2. **Java 端降级引发堆外 OOM 崩溃**：当 Sidecar 解析失败或返回非 2xx 时，Java 端 `SidecarStreamDocumentAdapter.java` 错误地调用 `Files.readString(filePath, UTF-8)` 试图将 1.66GB 二进制读入 DirectBuffer，引发 `OutOfMemoryError: Cannot reserve 1743831704 bytes of direct buffer memory`。由于 catch 只捕获 `Exception`，导致 JVM Error 击穿 Spring，请求阻塞 70s 后报 500。

## 用户故事与需求

### User Story 1 (P1): Java 端文档转换安全守卫与异常加固
- **AS** 平台开发者 / 运维系统
- **I WANT** Java 端在调用 Sidecar 解析文档失败或降级时，严格限制非文本文件的内存读取，并捕获所有 Throwable（包括 OOM 等 Error）
- **SO THAT** 任何异常都不会导致 JVM 堆外内存耗尽或 500 异常穿透，且对用户提供友好清晰的错误反馈。

### User Story 2 (P1): Docx 文本极速流式提取（秒级支持 3GB+ 标书）
- **AS** 质量核查系统 / 投标业务人员
- **I WANT** 系统从超大 docx 中提取纯文本和大纲时，直接流式读取 `word/document.xml` 并完全跳过 media 图片
- **SO THAT** 即使是 2GB~3GB 的超大标书也能在 2 秒内完成文字解析，内存消耗 < 10MB。

## 验收标准 (Acceptance Criteria)

1. **AC1.1**: Java 端 `SidecarStreamDocumentAdapter` 绝不针对 `.docx`, `.pdf`, `.zip` 等二进制文件执行 `Files.readString`；非文本降级时安全截断或返回空文档。
2. **AC1.2**: Java 端异常捕获扩展至 `Throwable`，确保即使底层抛出内存相关 Error 也绝不会导致 HTTP 500 穿透。
3. **AC1.3**: Sidecar 针对 `.docx` 文本解析实现 media 零开销流式直解，50MB~300MB+ docx 文本解析耗时 < 3s，内存占用 < 15MB。
4. **AC1.4**: 跨环境自动化单测全绿，测试机实证 project 1351 / 1352 解析稳定无 500。
