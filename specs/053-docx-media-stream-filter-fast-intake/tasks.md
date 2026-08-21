# Tasks: Docx 媒体流前置瘦身与超大标书秒级解析优化 (053)

## Phase 1: Setup & Foundational (Prerequisites)
- [ ] T001: 创建 `DocxMediaFilter.java` 纯 JDK Zip 流式媒体过滤器，过滤 `word/media/` [P]
- [ ] T002: 编写 `DocxMediaFilterTest.java` 单元测试，覆盖媒体过滤、小文件跳过与损坏 zip 回退 [P]

## Phase 2: Core Adapter Integration
- [ ] T003: 改造 `SidecarStreamDocumentAdapter.java`，集成 `DocxMediaFilter` 瘦身流水线与 `finally` 临时文件清理
- [ ] T004: 补充 `SidecarStreamDocumentAdapterTest.java` 针对大 Docx 过滤及流式 HTTP 请求的测试用例

## Phase 3: Verification & Polish
- [ ] T005: 运行 Maven 测试集与 ArchUnit 架构门禁
- [ ] T006: 知识库与教训文档沉淀（更新 lessons-learned.md 与 wiki）
- [ ] T007: 提交 PR 并合入 main，打包部署到测试机 172.16.38.78 并实测项目 1351
