# Requirements Checklist: Feature 051

## Quality Checklist

- [x] 明确消除 MarkItDown `pre_process_docx` 导致的大文件内存翻倍根因
- [x] 确保优化仅作用于转换 Worker 子进程，不影响主进程及 `/convert-images` 路径
- [x] 具备确定性的内存峰值自动化断言测试（tracemalloc 断言）
- [x] 跨环境验证（本地 miniconda pytest + Docker + 测试服务器 172.16.38.78）
