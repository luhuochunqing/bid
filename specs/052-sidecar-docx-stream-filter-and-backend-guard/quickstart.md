# Quickstart: Feature 052

## 运行测试

```bash
# 1. 运行 Java 单元测试与架构测试
cd backend && mvn test -Dtest=SidecarStreamDocumentAdapterTest,ArchitectureTest -q

# 2. 运行 Sidecar 自动化单测
PYTHONPATH=document-converter-sidecar /opt/miniconda3/bin/pytest document-converter-sidecar/tests/ -v
```
