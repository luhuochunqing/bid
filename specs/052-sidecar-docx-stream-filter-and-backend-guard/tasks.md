# Tasks: Docx 大文件图文分离极速解析与 Java 堆外内存安全守卫 (052)

## Phase 1: Java 端安全守卫与毒代码消除
- [x] T001: 在 `SidecarStreamDocumentAdapter.java` 中限制非文本格式与大文件的本地读取，扩展 catch 为 `Throwable`
- [x] T002: 在 `SidecarStreamDocumentAdapterTest.java` 中新增二进制文件/大文件降级不抛 OOM 的单元测试

## Phase 2: Sidecar 极速 docx 流式文本直解
- [x] T003: 验证 Sidecar 对超大 docx 的媒体跳过与流式提取

## Phase 3: 自动化测试与基准验证
- [x] T004: 运行 Java 单元测试与 ArchUnit 门禁
- [x] T005: 运行 Python pytest 套件验证全部测试通过

## Phase 4: 部署与测试环境实测
- [x] T006: 部署至测试机 `172.16.38.78` 并重启 sidecar / backend
- [x] T007: 针对项目 1351 / 1352 进行实证验收

