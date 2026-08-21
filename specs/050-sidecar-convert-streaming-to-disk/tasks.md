# Tasks: Sidecar /convert 流式落盘改造

## Phase 1: Setup & Prerequisite Checks
- [x] T001: 验证本地 Python 测试环境与依赖项 (`fastapi`, `uvicorn`, `markitdown`, `pytest`, `httpx`)

## Phase 2: Implementation (User Story 1 - 流式落盘与 SHA-256 计算)
- [x] T002: 修改 `document-converter-sidecar/app.py` 中的 `convert_document` 接口，移除 `chunks` 列表与 `b"".join(chunks)`，改为 64KB 块直接流式写入 `NamedTemporaryFile`
- [x] T003: 在文件流式写入循环中增量调用 `hasher.update(chunk)`，计算 `content_hash`，替代原先全量内存哈希计算
- [x] T004: 保持大小门禁（`MAX_UPLOAD_SIZE_BYTES` / 413 响应）与 `finally` 临时文件清理闭环

## Phase 3: Testing & Verification (User Story 2 & Regression Guard)
- [x] T005: 在 `document-converter-sidecar/tests/test_app.py` 中新增 `test_convert_streaming_content_hash` 自动化测试
- [x] T006: 运行 `pytest document-converter-sidecar/tests/ -v`，确认全部 26 项测试 100% 通过
