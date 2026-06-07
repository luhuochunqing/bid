# ProjectWorkflow 模块 (项目流程衍生对象)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
项目流程模块承接项目任务、提醒、分享链接、文档和评分草稿等派生能力。它位于 Project 域的执行侧，负责把项目执行中的子流程收拢到统一边界。
任务拆解只读取已解析的招标文件需求项或章节快照，不直接调用 AI；解析入口由 `projecttenderbreakdown` 模块提供。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ProjectWorkflowController.java` | Controller | 项目流程接口 |
| `controller/ProjectDocumentController.java` | Controller | 项目文档接口，承接结果附件查询、真实文件上传、元数据创建和删除 |
| `core/ScoreDraftPolicy.java` | Core | 评分草稿更新与任务生成的纯规则，返回显式决策和值对象，不直接做 I/O |
| `core/TaskBreakdownPolicy.java` | Core | 根据招标需求项或标书章节快照生成任务拆解决策，不访问数据库或框架 |
| `service/ProjectWorkflowService.java` | Service | 项目流程门面；转调任务、评分草稿、文档、提醒和分享链接子服务 |
| `service/ProjectTaskWorkflowService.java` | Service | 项目任务查询、创建和状态流转；创建任务未显式指定责任人时默认当前创建人 |
| `service/ProjectTaskBreakdownService.java` | Service | 任务拆解编排；读取真实标书拆解结果、调用纯核心、保存项目任务 |
| `service/ProjectTaskBreakdownSourceReader.java` | Reader | 读取任务拆解来源；通过端口读取招标需求项，章节只兜底读取顶层/二级章节 |
| `service/ProjectTaskRequirementSourceGateway.java` | Port | 任务拆解读取招标需求来源的端口，由招标文件解析能力侧提供实现，避免流程模块反向依赖解析模块实体 |
| `service/ProjectDocumentWorkflowService.java` | Service | 项目文档查询、元数据创建和删除编排，调用文档绑定边界 |
| `service/ProjectDocumentUploadWorkflowService.java` | Service | 项目文档真实文件上传编排；通过文件存储端口落盘后转交文档创建服务 |
| `service/ProjectDocumentFacade.java` | Component | 文档查询、元数据创建、真实上传和删除的内部组合门面，保持 ProjectWorkflowService 依赖数稳定 |
| `service/ProjectDocumentFileStorage.java` | Port | 项目文档文件落盘端口，由 bid-agent 文件存储适配器实现，避免 projectworkflow 反向依赖解析模块 |
| `service/ProjectDocumentViewAssembler.java` | Assembler | 项目文档实体到 DTO 的装配 |
| `service/ProjectDocumentBindingGateway.java` | Port | 项目文档与外部附件业务的可替换集成边界 |
| `service/ScoreDraftParserService.java` | Service | 评分草稿解析，支持 Word、Excel 和文本型 PDF 上传 |
| `parser/ScoreDraftDocumentTextExtractor.java` | Adapter | 将 Word、Excel、文本型 PDF 评分文件抽取为统一行文本，供评分草稿纯解析器消费 |
| `parser/ScoreDraftCompactTableLineExpander.java` | Core Helper | 将 PDF 抽取出的同一行表格文本展开为既有评分解析器可识别的行序列 |
| `entity/ProjectDocument.java` | Entity | 项目文档实体 |
| `entity/ProjectReminder.java` | Entity | 项目提醒实体 |
| `entity/ProjectShareLink.java` | Entity | 项目分享链接实体 |
| `entity/ProjectScoreDraft.java` | Entity | 评分草稿实体 |
| `repository/` | Repository | 项目流程持久化访问 |
| `dto/` | DTO | 任务、提醒、文档、分享和评分草稿请求/响应 |

## 任务拆解数据来源

1. 优先读取最新招标文件解析后的 `bid_requirement_items`。
2. 若需求项为空，回退读取 `document_sections` 的顶层和二级章节。
3. 若仍为空，返回业务错误“未找到可用于拆解任务的标书拆解结果”。

`TaskBreakdownPolicy` 是纯核心，负责把来源快照映射为待创建任务决策；`ProjectTaskBreakdownService` 只做权限、取数、事务和保存编排。
