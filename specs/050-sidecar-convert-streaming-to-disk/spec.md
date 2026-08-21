# Feature Specification: 文档转换 Sidecar /convert 流式落盘与哈希增量计算

**Feature Branch**: `agent/gemini/sidecar-convert-streaming-to-disk`

**Created**: 2026-08-21

**Status**: Completed

**Input**: User description: "上传标书，点击ai标书质量核查，报错：无法执行核查：投标文件解析失败。根本修复：将 document-converter-sidecar/app.py 的 /convert 接口改为边读边写临时文件（流式落盘），不使用 b''.join(chunks) 驻留内存，并保持 SHA-256 哈希计算准确。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 超大标书质量核查与文档解析不崩溃 (Priority: P1)

用户上传数百 MB（例如 383MB）的投标文件（.docx / .pdf / .pptx 等）并在标书质量核查或智能评分等模块发起解析时，Sidecar 的 `/convert` 接口通过流式将 HTTP 请求体边读边写入磁盘临时文件，并增量更新 SHA-256 哈希，而不是在内存中缓存全量字节与 `b"".join(chunks)`。在 1GB 内存限制的 Docker 容器中稳定运行，不再发生 Python `MemoryError` 或 Docker OOM-Killer（`Killed`），Java 后端能够顺利获取解析结果并完成质量核查。

**Why this priority**: 投标文件的体积通常较大（含高分辨率插图、证书扫描件），在生产与测试环境容器加固为 1GB/2GB 内存限制后，内存全量驻留会导致超大文件解析 100% 崩溃，阻断整个质量核查功能。

**Independent Test**: 上传一个 >300MB 的文档文件（或通过单测模拟分块读取与大文件分批流式传输），调用 `/convert`，Sidecar 内存使用保持平稳（仅占用固定 64KB 缓冲区），成功返回 200 及正确的 markdown 内容与 contentHash。

**Acceptance Scenarios**:

1. **Given** 用户上传 383MB 的投标文件（如 `张家口银行——投标6.16.docx`），Sidecar 容器内存限制为 1GB，**When** 后端调用 `/convert` 接口解析文档，**Then** Sidecar 边读边写入临时文件并在完成后调用 MarkItDown 解析，返回 200 成功响应，不触发 MemoryError，不被 OOM-Killer 杀死。
2. **Given** 正常上传任意合法文件，**When** 调用 `/convert` 接口，**Then** 返回的 `contentHash` 必须与该文件二进制内容的真实 SHA-256 十六进制字符串严格一致。
3. **Given** 上传文件大小超过 `MAX_UPLOAD_SIZE_BYTES`（默认 512MB），**When** 边读边写时累计字节数超限，**Then** 立即中止并返回 HTTP 413 "请求体过大"，并在 `finally` 块清理已写入的临时文件，不泄露磁盘空间。

---

### User Story 2 - 异常与格式处理与清理闭环 (Priority: P2)

当文档在流式传输中断、格式损坏或解析失败时，Sidecar 必须确保所有创建的临时文件（包括原始临时文件及 `.doc` 转 `.docx` 的转换临时文件）在 `finally` 块中被安全删除，不发生磁盘泄露，且向调用方返回明确的 HTTP 错误码（如 400/413/422/500）。

**Why this priority**: 长期运行的容器中若在异常时残留临时文件，会导致磁盘空间被耗尽，影响服务健康度。

**Independent Test**: 模拟解析损坏文件或超限请求，断言 HTTP 错误响应返回，并验证临时文件路径已被清理（不存在于 `/tmp`）。

**Acceptance Scenarios**:

1. **Given** 上传未提供文件名或空请求，**When** 调用 `/convert`，**Then** 返回 HTTP 400 "未提供文件名"。
2. **Given** 无论解析成功、失败抛出异常或连接中断，**When** 请求处理结束，**Then** `tmp_path` 与 `converted_path`（若有）均被安全移除。

---

### Edge Cases

- **极小文件 / 空内容**：正确计算空内容或小内容的 SHA-256，不产生死循环。
- **超大文件（>512MB）**：在流式读取达到 512MB 时立即抛出 413，不继续落盘，并在 finally 中删除临时文件。
- **旧版 `.doc` 文件**：临时文件保存为 `.doc`，在 macOS 环境下调用 `textutil` 转为 `.docx`，双重临时文件均在 `finally` 块中被安全删除。
- **鉴权拦截**：当配置 `SIDECAR_SHARED_KEY` 时，若未提供或提供错误的 `X-Sidecar-Key`，在开始读取流之前返回 HTTP 401。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `/convert` 接口 MUST 采用流式（每次 64KB）逐块从请求体中读取数据并写入 `tempfile.NamedTemporaryFile`，禁止在 Python 内存中构建 `chunks: list[bytes]` 或执行 `b"".join(chunks)`。
- **FR-002**: `/convert` 接口 MUST 在逐块读取并写入临时文件的过程中，使用 `hashlib.sha256().update(chunk)` 增量计算文件哈希，最终生成与文件全量 SHA-256 一致的 `contentHash`。
- **FR-003**: `/convert` 接口 MUST 保留 `MAX_UPLOAD_SIZE_BYTES` 门禁，累计读取字节数超过上限时立即抛出 HTTP 413 异常。
- **FR-004**: `/convert` 接口 MUST 在 `finally` 块中保证 `tmp_path` 以及可能存在的转换文件 `converted_path` 被删除，杜绝磁盘临时文件泄漏。
- **FR-005**: `/convert` 接口输出的数据契约（包含 `documentId`, `markdown`, `sections`, `tables`, `warnings`, `converter`, `contentHash`）MUST 保持 100% 向后兼容。

## Success Criteria *(mandatory)*

- **SC-001**: 自动化测试：Sidecar 测试套件中包含验证流式落盘与 SHA-256 计算一致性的单测，整体 26 项测试全部通过（100%）。
- **SC-002**: 内存稳定性：在 1GB 内存限制容器内，处理 >300MB 标书文件时，内存常驻占用显著降低，杜绝 `MemoryError` 与 OOM-Killer 杀死进程。
