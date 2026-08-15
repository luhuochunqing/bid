# Research: AI 评分标准解析 — 后端服务

**Date**: 2026-08-15 | **Branch**: `agent/mimo/ai-score-parse-backend`

调研方式：4 个并行只读代码侦察（biddraftagent 模块 / 五类知识库实体 / 异步任务与事件模式 / 权限与迁移模式），以下决策均基于仓库现状事实。

## R1. 模块落点：新建 `scoreparse` 业务域包，不塞进 biddraftagent

**Decision**: 新建 `backend/src/main/java/com/xiyu/bid/scoreparse/`，按 controller/application/domain/infrastructure/entity/repository/dto 分层。

**Rationale**:
- biddraftagent 已有 47 个测试文件、14 个端点，再塞入解析+打分+5 match 会违反 Constitution IV（Split-First）
- Constitution I 要求包名按业务域划分；评分标准解析是独立业务域（评分项/打分结果/知识库匹配），与标书草稿生成不同域
- 基础设施通过接口复用 biddraftagent 已有能力，不重复建设

**Alternatives considered**: 扩展 biddraftagent（否决：域混淆 + 体量）；新建独立 Maven module（否决：仓库为单 module，违反 boring 原则）。

## R2. LLM 集成：复用 biddraftagent 现有通道

**Decision**: 复用 `OpenAiStructuredOutputService`（结构化 JSON 输出 + json_schema 失败自动降级 ResponseFormatJsonObject）、`OpenAiBidAgentConfigurationResolver`（多 provider 动态解析）、`TenderDocumentPrompts` 模式新增评分解析专用 prompt 模板、`TenderIntakeTextProcessor.sanitizeUntrusted` 防 prompt 注入。

**Rationale**: 已验证的生产通道（OpenAI SDK + deepseek/豆包/通义千问多 provider + 超时配置），新增 useCase 即可；LLM 客户端超时 90s 兜底已有。

**Alternatives considered**: 新建 LLM client（否决：重复建设 + 配置漂移）。

## R3. 文件内容提取：sidecar/markitdown → markdown 全文

**Decision**: 复用 `TenderDocumentTextExtractor`（markitdown sidecar `/convert` + 健康预检 + `X-Sidecar-Key` 鉴权）与 `TenderDocumentStorage`（docinsight，category 区分 `tender-file`/`bid-file`）。

**Rationale**: 现有链路已把 PDF/Word/Excel 转 markdown 喂 LLM，评估文件与招标文件同构。

**Alternatives considered**: 直接把文件 URL 交给多模态模型（否决：与现有 chunk 切片模式不一致，成本与不可控性更高）。

## R4. 四路召回实现映射

**Decision**:
- 召回一（关键词及规则）：复用/增强 `RegexKeywordMatcher` + `ScoringItemExtractor`（正则兜底已有 4 种格式识别）
- 召回二（文档结构）：新增 markdown 表格/章节层级解析器（纯核心，按表格行列与标题编号识别候选区域，保留前后文）
- 召回三（评分规则语义）：LLM 分段扫描（chunk 级 prompt，识别"条件→得分/数量→分值"语义特征）
- 召回四（LLM 全文语义）：复用 `OpenAiStructuredOutputService` 多轮 chunk 分析（`TenderDocumentPrompts.buildFullTenderPrompt` 同款切片模式）
- 四路并行进候选池 → domain 纯核心 `ScoreItemMergePolicy`（按原文位置/名称/规则/语义相似度合并去重，只合并重复、不删相似）

**Rationale**: PRD §1.2 四路召回与现有基础设施逐路对得上；合并去重是纯函数，可确定性单测。

**Alternatives considered**: 单次 LLM 全文提取（否决：PRD 明确要求多路召回不设覆盖关系，且单次提取对跨页/脚注遗漏率高）。

## R5. 异步任务：spec 031 四件套 + 新增超时扫描 job

**Decision**: 复用 `TenderImportAppService` 已验证模式：
- trigger（同步入口，@Idempotent，返回 202 + taskId）→ `@Async("scoreParseExecutor")` 自代理调用（`@Lazy` self 注入规避 AOP 自调用失效）
- 新 executor bean 挂 `MdcTaskDecorator`（core=1/max=2/queue=20，LLM 长任务低并发）
- 任务状态机 `score_parse_task` 表：PENDING/PROCESSING/COMPLETED/FAILED + 三层降级 failTask 范式（CO-469）
- Redis 进度：`Optional<StringRedisTemplate>` 优雅降级，key `score:parse:progress:{taskId}` / `score:scoring:progress:{taskId}`，TTL 7d
- **30 分钟超时**：spec 031 只有启动时 RecoveryRunner、无运行中超时；新增 `@Scheduled(fixedDelay)` 超时扫描 job（复用 `findByStatusAndUpdatedAtBefore` 模式，阈值 30min，超时任务置 FAILED、保留上次成功结果）

**Rationale**: 全部为仓库已验证模式（AsyncConfig createExecutor 统一挂 MdcTaskDecorator；Redis key 规范 `模块:子域:用途:{id}`）。

**Alternatives considered**: tenderupload 的 TenderTaskWorkerService 任务队列（否决：面向高吞吐轮询抢占，本场景为低频长任务，过重）；WebSocket 推进度（否决：spec 假设已定轮询）。

## R6. 阶段 1 自动触发：Spring 事件解耦

**Decision**: `BidTenderDocumentImportAppService.parseTenderDocument` 在文档保存成功后 `publishEvent(new TenderDocumentStoredEvent(projectId, documentId, fileUrl))`（侵入 1 行 + 事件 record）；scoreparse 模块 `@Async @EventListener` 消费并触发解析任务（同项目解析任务互斥，进行中则跳过）。

**Rationale**: file 模块 `BidFileUploadedEventHandler` 已示范 `@Async @EventListener` 范式；biddraftagent 上传链路（multipart → ProjectDocument）与 OBS 直传链路独立，直接在 biddraftagent 保存点发事件侵入最小。

**Alternatives considered**: 前端上传后调触发接口（否决：PRD §1.1 要求"自动触发"，依赖前端不可靠）；监听 BidFileUploadedEvent（否决：OBS 链路与招标文件 multipart 链路不同源，事件不含业务类型）。

## R7. 知识库字段缺口处置

**Decision**（对照 PRD §3.3 匹配条件逐类核实）:
| 模块 | PRD 匹配条件 | 现状 | 处置 |
|---|---|---|---|
| 资质 businessqualification | 名称关键词+等级+有效期 | name/level/expiryDate/status 齐全 | 无缺口，直接匹配 |
| 人员 personnel | 岗位+资质证书 | technicalTitle + personnel_certificate 子表齐全 | 无缺口（PRD 匹配条件不含项目经验） |
| 业绩 performance | 类型+时间范围+**合同金额门槛** | 缺 contract_amount | **V1188 补列**（nullable DECIMAL） |
| 仓库 warehouse | 名称+位置+面积+设施 | 缺设施字段 | **降级**：remarks 文本匹配，命中按部分得分语义 |
| 品牌授权 manufacturer_authorization | 品牌名+**授权范围**+有效期 | 缺授权范围自由文本 | **降级**：productLine(38 枚举)+importDomestic 近似表达范围 |

**Rationale**: 只补 PRD 硬性要求的合同金额（数量计分公式依赖门槛比对）；设施/授权范围降级匹配避免大表结构变更，已在 evidence 文本中说明降级原因。旧表 `brand_authorization_deprecated` 不使用。

**Alternatives considered**: 五张 knowledge_* 新表（否决：与现有五模块存储重复，数据双写漂移）；全字段补齐（否决：设施/授权范围非结构化必需，YAGNI）。

## R8. 权限：跟随 biddraftagent 现行模式，不新增 permissionKey

**Decision**: 全部接口类级/方法级 `@PreAuthorize("isAuthenticated()")`；Service 层每个入口调 `ProjectAccessScopeService.assertCurrentUserCanAccessProject(projectId)`。5 个 match 接口同样 `isAuthenticated()`。

**Rationale**: PRD §5.4 只要求"投标任务的访问权限"（项目级），未要求菜单权限；biddraftagent 全模块 14 端点均为该模式（Constitution VI 允许形态，项目级下沉 Service 层 Policy）；避免动 `RoleProfileCatalog`（entity/ 热路径）与角色迁移，减少 2 个 agent lock。

**Alternatives considered**: 新增 `ai-scoring.parse` permissionKey + V 迁移同步 4 角色（否决：PRD 无菜单权限诉求，属过度设计）。

## R9. 数据库迁移

**Decision**: 两个迁移（版本号以 `scripts/next-migration-version.sh --reserve` 实际输出为准，当前本地最大 V1186，预计 V1187/V1188）：
- V1187：建 `score_parse_task` / `score_item` / `score_result` 三表（各配 U 回滚）
- V1188：`performance_record` 加 `contract_amount` DECIMAL(15,2) NULL（配 U 回滚）

**Rationale**: 特性核心三表内聚一个迁移；跨模块字段补充独立成迁移便于回滚隔离。hot-path lock 需覆盖 `db/migration-mysql/**` 与 `db/rollback/migration-mysql/**`（implement 阶段 acquire）。

**Alternatives considered**: 单迁移全含（否决：performance 列变更与新建表回滚策略不同）；三表三迁移（否决：同特性同生命周期，无独立交付诉求）。

## R10. 匹配计分与打分输出

**Decision**:
- 五类匹配器为 domain 纯核心（复用 `bidmatch/domain/MatchEvidence` record 风格）：资质/仓库/品牌授权分档（FULL/PARTIAL/NONE + matchRatio）、人员比例（符合/要求）、业绩数量（实际/要求）
- 阶段 2 LLM 打分输出 schema：`ScoreAssessmentOutput`（actualScore/evidence/quote/matchRatio/missedReason/suggestion），后端校验 [0, weight] 区间（domain 纯核心 `ScoreRangeGuard`）
- 部分得分 = weight × matchRatio 四舍五入取整，开区间 (0, weight)——domain 纯核心 `PartialScorePolicy`

**Rationale**: 计分公式全部确定性可单测（SC-002 依赖）；LLM 只负责证据定位与文本生成，数值计算不下放给模型。

**Alternatives considered**: LLM 直接输出最终得分（否决：数值一致性不可控，PRD 校验规则要求后端守卫）。
