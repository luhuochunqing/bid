# 标书生成 Agent 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明

`biddraftagent` 是项目详情里的“AI 生成标书初稿”后端主链路。
它负责把上传的招标文件、项目、标讯和企业资料快照拆解成可写作草稿、缺漏检查、人工确认提示，并在 `apply` 时写入 `documenteditor` 的章节树。

本模块不是“自动中标/自动定稿”系统，报价、法务承诺、资质真实性和关键商务偏离必须保留人工确认提示。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `domain/` | 纯核心 | 招标要求归类、材料匹配打分、缺漏检查、人工确认和写作覆盖决策 |
| `application/` | Application Service / Port / Planner | 编排招标文件导入、run 生命周期、生成写入计划、定义文档写入端口 |
| `infrastructure/documenteditor/` | Adapter | 把写入计划转换为 `documenteditor` 批量章节树写入请求 |
| `infrastructure/openai/` | Adapter | 通过 AI SDK 拆解招标要求、生成草稿、审阅摘要和交接清单；`TENDER_INTAKE` 强制走 DeepSeek Chat Completions；`e2e` profile 下不激活 |
| `infrastructure/tenderdocument/` | Adapter | 保存上传文件，优先通过带 `X-Sidecar-Key` 共享密钥的 MarkItDown sidecar 提取正文，失败时降级到 POI/PDFBox；同时适配 projectworkflow 的项目文档文件存储端口 |
| `infrastructure/e2e/` | Adapter | 仅 `e2e` profile 生效的固定文本提取、配置就绪和招标要求解析替身，用于 Playwright 端到端回归，不参与生产路径 |
| `controller/` | API 边界 | 暴露项目级 tender document import、run/review/apply 接口 |
| `repository/` | JPA Repository | 读写 run、artifact、招标文件解析快照与 requirement items |
| `entity/` | JPA Entity | `bid_agent_runs`、`bid_agent_artifacts`、`bid_tender_document_snapshots`、`bid_requirement_items` |
| `dto/` | DTO | API 入出参和前端展示 payload |

## 纯核心边界

- `domain/*` 受 `FPJavaArchitectureTest` 保护。
- `domain/*` 不依赖 Spring、Repository、JPA、日志、IO、异常业务流、时间或随机数。
- 应用层只负责编排：取快照、调用纯核心、生成 artifact、写 run 状态、调用文档写入端口。
- `documenteditor` 写入只发生在基础设施适配器中，且必须尊重锁定章节和来源 metadata。
- 招标文件结构化拆解和草稿正文生成只有真实 AI 调用路径；项目绑定 `TENDER` / 标书草稿生成统一使用 DeepSeek Chat Completions，key 优先读取系统设置 DeepSeek provider，再读 `DEEPSEEK_API_KEY`，默认模型为 `deepseek-chat`。

## 招标文件到标书初稿链路

1. `POST /api/projects/{projectId}/bid-agent/tender-documents` 上传 `.doc`、`.docx` 或文本型 `.pdf` 招标文件。项目详情页独立解析入口由 `projecttenderbreakdown` 模块承载，底层复用本模块的导入应用服务；再次点击解析入口时会先复用最新解析快照，再尝试复用项目文档中已上传的真实招标文件，最后复用人工录入标讯时保存的 `doc-insight://` 源附件，不需要重复选择同一文件。
2. `infrastructure/tenderdocument` 保存文件并提取正文；扫描件 PDF 会显式提示需要 OCR/人工处理。
3. `infrastructure/openai` 使用 structured outputs 拆解项目名称、预算、地区、行业、发布日期、截止时间、招标范围、资格要求、技术要求、商务要求、评分标准、必须材料和风险点；无法从正文确认的结构化字段保持为空。
4. 解析结果写入 `bid_tender_document_snapshots`、`bid_requirement_items`，并在 Tender 对应字段为空时补充标题、采购人、预算、地区、行业、发布日期、截止时间、描述和标签。
5. `BidRequirementSnapshotReader` 统一读取最新招标文件快照对应的 requirement items，`BidDraftSnapshotAssembler` 和项目任务拆解都复用这条读模型，避免历史需求项漂移；Project 的预算、地区、行业、截止日期为空时，会从 Tender 的结构化字段 fallback 后再生成 run。
   项目任务拆解通过 `BidRequirementTaskSourceGateway` 适配 `projectworkflow` 的端口，避免 `projectworkflow` 直接依赖本模块实体。
6. 默认 `applyToEditor=true`，生成后立即写入 `documenteditor` 章节树；锁定章节仍由 `documenteditor` 跳过。

## 关键 API

- `POST /api/projects/{projectId}/bid-agent/tender-documents`
- `GET /api/projects/{projectId}/tender-breakdown/latest`
- `POST /api/projects/{projectId}/tender-breakdown/reuse-uploaded`
- `POST /api/projects/{projectId}/bid-agent/runs`
- `GET /api/projects/{projectId}/bid-agent/runs/{runId}`
- `POST /api/projects/{projectId}/bid-agent/runs/{runId}/apply`
- `POST /api/projects/{projectId}/bid-agent/runs/{runId}/reviews`
- `POST /api/projects/{projectId}/bid-agent/reviews`
