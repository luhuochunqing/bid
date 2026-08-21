# Research: MarkItDown Docx Pre-Processing Memory Analysis

## 现状调查
1. MarkItDown 0.1.7 的 `DocxConverter` 默认调用 `markitdown.converters._docx_converter.pre_process_docx`。
2. 该函数通过 `files = {name: zip_input.read(name) for name in zip_input.namelist()}` 把所有条目读入字典，用于预处理数学公式。
3. 对于 383MB 标书，包含 1893 张图片，内存暴增 2x~3x，直接导致 1GB 容器崩溃。

## Benchmark 实验数据
- 100MB 包含媒体的 DOCX：
  - 默认 MarkItDown：峰值内存 **212.78 MB**
  - 绕过 `pre_process_docx`：峰值内存 **0.10 MB** (降幅 99.95%)
