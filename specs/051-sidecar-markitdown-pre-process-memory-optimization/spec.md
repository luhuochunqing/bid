# Feature Specification: Sidecar MarkItDown Docx 预处理内存优化 (051)

## 业务背景与问题定义

西域投标管理平台在对大型投标文件（如 383MB 的 docx 标书）执行 AI 标书质量核查时，Sidecar 进程池中的转换 Worker 因解析内存峰值超过 1GB，被 Docker 容器的 Linux cgroup OOM-Killer 杀死，导致 Java 端收到 503 并在前端呈现 422 解析失败。

### 根因分析
MarkItDown 的 `DocxConverter` 默认调用了 `pre_process_docx(file_stream)`。该函数将 docx 压缩包内的所有文件（包括数百兆的 `word/media/*` 嵌入图片）全量解压读入内存字典，并重新打包输出到 `BytesIO`，一次性产生 >760MB 的内存分配，叠加 Python 运行时与 DOM 构建后瞬间击穿 1GB 容器内存限制。

## 用户故事与需求

### User Story 1 (P1): 消除 docx 转换过程中的全量解包与内存重打包
- **AS** 质量核查系统 / 投标业务人员
- **I WANT** 上传 300MB+ 包含海量图片的 docx 标书时，Sidecar 能够在 1GB 容器内平稳完成文本与大纲解析
- **SO THAT** 质量核查任务不再因为 OOM-Killer 导致中断报错

### 验收标准 (Acceptance Criteria)
1. **AC1.1**: 对包含 100MB+ media 的 docx 文件执行转换时，转换 Worker 的内存峰值开销从 >200MB 降至 <30MB（消除 rate > 85%）。
2. **AC1.2**: 转换过程不影响文本内容提取、大纲层级抽取以及 SHA-256 哈希计算的正确性。
3. **AC1.3**: 具备 50MB+ media 的真实 docx 内存基准自动化单测，CI 门禁常态化防护。
4. **AC1.4**: 部署至测试服务器并在真实环境验证 383MB 标书（项目 1352）质量核查触发成功（200 OK）。
