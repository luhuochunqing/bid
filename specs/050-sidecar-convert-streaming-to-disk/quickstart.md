# Quickstart & Verification Guide

## 1. 本地运行测试

```bash
# 运行全部 sidecar 测试
PYTHONPATH=document-converter-sidecar /opt/miniconda3/bin/pytest document-converter-sidecar/tests/ -v
```

## 2. 验证关键断言

- `test_upload_exceeds_limit_returns_413` -> 验证 413 大小门禁
- `test_convert_streaming_content_hash` -> 验证流式 SHA-256 计算一致性
- 全部 26 项测试通过
