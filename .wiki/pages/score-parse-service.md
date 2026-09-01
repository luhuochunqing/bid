---
title: AI 评分标准解析后端服务
space: engineering
category: module
tags: [评分标准, scoreparse, LLM, 知识库匹配, 异步任务, 阶段打分]
created: 2026-08-15
updated: 2026-09-01
health_checked: 2026-09-01
sources:
  - specs/041-ai-score-parse-backend/spec.md
  - specs/041-ai-score-parse-backend/plan.md
  - specs/045-bid-file-streaming-scoring/spec.md
  - specs/046-fix-score-parse-ghost/spec.md
  - specs/047-fix-score-weight-gap/spec.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/
  - docs/implementation-notes/ai-score-parse-backend.md
  - specs/048-score-parse-image-multimodal/spec.md
backlinks:
  - _index
  - ai-capabilities
  - architecture
  - data-model
  - spring-pitfalls
---
# AI 评分标准解析后端服务

## 1. 定位（spec 041）

从招标文件评分办法中结构化抽取评分项，联动知识库完成「预计得分（阶段1）」与「实际打分（阶段2）」。独立于旧 `scoreanalysis` 模块（旧模块是前端手工表格，本模块是 LLM 解析 + 知识库匹配）。

## 2. 三张新表（V1187/V1188）

| 表 | 职责 | 关键约束 |
|----|------|----------|
| `score_parse_task` | 解析/打分任务（PENDING→PROCESSING→COMPLETED/FAILED） | `task_id` 唯一；`task_type` PARSE/SCORING；30min 超时扫描 |
| `score_item` | 评分项（解析产物） | FR-021: 重新解析按 `project_id` 覆盖清理；`score_type` OBJECTIVE/SUBJECTIVE 由域策略判定 |
| `score_result` | 打分结果（阶段1/2） | `score_item_id` 无 FK 级联，须先删 result 再删 item |
| `performance_record`（改） | 加 `contract_amount` 列（V1188） | 业绩匹配金额字段 |

## 3. 解析管线（US1，@Async 分钟级）

```
trigger（同步<1s，互斥校验+建任务）
  └→ executeParse（异步）
      Sidecar PDF 转换（pdfplumber 逐页抽取管道表格 + 换行保真）
      → 四路召回（关键词/文档结构/评分规则语义/LLM全文高信号聚焦切片）→ RecallOutcome(candidates, skippedChunks)
      → ScoreItemMergePolicy 合并去重（同维度同编号合并补强；跨维度同编号独立保留）
      → GhostCandidateFilter 幽灵过滤（spec 046 FR-001）
      → WeightSumCheck + ItemCountCheck 闭环校验（差异触发二次回补）
      → WeightOverloadTrimPolicy 超载剔除：合计>100.5 两轮剔嫌疑（spec 046 FR-002）
      → WeightNormalizationPolicy 权重归一化：合计偏离100（容差±0.5外且>0）按比例缩放+最大余数法收敛，落库和恒=100（spec 047）
      → ScoreTypeClassificationPolicy 客观/主观分类
      → ScoreItemPersistenceService 覆盖落库（ScoreItemOrderPolicy 编号数值序，空code落""）
      → EstimatedScoreService 阶段1预计得分（链尾）
```

- **整表级一次性 LLM 结构化抽取与主备平滑降级（2026-09-01）**：针对传统行级正则特征工程（`HeuristicTableColumnMapper` + `ScoreTableRowParser`）在跨行合并单元格、括号分值列和考核表混排中的脆弱性，引入 `LlmTableScoreExtractor` 与 `TableScoreExtractionPrompt`。通过单次 Structured Output JSON Schema 同时完成表格分类判定（剔除合同考核表与废标程序表）与多层级评分项抽取，`detail` 逐字复制并经 `ScoreOriginalTextAligner` 消除空白漂移；在 `ScoreParseAppService` 中作为主路径优先调用，超时/异常时平滑降级至既有启发式规则。
- **字段长度安全截断与双层防御（2026-08-31）**：大模型结构化抽取评分项时，`location`、`dim`、`code` 字段可能产生多段详细长文本。`ScoreCandidateMapper` 在映射源头做安全长度截断（`code: 50`，`dim/location: 200`，`source_text: 500`），`ScoreItemPersistenceService` 在落库前增加 `resolveLocation` 兜底截断，`ScoreParseTaskStateService` 对 `stage` 描述截断（50 字符），彻底根治 MySQL 严格模式下的 `DataTruncationException`。
- **评分细则（detail）保真升级与候选池完整透传（2026-09-01）**：`ScoreGlobalMergeExecutor` 将候选池单项 detail 截断上限提升至 4000 字符，避免大模型全局裁决时复述截断摘要；`ScoreMergeResultGuard` 在采纳项中引入 `upgradeWithLongestPoolDetail` 机制，当候选池中存在更长、更完整的逐字原文版本时自动升级，彻底解决 1/2/3 等多子要点条款被截断丢失的问题。
- **Sidecar PDF 转换保真（2026-08-24）**：针对复杂排版 PDF，Sidecar 优先使用 `convert_pdf_to_markdown` 通过 `pdfplumber` 抽取表格为 Markdown 管道表格，单元格内换行转 `<br>`，段落换行 `\n` 严格保真，彻底解决 `markitdown`/`pdfminer` 单行化（0 换行）缺陷。
- **跨维度去重防碰撞（2026-08-24）**：`ScoreItemMergePolicy` 区分同维度同编号合并与跨维度同编号保留，避免多标的/多部分招标文件（如商务表 1 与技术表 1）因编号相同被误吞。
- **LLM 全文聚焦切片（2026-08-24）**：`OpenAiScoreAnalyzer` 在多 chunk 时基于 sectionPath 和评分关键词过滤高信号 chunk，避免数十个无关合同 chunk 盲目调用 LLM 稀释信号。
- **正文来源（2026-08-16）**：立项 `TENDER` 优先，其次 Bid Agent `TENDER_FILE`，再兜底旧快照。不再要求走「启动 AI 生成初稿」。
- **自动解析门闩（spec 043）**：仅当从未有过 PARSE 任务且无评分项时自动新建；PENDING/PROCESSING 跟随已有任务；FAILED 展示原因、不自动重打。无源 400 会落 FAILED，避免每次打开抽屉再 POST。
- **读取上限**：远程/本地招标文件硬限制 50MB（先看 Content-Length，再流式累计）。超大无底稿提示「招标文件超过 50MB，无法解析」；有底稿则回退。`hasSource` 与 `resolve` 同一套成功条件。
- 事件挂链：`TenderDocumentStoredEvent` 仍可触发解析，但产品主路径是立项文件 + 评分抽屉
- 进度：Redis 缓存 by taskId（`ScoreParseProgressService`），非 DB 轮询

## 4. 知识库五类匹配（US2，`/api/score-parse/match/*`）

| 接口 | 数据源 | 降级匹配 |
|------|--------|----------|
| `cert:match` | 资质表 | 证书名称标准化后前缀匹配 |
| `person:match` | 人员表 | 证书/岗位/专业分字段匹配 |
| `project:match` | 业绩表 | 金额/类型/时间窗 |
| `warehouse:match` | 仓库表 | 库存数量语义 |
| `brand:match` | 品牌授权表 | 授权有效期校验 |

- 返回语义：`tier`（NONE/PARTIAL/FULL）+ `ratio`（0~1）
- tier 规则：0 命中=NONE；存在标记项/降级匹配/比例<100%=PARTIAL；100%=FULL

## 5. 阶段化打分（US3/US4）

| 阶段 | 触发 | 输入 | 输出 |
|------|------|------|------|
| 阶段1 预计得分 | 解析完成后自动 | 知识库匹配结果 | 客观项按 ratio 四舍五入取整；主观项强制 null；得分开区间 (0, weight) 钳位 |
| 阶段2 实际打分 | 手动（前置：投标文件已上传+解析完成） | 投标文件内容 | LLM 对标输出得分/依据/引用/建议；超区间得分置空；主观项仅保留建议 |

- 满足状态：客观项满分=OK / 零分=DANGER / 部分得分或过期=PENDING；主观项恒 PENDING
- 守卫：`ScoreAssessmentGuard`（域纯函数）统一钳位与置空
- **空值语义（spec 044 / PRD 1.3）**：客观项无预判得分（类别未识别 / 单项匹配失败）时 `est_score` 必须保留 `null` + `PENDING`，禁止兜底转 `0`（否则会被误渲染为红色 0 分）
  - 前端 `scoreParseTask.js normalizeScoreItem`：空值原样透传 null；`est_basis` 缺失时兜底"待人工确认预计得分"
  - UI：待确认（灰字 + 蓝点）≠ DANGER（红色 0 分），两者语义必须区分

## 6. 触发控制与超时（US5）

- `ScoreParseTimeoutScanJob`：定时扫描 PROCESSING 超 30min 任务 → 标记 FAILED（超时）；单任务失败不中断整批
- `ScoreParseTaskRecoveryRunner`：服务启动时恢复卡死任务（标记 FAILED + 保留上次成功结果）
- 任务互斥：同 project 已有 PENDING/PROCESSING 时 trigger 返回既有任务（幂等）

## 7. 关键工程约束（踩坑点）

- **FP-Java 分层**：`domain/` 纯函数（MergePolicy/ClassificationPolicy/WeightSumCheck/ItemCountCheck/SummaryAggregator/AssessmentGuard）可单测不依赖框架；`application/` 只编排
- **权限守卫链**：Controller → AppService 入口 `assertCurrentUserCanAccessProject`；异步管线内服务（EstimatedScore/ScoreItemPersistence/StateService/ProgressService）无 SecurityContext，靠 baseline 声明（见 `project-access-guard-baseline.txt`）
- **@Async 自代理**：`@Lazy @Autowired self` 解决自调用失效（同 spec 031 范式）
- **行预算 300**：ScoreParseAppService 拆出 ScoreItemPersistenceService；持久化细节测试同步迁移，勿在编排类里堆持久化断言
- **FR-021 覆盖语义**：重解析必须先删 `score_result`（by 旧 item IDs）再删 `score_item`，顺序不可反
- **UI 对齐（spec 044 / PRD 6.4-6.5）**：
  - 详情弹窗整体高度 ≤70vh（`el-dialog` 为 append-to-body 挂载，scoped 样式不可达，须用非 scoped 块 + `.el-dialog__body { overflow-y:auto }`）
  - 待确认状态 = 灰字 + 蓝点前缀（非 DANGER 红色）
  - 表格用 `table-layout: fixed` 时，窄列（编号 48px）放 nowrap/不可断内容会溢出叠压相邻列；tfoot 多格统计（nowrap 文案塞进 80px 满足状态列）同理全叠。修法：编号列加宽 + `word-break: break-all`，tfoot 改 `colspan=8` 单条 flex（`flex-wrap: wrap`）横向排布（2026-08-17 项目 225 UI 事故）
- **投标文件走 OBS 对象存储（2026-08-17）**：投标文件编制阶段通过 `useObsProjectDocumentUpload` 走华为云 OBS 直传（`obs-direct:xxx`），不设 50MB 业务上传限制。阶段 2 实际打分服务 `ScoreBidDocumentLookup` 透明兼容 `ObsShareUrlSigner` / `ProjectDocumentFileStorage` / `TenderDocumentStorage` 提取文本。
- **打分读取侧流式落盘 + 文本量设限（2026-08-17 spec 045 根治）**：50MB 字节硬限（PR !2314 止血方案）已废止，根治方案为流式落盘 + 提取后文本量设限（上限 500 万字 / 10MB）：
  - `ScoreBidDocumentLookup.loadBidFile` 返回 `LoadedBidFile` 二态（`InMemory(byte[])` / `OnDisk(Path)`）：OBS 链路走 `StreamingBidFileFetcher` 自适应 spill（内存 buffer 累计 ≤50MB 全程内存快路径，超出 flush `{tempDir}/{taskId}.part` 继续流式写盘；Content-Length 预检 + 单文件 512MB 配额即拒 `BidFileQuotaException`）；本地/doc-insight 链路维持内存快路径。
  - `TempBidFileStore` 管控并发总量 4GB 配额（`AtomicLong` 计数 + 写入 chunk 检查与超限回滚），并在任务终态（`finally release`）、超时扫描（`ScoreParseTimeoutScanJob`）、服务重启恢复（`ScoreParseTaskRecoveryRunner` 启动全清 `cleanupAll` 建立零基线 + recover 释放）四挂点彻底清理临时文件。
  - `BidFileLoadingCoordinator` 按二态分派提取——InMemory 走既有 `textExtractor`（快路径行为不变），OnDisk 走 `SidecarStreamTextExtractor`（sidecar 新端点 `POST /convert-stream` 流式上传 + text/plain 流式响应，>500 万字中止连接抛 `OversizedBidTextException`）；try-finally 保证 `.part` 用毕即释。
  - 错误语义收口：同步触发段 `BID_FILE_QUOTA_EXCEEDED` / `OVERSIZED_BID_TEXT` 转 400 对应异常 MESSAGE；异步执行段 `OversizedBidTextException` / `BidFileQuotaException` 走 `failScoringWithPending` 统一出口（状态 FAILED + fallbackPending 占位），旧 `OVERSIZED_BID_FILE` 与 `OversizedBidFileException` 全局清零。
  - 注意：`BoundedHttpDownloader` 不动（招标链路 spec 043 管辖继续用）。
  - **配额 512MB 双端对齐（2026-08-17 T025 终验修复）**：Java 端 `max-file-bytes` 与 sidecar `SIDECAR_MAX_UPLOAD_MB` 统一 512MB。教训：Java 端原配 2GB 而 sidecar markitdown 实际转换能力仅 ~512MB（1.62GB 文件转换时 sidecar RSS 飙到 4.8GB 被 OOM killer 击杀）——**配额必须按链路最弱环节设定**，Java 端放行 ≠ 下游扛得住；两端默认值注释双向锚定，改一端必须同步另一端。
  - **@Async 线程事务缺失（2026-08-17 T025 终验修复）**：打分成功/失败路径的 DELETE+INSERT 落库原直接调 `resultRepository`，在 `@Async` 线程（无事务上下文）抛 `TransactionRequiredException`。修复：抽 `ScoreResultOverwriter` 组件（`@Transactional replaceAtomically`），`ScoreScoringAppService` 只编排不落库——顺带满足行预算 300 拆分。陷阱细节见 spring-pitfalls §10.7。
- **prompt 模板是 Formatter 格式串**：`ScoreParsePrompts` 的 text block 配 `.formatted()`，模板内字面 `%` 必须写 `%%`（如示例文案 `占30%%`），否则 `UnknownFormatConversionException` 且与输入无关 100% 必炸（2026-08-17 项目 225 线上事故，详见 lessons-learned §117；回归测试 `ScoreParsePromptsTest`）
- **toCandidate 对 null section 必须降级**：召回一/三以 `null` section 调用 `toCandidate`，裸访问 `section.sectionTitle()` 即 NPE 全任务终止（2026-08-17 项目 226 事故，详见 §118；回归测试 `OpenAiScoreAnalyzerTest`）；新召回路或转换方法允许"无此数据"语义时必须显式处理 null 分支
- **扫描件输入的失败文案**：sidecar 提取文本 <10 字符（`low_text_density` 警告）时四路召回必然空手，最终报"未识别到评分标准章节"——文案有误导性，真实原因是文件无文本层（扫描件/图片型 docx）；排查时先看 `markdownLength` 而不是怀疑解析逻辑
- **前端抽屉：导入评分草稿/导出报告已下线（2026-08-17）**：`ScoreParseDrawer` 顶部操作栏（阶段标签 + 重新解析/导入评分草稿/导出报告）随功能取消整体删除。`useScoreParseDrawer` 的 `importToDrafts()`/`exportReport()`、`projects.js` 的 `importScoreDraftsFromAnalysis()`、`imported` emit 一并清理。重新解析入口保留三处：「招标文件解析」标题栏 / 错误重试 / 空态占位。注意：别再按 `importToDrafts` / `exportReport()` 这两个已删函数名检索。
- **前端抽屉：两节副标题与打分范围单选已删（2026-08-18 客户验收反馈）**：「（规则提取与满足预判）」「（标书对标打分与引用建议）」副标题及重新打分左侧「全部/仅不满足/勾选」单选删除，`useScoreParseDrawer` 的 `scoringScope`/`selectedItemIds` 死状态一并清理，打分固定 `scope=ALL`（`runScoring` 仍支持 options 透传 scope/itemIds）。合计栏「客观项/主观项」标签改为「客观项权重/主观项权重」澄清语义——客户反馈"合计得分不对"实为权重合计被误读为得分，得分合计以高亮分值为准（阶段1 预计/阶段2 实际）。
- **幽灵项三重根因与治理（2026-08-18 spec 046，project 212 权重 200 事故）**：LLM 把表头/汇总行当评分项召回（如 dim=detail="得分"、weight=100），去重不滤、权重校验只补漏不剔除，三项叠加导致落库权重合计 200。治理（三个 domain 纯函数，TDD 全覆盖）：
  - `GhostCandidateFilter`（FR-001）：规则1 dim 与 detail 归一化（trim+去空白）相同且 ≤4 字 → 表头回声滤除；规则2 code 空白且 sourceText 空白 → 非条款行滤除；有 code 或 sourceText 任一即保留（真实项零误删）。插在 merge 之后、weight 校验之前。
  - `WeightOverloadTrimPolicy`（FR-002）：合计 >100.5 时两轮剔嫌疑（先无 code → 再无 sourceText），逐个剔+重算达标即停；剔尽仍超保留现状（weightWarning 兜底）。插在完整性回补之后。
  - `ScoreItemOrderPolicy`（FR-003/004）：落库按编号数值序（`^\d+(-\d+)?$` major/minor，4-1<4-2），不可解析稳定置后；空 code 落 `""` 不再补行号假编号（旧逻辑假编号会与真实编号冲突）。
  - **LLM chunk 失败可见性（FR-005）**：单 chunk 失败重试 1 次，两连败跳过并计数 `RecallOutcome.skippedChunks` → `score_parse_task.skipped_chunks`（V1193）→ Meta 透出 `skippedChunks`。此前 chunk 静默跳过用户无感知，解析质量问题不可见。
  - **@Async 落库事务缺失第二处（2026-08-18 spec 046 部署验证发现）**：`ScoreItemPersistenceService.persistItems` 内的 `deleteByScoreItemIdIn`/`deleteByProjectId` 同样在 `@Async` 线程抛 `No EntityManager with actual transaction`，全任务在落库阶段终止（召回结果全丢）。修复：`persistItems` 加 `@Transactional`（跨 bean 调用代理生效）。与 §7 `ScoreResultOverwriter` 同类根因——**@Async 管线里所有含 bulk delete/save 的落库方法都必须显式开事务**，save 单条可侥幸、delete 必炸。
  - **LLM 网关"成功返回空候选"不触发重试也不计 skip（2026-08-18 测试环境事故）**：ai-tech.ehsy.com 的 qwen3.7-max 自某时刻起对候选提取 prompt 持续返回空 `candidates`（HTTP 200 + 合法 JSON + 空数组），召回四 `count=0, skipped=0` 零异常，三次稳定复现；切 `gemini-3.5-flash` 后同文档恢复 count=10。排查要点：skippedChunks=0 且 count=0 ≠ 代码回归，先换模型对照再查代码；此形态 FR-005 覆盖不到（重试只兜 HTTP/异常失败），只能靠 weightWarning + 完整性回补兜底。
  - **权重合计缺口根治：归一化（spec 047）**：spec 046 只对合计≠100 透 `weightWarning` 提示不修正，用户仍看到 90 缺 10 分。spec 047 改为在超载剔除后、落库前对候选集执行权重归一化——`WeightNormalizationPolicy`（纯核心，10 用例）对 `total>0 且 |total-100|>0.5` 按 `scale=100/total` 逐项缩放 + **最大余数法**确定性分配四舍五入余数（同余按 index 升序，保证可复现），落库合计恒=100 且保持相对比例。归一化发生则把原始合计写 `score_parse_task.original_total_weight`（V1194）并在 items 接口 `summary.originalTotalWeight` 透出（null=未归一化）；`weightWarning` 归一化后恒 false。前端据此可呈现"原合计 X，已补齐到 100"。
  - **ScoreParseAppService 行预算 300 临界**：spec 047 归一并排进编排类，文件卡在 300 行边缘——紧凑化手段（合并 record 参数、去冗余空行、内联单次调用）可过线，勿再往编排类加逻辑，继续拆分到子服务。
  - **分值业务边界与多层纵深防御（2026-08-26，Data truncation 根治）**：复杂文档误提取年份（2024）或金额（100,000,000）等大数导致原始合计 `total` 击穿数据库 `DECIMAL(10,2)` 上限抛 `Data truncation: Out of range value for column 'original_total_weight'`。三层防御：①`filterInvalidWeights` 约束单项 `0 < weight <= 100`；②`WeightNormalizationPolicy` 约束 `total <= 1000` 超限拒绝归一并返回 null；③`ScoreMergeResultGuard` 防大数假项回补 + `ScoreParseAppService` 强制 `setScale(2, HALF_UP)` 安全入库。详见 lessons-learned §164。
  - **评分项落库全字段非空与长度防御（2026-08-26，Column 'dim' cannot be null 根治）**：LLM 归并输出若缺少 `dim` 字段，直接透传会导致 `ScoreItem.dim` 为 null 触发 MySQL DDL `NOT NULL` 约束异常。治理：`ScoreGlobalMergeExecutor` 在 DTO 映射层通过 `resolveDim` 兜底（回退至 detail 前 50 字或"评分项"）；`ScoreItemPersistenceService` 在落库构建实体时统一进行 `resolveDim`（<=200 非空）、`resolveCode`（<=50）与 `detail` 判空保底，杜绝任何外部输入击穿数据库物理约束。详见 lessons-learned §166。
- **多模态图片：把 data-URI base64 拼进 prompt 文本 → Gemini 报 500 整任务 FAILED（2026-08-19 spec 048 测试环境回归）**：客观项应走「图片独立多模态 content parts 入模」，但打分 prompt 模板样板里把 `scoreItemDetail`/`bidDocExcerpt` 直接 `formatted()`，若其中含 sidecar `/convert` 返回的 `![...](data:image/png;base64,...)` 或截断裸 `data:` 片段，Gemini 解码失败抛"illegal base64 data" 500，整任务 FAILED（日志"打分任务异常终止"）。修复：`ScoreParsePrompts.promptSafe(raw)` 先正则剥离 markdown 内联图片占位符（`![...](...)`）与 `data:image/...;base64,` 裸 URI 段再格式化；`ScoreItemAssessor` 客观分支把 `DocumentImage` 映射为 `MultimodalImage` 走多模态过载，图片不进 prompt 文本以免双写。回归测试 +3 至 9 用例（`ScoreParsePromptsTest`）。
  - **验证基准（测试环境 Gemini-3.5-Flash 真消费完成）**：`/actuator/health` aiProvider=gemini-3.5-flash、apiKeyConfigured=true；修复后 project 224 含图样本打分任务 COMPLETED 100%（15:11→15:13），12 客观项 0.0/DANGER（诚实结果），其中 5 项 evidence 显式引用图片（"文字与图片/附图/资质证书图片 ISO 9001/27001"）——文本内 data-URI 已剥离，模型仍写出"图片"字样，反证图片确实从独立 content parts 入模。多图鉴权：**竞技场图片务必喂满 base64 完整段，截断占位符即是本次 500 根源**。
- **评分项/详细要素原文保真对齐（2026-08-20 客户验收反馈）**：客户反馈"识别出的评分项与详细要素文字和招标文件评分表原文不一致"。三层根因叠加：
  1. LLM 输出 dim/detail/sourceText 是模型复述——prompt"禁止摘要、禁止改写"是软约束，空白/断词漂移（全角→半角空格、中文断词加空格、跨行合并）无校验拦截；
  2. 正则召回（ScoringItemExtractor）按单行拆"编号+维度+指标+分值"，跨行单元格天然拆碎成短语；
  3. `ScoreItemMergePolicy.enrich` detail 首次出现优先 + 正则召回先入池 → 残缺短语版挤掉 LLM 完整版。
  治理三件套（domain 纯核心，TDD 全覆盖）：
  - `ScoreOriginalTextAligner.align(original, llmText)`：LLM 文本按 Unicode 空白拆段 → 每段 `Pattern.quote` → 段间空白弹性（零或多，兼容中文无空格+模型断词加空格）正则在原文定位；命中返回**原文精确片段**（逐字保留原文空白/换行/标点），未命中返回 null（调用方降级保留 LLM 输出）。
  - `ScoreCandidateMapper.fromLlmCandidate(candidate, sourceText)` 签名加来源原文参数（召回四传 sanitize 后 chunk 文本、回补传节选）：detail/dim/sourceText 依次对齐回填；detail 命中时 sourceText=detail（原文背书包含关系）；**对齐失败时 sourceText 置空**（google-code-review 阻塞项修复）——LLM 复述的 sourceText 自包含 detail 会让背书判据对幻觉文本天然成立（伪背书），source_text 列只允许携带原文验证过的内容，防幻觉闭环。
  - `ScoreItemMergePolicy.enrich`：later.detail 有原文背书（`sourceText.contains(detail)`）且比 base 更长 → 覆盖首次版本（跨行条款取完整原文）；无背书即使更长也不覆盖（防 LLM 幻觉长文本污染）。同编号不同名称（两路 dim 表述不一致，如正则"技术部分" vs LLM"技术方案"）时以"base.detail 是 later.detail 的归一化子串"判同项补强，互不为子串的真不同项撞编号维持丢弃（review P1 修复，防误并子串判据）。
  - 可观测性：Mapper 对 detail 对齐失败记 WARN（含 code/dim/detail 头部 60 字）；召回四完成日志带 `aligned=N/M` 对齐率（判据：detail 为 chunk 原文精确子串）。对齐率低 = 模型概括改写多，重新解析前先换模型对照（同 §7 空候选事故排查思路）。
  副作用：前端零改动（detail 落库已保真）；**存量 score_item 数据不回溯，需重新解析才生效**；dim 对齐失败（LLM 归纳的维度名）保留 LLM 输出属预期。
- **前端表格 UI 调整（2026-08-20 客户验收反馈）**：实际评分列"重评"标签（`reuseKind==='FRESH'`）删除，"沿用"（REUSED）保留；tfoot 顺序调整为「满足统计（靠左）→ 合计+总权重 → 客观项权重·主观项权重 → 高亮得分」，`margin-right:auto` 从 `.tfoot-label` 移至 `.tfoot-stats`。别再按"重评"文案检索已删标签。

## 8. 验证基线

- 单测：解析编排/持久化覆盖/匹配五类/预计得分/阶段2打分/超时扫描/启动恢复 全绿（~143 用例含 biddraftagent 回归）
- **spec 046 测试环境真实验证（2026-08-18，project 212）**：7 项全真实评分项（幽灵"得分 得分 100"行已消失）、DB item_index 1-7 与 code 数值序一致、`skipped_chunks=0` 落库且 meta 透出、事务修复后落库 COMPLETED。对照修复前 200 事故已根治。
- **spec 047 权重归一化（2026-08-18）**：`WeightNormalizationPolicyTest` 10 用例全绿（缺口 90→100 / 超载 200→100 / 恰好 100 透传 / 容差 99.6 透传 / 空候选 / 相对比例保持 / 确定性可复现 / 顺序无关收敛 / 仅改 weight / null 权重）。编排类行预算 300 达标，checkstyle 改动文件 0 violations，架构测试 43 通过。落库合计恒=100，缺口不再以 `weightWarning` 提示收场。
- **spec 047 收敛单测（2026-08-18 C001）**：`OriginalTotalWeightSignalTest` 5 用例锚定 US3 信号链路——`Summary.originalTotalWeight` Jackson 序列化（非 null 值 `:90` / null 键存在且为 null）、`ScoreParseTask` 反射断言 `@Column(name="original_total_weight")` 映射存在、getter 读写闭环 + `getItems` 透出映射语义（latest 非 null 携带值 / null 透传 null）。配 C002 日志重算，Convergence 闭合。
- **原文保真对齐（2026-08-20）**：`ScoreOriginalTextAlignerTest` 6 用例 + `ScoreCandidateMapperTest` 3 用例 + `ScoreItemMergePolicyTest` 10 用例（新增背书覆盖/无背书保留 2 例）+ `OpenAiScoreAnalyzerTest` 回归 6 用例全绿（25/25）；前端 `ScoreParseV3.qa.spec.js` 8 用例通过、`npm run build`（含全部治理检查）EXIT=0。
- 架构：ArchitectureTest + FPJavaArchitectureTest + ProjectAccessGuardCoverageTest 通过
- **报价评分跨页截断治理与评审修复（2026-08-25）**：
  - **核心痛点**：PDF 转换时跨页报价表格被切开（下浮率/BP%/HP% 等参数散落各页），导致 LLM 提取时仅见前半截截断，或把计算参数误拆为 80 分独立项。
  - **治理四层协同**：
    1. `converter.py`：跨页报价表链式合并（多信号交集：报价关键词 ∩ 主体列数一致 ∩ 间隔≤3 空行，支持 3+ 页连续吸收，跨页重复表头去重，短词 `\b` 词边界匹配）；
    2. `PriceScoringKeywords`：中心化报价领域常量（单一真相源），下沉至 `biddraftagent.domain` 维持干净单向依赖；
    3. `ScoringItemExtractor`：重构管道行分类器 `classifyPipeRow`，三道判据（章节别名守卫 → 参数行/主项行区分 → 评分行保留），带序号参数行跳过，报价主项行保留；
    4. `MarkdownScoreSectionLocator`：孤立块兜底收紧为 `countDistinctLongParams >= 2`（嵌套词单次计数），上下文行数差异化（普通 3 行 / 报价 8 行）；
    5. `OpenAiScoreAnalyzer`：`filterRelevantChunks` 邻近扩展使用独立数组仅扩张一跳，避免链式蔓延；
    6. `ScoreItemMergePolicy`：报价类跨 chunk 互补拼合，引入 `PRICE_DETAIL_MAX_CHARS = 6000` 上界与行级 trim 去重；
    7. `scripts/check-keyword-sync.mjs`（pre-push §5.6 门禁）：Python/Java 跨语言关键词自动化同步校验。
- **标书原文保真直抽与 8 列标准排版（2026-08-25）**：
  - 前端 `ScoreParseTable.vue` 表格列顺序严格重构为：1 编号 (`code`) → 2 标书原文 (`detail`，自适应多行排版并保留换行) → 3 评分项 (`dim`，大类/维度) → 4 权重 (`weight`) → 5 满足状态 (`status`) → 6 评分类别 (`scoreType`) → 7 预计/实际得分 (`estScore`/`actualScore`) → 8 得分依据（详情操作）；
  - 前端详情弹窗 `ScoreItemDetailModal.vue` 首项文案重命名为「标书原文」，并在存在出处时展示「招标文件位置出处（如 `P47 评分办法表 第3行`）」；
  - 后端 `ScoreParsePrompts.java` 严格规定 `detail` 必须为招标文件评分表格/条款中的评审细则逐字原文，绝对禁止任何形式的 AI 摘要、概括、缩写或改写，且权重列准确识别数值；
  - 知识库 5 大库自动对标与 100 分闭环校验完整保留并与新 8 列结构完全兼容。


## 全局归并裁决 + 表格行保真（spec 055，2026-08-26）

- **背景**：项目 1347 暴露两类管线级缺陷——同一条款重复成项（S9/A2）、报价维度特判把同维度多个无编号独立项吞并成一项（S28 吞掉 4 分"价格管控方案"）；根因是启发式合并用不稳定的 LLM 输出字段（编号/维度）做身份主键，且 4000 字硬滑窗切片会拦腰斩断表格行。
- **两阶段架构**：召回（四路+召回五表格行直抽）允许重复 → 启发式合并仅做预降噪 → **全局归并裁决**（`ScoreGlobalMergeExecutor` 单次 LLM：候选池摘要 + 评分章节原文 → 最终清单）→ `ScoreMergeResultGuard` 双向防御（幻觉 detail ⊄ 原文即弃；池中高置信漏项回补）。
- **表格行保真**：`ScoreTableAwareChunker` 包装召回四切片（超长表格 chunk 按行边界重切，边界永不落在行内）；`ScoreTableRecallLane` = `ScoreTableBlockLocator`（评分语义块定位，剔除表头/分隔行）+ `ScoreTableRowParser`（一行=一候选，纯分值列优先/档位取 max/dim 从大类名列提取）。
- **fail-open**：LLM 两连败/候选<2/守卫全滤 → 保持启发式结果，任务不失败；摘要日志含 pool/merged/accepted/hallucinationDropped/restored 计数。
- **教训**：合并身份判定不要依赖 LLM 输出的编号/维度（跨 chunk 漂移）；文本相似度 + 原文背书才是可靠信号；LLM 裁决必须配确定性双向防御（幻觉过滤 + 漏项回补）。

## 须知前附表过滤与评分候选防污染（2026-08-27，PR !2427）

- **判定特征**：表格首行含"编列内容"（前附表专有列名）且不含"评分因素/评分标准"→ 认定为须知前附表，整表跳过（`MarkdownScoreSectionLocator.isProcedureNoticeTable`）；混合表头保留不跳过。判定理由唯一出处在该方法 Javadoc。
- **为什么必须在结构层**：前附表正文常见"综合评分/综合得分"字样会命中 TABLE_SCORE_PATTERN 的通用子串，开启程序/履约保证金/成交结果公示等程序条款整表误入候选池——token 浪费且输出污染，LLM 层拦截最不可靠也最贵。
- **双层 prompt 配套**：候选提取禁令 3 条（前附表程序条款、形式/资格/响应性评审条款、总分构成汇总行）+ 全局归并规则第 6 条（混入必丢弃），均有 contains 锚定测试防静默删改。
- **已知盲区（技术债）**：空行截断续块失去首行 marker、前附表与真评分表纵向胶连时整块吞掉真实评分行、纯文本通道未覆盖——详见 docs/lessons §167，待后续 spec 消化。

## 金标回放基线与运行遥测（spec 057，2026-08-27）

- **守卫语料完整性（FR-01~03）**：`ScoreGlobalMergeOrchestrator` 定位失败即跳过归并（禁用全文前缀裁决）；截断时与池身份匹配的输出走 `poolBackedKept` 放行——hallucinationDropped 只统计真幻觉。
- **治理链抽离（FR-07/D5 预案兑现）**：`ScoreCandidatePostProcessor` 承接合并→守卫→裁决→回补→权重链；AppService 300→238 行；`countAligned` 迁 Mapper。
- **run-summary（FR-04~06）**：`score_parse_task.run_summary` JSON 持久化 lanes/aligned/merge 计数/model，items meta 透出；对齐率<50% 或幻觉比>30% WARN。
- **金标回放（FR-08~10）**：`GoldenCasesReplayTest` 合成夹具确定性六件套回放进默认套件（pre-push 等效第一层）；scripts/eval-scoreparse 承载标注规范与两层执行说明。首跑 case-dual F1=83.3%（4FP=前附表在 table lane 泄漏，§167 盲区实证）/ case-weight-gap F1=100%。

## 1347 多标包与复合表格边界修复（2026-08-28，PR !2459 & 修复补丁）

- **复合编号排序保真**：`ScoreItemOrderPolicy` 支持 `COMPOUND_CODE_PATTERN`（如 `2.2.4(1)` / `3.1(2)`），提取首段整数与括号内子段数值，保留文档中自然出现的顺序，防止非标准编号被当成 `unparsed` 稳定沉底到最后一行。
- **百分比权重列识别**：`ScoreTableRowParser.PURE_WEIGHT_COLUMN` 扩展为 `^[~≈]?\d{1,3}(\.\d+)?\s*(?:分|%)?$`，正确识别 `6%` / `10%` 等以 `%` 结尾的纯分值列，解决三列表格漏项问题。
- **长文本章节截断防误切**：`MarkdownScoreSectionLocator` 将连续非评分行容忍阈值由 10 提升至 20，并引入 `GRADING_PATTERN`（分档打分/A、B、C、D 级等语义行），防止大段文本表格单元格被转换器切碎时被提前截断。

## 单元格锚定与金标基准防回退引擎（spec 058，2026-08-28）

- **2D 网格实体（`TableGrid`）**：将拍平的 1D 文本流重新封装为结构化的行列网格，支持跨多行/跨页表格抽取与纵向合并单元格自动补全（Fill-down）。
- **LLM 列语义映射（`TableColumnMapping`）**：大模型仅负责分析前几行采样推断列语义属性（`code_col`, `dim_col`, `detail_col`, `weight_col`），不生成任何细则文字，彻底杜绝幻觉与概括篡改。
- **确定性物理单元格直抽（`CellAnchoredScoreExtractor`）**：直接读取物理单元格原文，100% 逐字保真，天然保持文档物理行出现顺序。
- **列类型交叉验真守卫（`ColumnTypeCrossVerifier`）**：强制校验权重列有效分值占比（≥40%）与细则列平均字符长度（≥4.0），防止大模型采样错判引发全表列错位。
- **程序性条款多层拦截守卫**：在 `CellAnchoredScoreExtractor`、`ScoreTableRowParser` 与 `GhostCandidateFilter` 中建立统一的 `NON_SCORE_PROCEDURE_PATTERN` 拦截器，自动过滤 `推荐成交候选|成交候选|定标|保证金|监督` 等非评分程序条款及第 4 节程序条款，防止其作为幽灵分值项破坏权重归一化。
- **权重与细则精准对齐防线**：在抽取与全局归并阶段增加提示词级权重就近对齐与逐项验真规则，防止无表格线多行文本中相邻评分项（如信用评价 5% 与财务状况 6%）发生权重颠倒错位。
- **并列能力评分项完整提取与分值闭环防线**：在候选提取、完整性复查与全局归并中建立并列能力子项（如技术部分 6 项各 5% 包含运营服务能力等）防穿插遗漏规则，确保大项子项分值闭环，彻底杜绝因漏项触发下游归一化余数拼凑失真。
- **金标自动化回归套件（`GoldenCasesReplayTest`）**：收录 1347（复合编号+百分比权重+程序表防污染+单项权重精确对齐+全并列项闭环）、dual-section（跨维度重号）、weight-gap 等典型真实标书，确保每次改动必须 100% 通过且 F1=1.0，构筑坚实防回退底线。

## 合同履约考核表过滤与外部系统评分声明诊断（2026-08-31）

- **合同履约考核表拦截**：文末合同附件常见《供应商季度/年度合同履约考核表》（考核项目、考核内容、分值，如周期保证 25 分、质量保证 25 分等，总分 95 分），属于合同履约期管理指标而非评标打分项。在 `ScoreParseConstants` 统一定义 `NON_SCORE_PROCEDURE_PATTERN` 与 `CONTRACT_ASSESSMENT_PATTERN`，并在 `GhostCandidateFilter`、`ScoreTableRowParser`、`CellAnchoredScoreExtractor`、`ScoreTableBlockLocator`、`HeuristicTableColumnMapper` 五层全面拦截，防止非评分项进入候选池破坏 100 分闭环。
- **外部交易系统评分声明识别与精准诊断**：部分标书（如中国一汽 MRO 标书）正文声明“评分标准详见 ZBJ 格式采购文件或电子交易平台‘评审项目’页签”，正文无评分细则。`MarkdownScoreSectionLocator` 识别该外部声明并在正文处截断扫描；当有效候选数为 0 时，`ScoreCandidatePostProcessor` 精准输出诊断错误文案，防止因归一化强行缩放伪造满分。

## 评分项编号排序策略层级隔离（2026-09-01）

- **多大类局部编号防杂糅**：在复杂标书中，不同大类（商务评分、报价评分、技术评分）及各父维度（财务状况、服务网点）下均存在各自局部的子项编号 "1"、"2"。`ScoreItemOrderPolicy` 遵循四层排序纪律：
  1. 顶级大类（商务评分 -> 技术评分 -> 报价评分）首次出现顺序（自然出现序）；
  2. 所属父维度（财务状况 -> 服务网点）首次出现顺序；
  3. 同父维度内部按局部编号（1, 2, 3...）数值序重排；
  4. 同组内无编号或无法解析项排在该组有编号项之后，并严格保持原始相对输入序。
- 彻底杜绝脱离层级上下文的跨大类全局扁平拍平排序。

