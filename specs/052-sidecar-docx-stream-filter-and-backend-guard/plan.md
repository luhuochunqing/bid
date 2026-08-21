# Implementation Plan: Docx 大文件图文分离极速解析与 Java 堆外内存安全守卫 (052)

## 架构与设计决策

### 1. Java 后端降级防护与毒代码消除
- **文件**: `com.xiyu.bid.compliance.service.SidecarStreamDocumentAdapter`
- **改造**:
  - 当 Sidecar 返回非 2xx 或异常时：
    - 仅对已知纯文本后缀（如 `.txt`, `.md`, `.json`, `.csv`）且文件大小小于 5MB 时允许 `Files.readString`；
    - 对 `.docx`, `.pdf`, `.zip` 等二进制文件或超大文件，直接计算 SHA-256 并返回空文本 `DocumentContent`（由上层抛出 422 友好提示），坚决杜绝 `Files.readString` 导致的 DirectBuffer 堆外 OOM。
    - 将 catch 块统一改为 `catch (Throwable t)`，阻止 `OutOfMemoryError` 击穿框架。

### 2. Sidecar Docx Direct Stream 文本解析
- **文件**: `document-converter-sidecar/app.py` / `converter.py`
- **改造**:
  - 在 docx 转换时，除了已有 `pre_process_docx` 直通和 `mammoth` 补丁外，通过极简的流式 zipfile 读取器直接提取 `word/document.xml` 或交由 mammoth 处理，确保无论 docx 包含几千张媒体图片，整个解析过程零媒体内存开销。

### 3. 测试与验证策略
- **Java 端**: 在 `SidecarStreamDocumentAdapterTest` 中测试大文件及二进制格式的安全降级，断言不抛出 `OutOfMemoryError` 且返回结构健全。
- **Python 端**: 在 `test_memory_guard.py` 中测试超大媒体 docx 依然以 < 10MB 内存完成转换。
- **真机实测**: 在 `172.16.38.78` 测试环境验证。
