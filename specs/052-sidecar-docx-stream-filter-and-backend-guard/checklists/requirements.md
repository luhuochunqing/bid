# Quality Checklist: Feature 052

## 质量门禁清单

- [ ] Java 端 `SidecarStreamDocumentAdapter` 移除对非文本文件的 `Files.readString` 降级，避免堆外 DirectBuffer OOM
- [ ] Java 端通信异常防护升级至 `catch (Throwable t)`，杜绝 `OutOfMemoryError` 等致命 Error 穿透成 500
- [ ] Sidecar 针对 `.docx` 实现零内存开销 direct stream 解析，完全规避 `word/media/` 带来的内存与时间浪费
- [ ] 编写 Java 与 Python 单元测试，覆盖超大文件安全性与直接解析正确性
- [ ] 部署至 `172.16.38.78` 测试环境，实测验证无 500 异常
