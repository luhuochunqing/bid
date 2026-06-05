# 项目级招标文件拆解模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明

`projecttenderbreakdown` 承载项目详情页“解析招标文件”的项目级入口。
它负责权限守卫、解析就绪检查、最新解析结果复用和 HTTP 边界编排，并把上传文件交给现有招标文件导入服务写入
`bid_tender_document_snapshots` 与 `bid_requirement_items`。

本模块保持 API 路径 `/api/projects/{projectId}/tender-breakdown` 不变。解析结果后续可被任务拆解和
AI 生成初稿共同复用，因此不再归属于 `biddraftagent` 的 controller/application 边界。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/` | API 边界 | 暴露项目级招标文件解析和 readiness 检查接口 |
| `application/` | Application Service / Port | 执行项目权限守卫、返回解析配置就绪状态 |

## API 入口

| Method | Path | 用途 |
|--------|------|------|
| `GET` | `/api/projects/{projectId}/tender-breakdown/readiness` | 检查当前用户项目权限和 DeepSeek 解析配置是否就绪 |
| `GET` | `/api/projects/{projectId}/tender-breakdown/latest` | 返回项目最新已解析招标文件快照；无快照时返回空数据，前端再进入上传解析 |
| `POST` | `/api/projects/{projectId}/tender-breakdown/reuse-uploaded` | 复用项目文档中已上传的招标文件，或复用人工录入标讯时保存的源附件，直接解析并写入快照；无可复用文件时返回空数据 |
| `POST` | `/api/projects/{projectId}/tender-breakdown` | 上传并解析项目级招标文件，写入快照和需求项 |

## 复用关系

- 上传解析仍复用 `BidTenderDocumentImportAppService.parseTenderDocument()`，避免复制文件保存、正文提取、需求项入库逻辑。
- 最新快照查询复用 `BidTenderDocumentImportAppService.latestParsedTenderDocument()`，只读取已入库的解析快照，不重新读取或上传文件。
- 已上传文件复用通过 `BidUploadedTenderDocumentReuseAppService.parseLatestUploadedTenderDocument()` 读取项目文档中真实落盘的招标文件；若项目文档为空，则读取 Tender 上保存的 `doc-insight://` 源附件并创建项目招标文件记录，生成新的解析快照而不要求用户再次选择文件。
- DeepSeek 配置检查复用 `biddraftagent.application.TenderIntakeConfigurationReadiness` 端口和 readiness DTO，避免 AI 基础设施反向依赖本项目入口模块。
- 模块只做项目级入口编排，不承担招标要求抽取规则、任务生成规则或数据库实体转换。
- 解析入口是独立项目能力：任务拆解和 AI 生成初稿都可以消费解析结果，但彼此不互相阻塞。
