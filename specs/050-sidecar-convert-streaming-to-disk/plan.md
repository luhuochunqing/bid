# Implementation Plan: Sidecar /convert 流式落盘与哈希增量计算

**Feature Directory**: `specs/050-sidecar-convert-streaming-to-disk`

## Architecture & Design

1. **改造范围**：
   - 目标文件：`document-converter-sidecar/app.py`
   - 目标函数：`convert_document` (POST `/convert`)
   - 目标测试：`document-converter-sidecar/tests/test_app.py`

2. **核心逻辑**：
   - 使用 `hashlib.sha256()` 实例 `hasher`。
   - 使用 `tempfile.NamedTemporaryFile(delete=False, suffix=original_suffix)` 创建临时文件。
   - 在 `while True: chunk = await file.read(chunk_size)` 循环中（64KB 块）：
     - 检查 `total_read > MAX_UPLOAD_SIZE_BYTES`，若超限抛出 `HTTPException(413)`。
     - 执行 `hasher.update(chunk)`。
     - 执行 `tmp.write(chunk)`。
   - 循环结束后调用 `content_hash = hasher.hexdigest()`。
   - 将 `tmp.name` 传递给下游 `md.convert(processing_path)`。
   - `finally` 块中清理 `tmp_path` 与 `converted_path`。

3. **门禁与合规检查**：
   - 遵守 Constitution Core Principle: 最小化代码、无状态泄漏、防御性编程。
   - 保持向后兼容：输出 JSON 结构及字段语义完全一致。
