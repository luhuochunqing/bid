# Implementation Plan: Sidecar MarkItDown Docx 预处理内存优化 (051)

## 架构与设计决策

### 1. 拦截与绕过 `pre_process_docx`
- 在 `_convert_worker` 执行时，除了调用 `_suppress_docx_image_inlining()`，同时将 `markitdown.converters._docx_converter.pre_process_docx` 替换为直通函数 `lambda stream: stream`。
- `mammoth.convert_to_html` 会直接接收磁盘临时文件的文件流，通过 zipfile 按需解压读取 `word/document.xml`，完全不把媒体数据加载到内存中。

### 2. 进程作用域隔离
- 与 mammoth 猴补丁保持相同的原则：猴补丁仅在 `ProcessPoolExecutor` 的 Worker 子进程内生效，父进程的符号保持干净，确保 `/convert-images` 依赖原生的提取能力完全不受影响。

### 3. 内存基准自动化单测
- 在 `test_memory_guard.py` 中增加 `test_docx_pre_process_memory_bench`：构造 50MB media 的 docx，通过 `tracemalloc` 断言内存峰值 < 30MB。
