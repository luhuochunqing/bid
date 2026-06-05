# Research: 知识库模块技术可行性与设计方案

## 1. AI 案例异步切片沉淀与 Sidecar 交互

### 问题描述
如何从已上传的标书大文件中提取并分块，按项目评分项进行精准分类切片，最终沉淀成可复用的案例？

### 调研与发现
1. **正文提取服务**：
   后端 `docinsight` 模块已配备 `MarkItDownSidecarExtractor`。该适配器能够将标书文件（Word, PDF 等）发送至 `http://localhost:8000/convert` 做 doc-to-markdown 的高质量转换，且支持通过 headers 注入 `X-Sidecar-Key` 共享密钥。
2. **文本切片与 LLM 匹配**：
   由于标书可能长达数百页，直接提交会超出 LLM 上下文。我们计划在异步切片服务中引入 `StructuralDocumentChunker` 进行章节与段落分割，再针对各个评分项的标题和核心要求，批量发送给 AI 客户端（借助现有的 `docinsight` 中注入的大模型接口），过滤无用过渡段，仅保留与评分项完全对齐的应答段落。

### 最终决策
*   **提取媒介**：异步任务启动时，调用 `MarkItDownSidecarExtractor` 将标书转为 Markdown 纯文本。
*   **LLM 交互流程**：
    1.  解析并读取招标文件中的评分项原文列表。
    2.  大模型以 Markdown 标书正文为上下文源，按各评分项语义进行应答段落精确定位。
    3.  仅抽取不做改写，对提取出的匹配片段生成 `KnowledgeCase` 并入库。

---

## 2. 台账与归档文件包一键导出方案

### 问题描述
如何高效、安全地导出项目台账 Excel 及带分类结构的原文件 ZIP 包，并防止极大型项目因 IO 阻塞导致 OOM？

### 调研与发现
1. **Excel 导出**：
   后端 `export` 模块封装了 `ExcelExportService` 基础设施，能够处理导出的数据映射和流式写入，同时具备行数监控。我们将在该模块下为 `PROJECT_ARCHIVE` 注册专用的 Sheet 写出器：
   *   Sheet 1：项目档案信息（档案创建时间、负责人、文件总数等）。
   *   Sheet 2：文件清单明细。
2. **ZIP 打包流式处理**：
   为了规避因一次性加载文件数据进内存导致的 OOM，我们避免在内存中缓存字节数组。而是利用 `java.util.zip.ZipOutputStream` 结合 Spring Boot 提供的流式响应 `StreamingResponseBody` 直接在网络输出流中进行 ZIP 打包：
   ```java
   StreamingResponseBody responseBody = out -> {
       try (ZipOutputStream zos = new ZipOutputStream(out)) {
           for (ArchiveFile file : files) {
               ZipEntry entry = new ZipEntry(file.getRelativePath());
               zos.putNextEntry(entry);
               // 流式读取本地文件并写到 zos 中
               StreamUtils.copy(fileInputStream, zos);
               zos.closeEntry();
           }
       }
   };
   ```

### 最终决策
*   **Excel 导出**：扩展 `ExcelExportService`，定义 `ProjectArchive` 的数据导出规则。
*   **ZIP 导出**：在 `casework` 的 application 服务层提供打包流生成，在 HTTP controller 响应体中直接绑定 `StreamingResponseBody` 输出，包内路径格式为 `[项目名]/[文档分类]/[原文件名]`，并在根目录放入导出的 `_台账.xlsx` 文件。

---

## 3. 安全隔离权限与审计日志

### 问题描述
如何确保投标专员仅可见自己参与的项目，且记录所有对敏感资质附件及项目档案的预览、下载行为？

### 调研与发现
1. **权限守卫**：
   系统架构已有 `ProjectAccessScopeService` 权限拦截基础设施，用于拦截用户无权的 `projectId` 资源。
2. **敏感资质与 OA 对接**：
   投标专员借用证书前，必须录入与该专员及当前项目关联且在有效期内、审批状态为 `APPROVED` 的 `QUALIFICATION_BORROW` 工作流表单实例，这部分的实体已由 `businessqualification` 包负责。

### 最终决策
*   **项目档案数据过滤**：在 `casework/application` 的查询服务中，拦截当前的 `userId`。若角色为投标专员，将 `ProjectAccessScopeService.getAccessibleProjectIds(userId)` 作为 SQL 条件注入，防止越权检索。
*   **借阅鉴权**：在资质下载/预览 Controller 接口中，查询 `QualificationBorrowRecord` 表，确认当前用户对当前资质证书在有效期内具有通过审批的记录。
*   **操作审计日志**：在 Core domain 中定义 `ArchiveLog` 和 `QualificationActionLog` 实体。Imperative Shell 拦截到预览/下载请求并校验通过后，向数据库插入日志，操作日志在详情抽屉中以时间倒序列表供所有人只读调阅。
