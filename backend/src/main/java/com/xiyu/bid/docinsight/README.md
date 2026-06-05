# DocInsight 模块 (文档智能解析模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
DocInsight 模块负责通用文档上传、文本抽取、结构化分块和 AI 解析编排。这里提供可被招标文件解析等业务复用的文档智能能力，业务特定提示词和结果映射由调用方适配。

## 边界
`domain` 包是纯核心，负责文档分块规则和结构化文本处理，不读取数据库、文件、网络、时间或日志。`application` 包负责端口定义和用例编排。`controller`、`infrastructure` 包承担 HTTP、文件存储、MarkItDown sidecar 和 AI 解析调用等副作用。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `application/DocumentAnalysisInput.java` | Port Model | 文档解析输入 |
| `application/DocumentAnalysisResult.java` | Port Model | 文档解析结果 |
| `application/DocumentAnalyzer.java` | Port | 文档解析器接口 |
| `application/DocumentIntelligenceService.java` | Application Service | 文档智能编排接口 |
| `application/DocumentIntelligenceServiceImpl.java` | Application Service | 上传、抽取、解析编排 |
| `application/DocumentStorage.java` | Port | 文档存储接口 |
| `application/DocumentTextExtractor.java` | Port | 文本抽取接口 |
| `application/ExtractedDocument.java` | DTO | 抽取后的文档文本 |
| `application/StoredDocument.java` | DTO | 存储后的文档元数据 |
| `controller/DocInsightController.java` | Controller | 通用文档解析接口 |
| `domain/DocInsightProfiles.java` | Pure Core | profile 常量与项目绑定判断 |
| `domain/DocumentChunk.java` | Pure Core | 文档分块值对象 |
| `domain/StructuralDocumentChunker.java` | Pure Core | 结构化文档分块策略 |
| `domain/TextChunker.java` | Pure Core | 文本分块接口 |
| `infrastructure/config/MarkItDownSidecarClientConfig.java` | Config | MarkItDown sidecar 客户端配置 |
| `infrastructure/extractor/MarkItDownSidecarExtractor.java` | Adapter | MarkItDown sidecar 文本抽取适配，支持 `X-Sidecar-Key` 共享密钥 |
| `infrastructure/openai/BaseOpenAiDocumentAnalyzer.java` | Adapter | AI 通用文档解析适配 |
| `infrastructure/storage/LocalDocumentStorage.java` | Adapter | 本地文档存储适配 |

## Profile 约定

- `TENDER`：项目绑定招标文件解析，`entityId` 视为项目 ID，进入解析前必须通过 `ProjectAccessScopeService` 校验。
- `TENDER_INTAKE`：标讯入库前自动识别，复用招标文件字段提取能力，但不绑定项目，也不触发项目访问范围校验。
