# Quality Checklist: Docx 媒体流前置瘦身与超大标书秒级解析优化

- [x] **FR-01**: 支持超大 `.docx` 格式文档流式剔除 `word/media/` 图片资源
- [x] **FR-02**: 零外部依赖，使用纯 JDK `ZipInputStream` 与 `ZipOutputStream` 实现流式处理
- [x] **FR-03**: 临时精简文件在转换结束后通过 `try-finally` 可靠删除
- [x] **FR-04**: `fileHash` 计算严格基于原始文件，保障缓存命中一致性
- [x] **FR-05**: 针对普通小文件（$\le 20\text{MB}$）或非 docx 格式零额外开销透传
- [x] **PERF**: 1.66GB 级大文件过滤耗时 $< 2$ 秒，内存增量 $< 1\text{MB}$
