# AI 评分标准解析与打分模块（spec 041）

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明

`scoreparse` 承载项目详情页「AI 评分标准解析」抽屉（V3 双阶段闭环）的全部后端能力：

- **评分标准提取**：大模型结构化抽取招标文件评分规则（维度/细则/权重/原文定位），写入 `score_parse_tasks` + `score_items`（`ScoreParseAppService`，异步任务）；
- **五大知识库对标**：资质、人员、业绩、仓储网点、品牌授权自动匹配，输出 tier/ratio 与命中依据（`application/match/*`）；
- **双阶段打分**：阶段 1 基于知识库预计得分（`EstimatedScoreService`，客观项按匹配比例取整、主观项强制 null）；阶段 2 基于投标文件实际对标打分，输出得分/依据/原文引用/缺失原因/修改建议（`ScoreScoringAppService` + `OpenAiScoreAnalyzer`），主观项隔离为待专家评审；
- **高可靠机制**：30 分钟超时巡检（`ScoreParseTimeoutScanJob`，每 5 分钟扫描，`app.score-parse.timeout-minutes` 可配）+ 服务启动自愈（`ScoreParseTaskRecoveryRunner` 收敛重启期间卡死的 PROCESSING 任务）。

## 文件清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ScoreParseController.java` | Controller | 阶段 1/2 解析打分任务与结果接口 |
| `controller/KnowledgeMatchController.java` | Controller | 五大知识库匹配接口 |
| `application/ScoreParseAppService.java` | Application Service | 评分标准解析异步编排（触发/守卫/落库调度） |
| `application/ScoreScoringAppService.java` | Application Service | 阶段 2 实际打分异步编排 |
| `application/EstimatedScoreService.java` | Application Service | 阶段 1 知识库预估打分 |
| `application/ScoreItemPersistenceService.java` | Application Service | 评分项持久化与重解析覆盖清理 |
| `application/ScoreParseProgressService.java` | Application Service | Redis 进度缓存 |
| `application/ScoreParseTaskStateService.java` | Application Service | 任务状态机流转 |
| `application/BidDocumentUploadService.java` | Application Service | 投标文件上传（阶段 2 前置） |
| `application/TenderDocumentStoredListener.java` | Listener | 监听招标文件快照落库事件 |
| `application/match/*MatchService.java` | Application Service | 资质/人员/业绩/仓储/品牌五大匹配 |
| `domain/OriginalRuleRestoration.java` + `LogicalRuleSourceUnit.java` | Domain 纯核心 | 从闭合来源单元恢复完整规则/材料；XML 与物理页证明分开，未知范围保留完整模型候选并阻止自动覆盖 |
| `domain/ScoreTypeClassificationPolicy.java` 等 | Domain 纯核心 | 评分类型判定、满足状态判定、打分结果守卫 |
| `entity/` + `repository/` | 持久层 | `score_parse_tasks` / `score_items` / `score_results` 等表 |
| `infrastructure/openai/OpenAiScoreAnalyzer.java` | Infrastructure | LLM 解析与打分适配 |
| `infrastructure/scheduler/ScoreParseTimeoutScanJob.java` | Infrastructure | 30 分钟超时巡检（每 5 分钟扫描） |
| `infrastructure/bootstrap/ScoreParseTaskRecoveryRunner.java` | Infrastructure | 启动自愈，收敛卡死任务 |
| `dto/` | 数据契约 | 任务/清单/结果 DTO |

## API 入口

| Method | Path | 用途 |
|--------|------|------|
| `POST` | `/api/projects/{projectId}/score-parse/parse` | 触发评分标准解析（异步，覆盖旧解析结果） |
| `GET` | `/api/projects/{projectId}/score-parse/parse/status` | 解析任务进度轮询 |
| `GET` | `/api/projects/{projectId}/score-parse/items` | 阶段 1 评分项清单（含 estScore/estBasis/kbHit 与 summary） |
| `POST` | `/api/projects/{projectId}/score-parse/bid-documents` | 上传投标文件（阶段 2 前置） |
| `POST` | `/api/projects/{projectId}/score-parse/scoring` | 触发阶段 2 实际打分（异步，手动触发） |
| `GET` | `/api/projects/{projectId}/score-parse/scoring/status` | 打分任务进度轮询 |
| `GET` | `/api/projects/{projectId}/score-parse/results` | 阶段 2 打分结果（actualScore/evidence/quote/missedReason/suggestion） |
| `POST` | `/api/knowledge/cert\|person\|project\|warehouse\|brand/match` | 五大知识库匹配接口（供本模块与其他消费方复用） |

## 复用关系

- 项目权限守卫复用 `ProjectAccessScopeService`，所有用户入口在 Controller/Application Service 层校验；`@Async` 线程内的下游服务（预估打分、评分项持久化）不重复校验，守卫归属见 `project-access-guard-baseline.txt`；
- 招标文件正文复用 `bid_tender_document_snapshots` 快照（`TenderDocumentStoredListener` 监听落库事件），不重复解析文件；
- 知识库匹配读取现有资质/人员/业绩/仓储/品牌授权存储，部分字段降级匹配（无精确字段时按名称/等级模糊匹配），不新建知识库表；
- 前端消费方为 `ScoreParseDrawer.vue`（PR !2292），API 封装见 `src/api/modules/scoreParse.js`。

## 证书评分与预判更新（spec 081）

- `CertificateScoreRule` 完整解析已支持的证书评分段，未解释附加条件、材料引用或冲突条件保持待确认。
- `CertificateScorePolicy` 按标准身份、企业证书种类、等级、有效期逐项裁决；`CertMatchService.estimateScore` 将真实库存映射为事实，并保存输入指纹。
- `POST /api/projects/{projectId}/score-parse/estimates/refresh` 复用项目访问守卫，独立更新已生效证书项预判，不采纳候选、不改阶段2。
- `ScoreEstimateFreshness` 校验计算引擎、规则、证书事实和核验日；无记录或不一致为STALE，读取投影隐藏历史分且从汇总排除。当前指纹保守绑定全企业证书快照。
- nullable字段由V1225新增；迁移不重算、不提升历史满足状态。独立刷新有规则CAS及证书指纹校验，失败项可清空自身结果并保留其他新鲜项。
- 通用 `POST /api/knowledge/cert/match` 仍为兼容召回查询，其任一关键词命中不代表满足组合评分要求。

## 评分提取模型请求预算

- 独立配置 `APP_SCORE_PARSE_MODEL_TIMEOUT`（默认PT300S），评分SDK自动重试为0，其他AI业务保留原配置。
- 文字上下文按最多6000字符分片并保留重叠/全文覆盖。请求失败不整轮重发，不伪装为文档结构异常；原cause由异步入口记录，任务为FAILED，前端显示安全错误说明。
- 请求日志仅包含关联ID、模型及预算，不输出凭证或完整prompt；该ID通过X-Request-Id传到网关，便于查透传记录。
- 无方案标题或未知总分时保留未知状态，不自动补100分，待复核门禁不变。

- `BidFileQuotaException(actualBytes, maxBytes)`：单文件大小拒绝时携带已知大小下界和本次配额；下载比较条件与上限不变。

### 扫描投标PDF评分入口（2026-11-11）
`BidFileLoadingCoordinator` 的内存PDF与磁盘文件统一调用
`SidecarStreamTextExtractor.extractForScoring`，评分请求显式启用Sidecar完整扫描页OCR；
非评分的 `extract` 调用继续原有契约。`BidTextExtractionException` 只透传白名单安全原因，
任务errorMessage与各项missedReason保持一致；OCR未完成不调用评分模型。
OCR处理预算默认1500秒，评分HTTP等待默认1800秒；全卷耗时仍需真实文档验收。
