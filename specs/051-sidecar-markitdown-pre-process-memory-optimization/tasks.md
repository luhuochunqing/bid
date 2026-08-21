# Tasks: Sidecar MarkItDown Docx 预处理内存优化 (051)

## Phase 1: 核心实现与 Worker 补丁
- [x] T001: 在 `document-converter-sidecar/app.py` 中增加 `_suppress_docx_pre_process()`，并在 `_convert_worker` 中初始化调用
- [x] T002: 在 `document-converter-sidecar/tests/test_memory_guard.py` 中增加大媒体 docx 内存峰值自动化单测

## Phase 2: 测试与基准验证
- [x] T003: 运行完整 pytest 测试套件并验证内存占用 < 30MB
- [x] T004: 运行后端 ArchUnit 测试保证无架构破坏

## Phase 3: 部署与实证
- [x] T005: 部署至测试机 `172.16.38.78` 并重建 sidecar 容器
- [x] T006: 针对项目 1352 / 383MB 标书触发质量核查，实证通过

