---
title: AI 评分标准解析后端服务
space: engineering
category: module
tags: [评分标准, scoreparse, LLM, 知识库匹配, 异步任务, 阶段打分]
created: 2026-08-15
updated: 2026-11-06
health_checked: 2026-11-06
sources:
  - specs/065-tender-large-file-parse/spec.md
  - specs/041-ai-score-parse-backend/spec.md
  - specs/041-ai-score-parse-backend/plan.md
  - specs/045-bid-file-streaming-scoring/spec.md
  - specs/046-fix-score-parse-ghost/spec.md
  - specs/047-fix-score-weight-gap/spec.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SidecarSchemaContract.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/StructuredTableExtractionHandler.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreReviewCandidateFactMapper.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSummaryPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreCompletenessPolicy.java
  - backend/src/test/resources/scoreparse-golden/case-misaligned-summary-row.md
  - specs/062-score-multi-table-split/spec.md
  - specs/062-score-multi-table-split/plan.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/DeterministicScoreFactExtractor.java
  - backend/src/test/resources/scoreparse-golden/merged-parent-first-leaf-table-v2.json
  - backend/src/test/resources/scoreparse-golden/case-multi-table-tech-commercial.md
  - document-converter-sidecar/converter.py
  - document-converter-sidecar/app.py
  - document-converter-sidecar/README.md
  - backend/src/main/resources/application.yml
  - backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplaySupport.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/golden/ScoreParseRealCorpusRecorderTest.java
  - scripts/eval-scoreparse/record-real-corpus.sh
  - docs/implementation-notes/ai-score-parse-backend.md
  - specs/048-score-parse-image-multimodal/spec.md
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
backlinks:
  - _index
  - ai-case-recommend-data-source-fix
  - bid-quality-check-pagination-fix
  - case-precipitation-streaming
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

- **评分类型（主客观分）精准判定规范（2026-10-26）**：针对招标文件商务/财务项（如销售额、营业收入、净资产等）常包含“优先/优质/一般纳税人”等混淆词汇导致的误判问题，明确判定原则：
  1. **描述词收敛**：`DESCRIPTIVE_PATTERN` 中收敛 `优[良秀]?` 为 `(优秀|优良|优等|优(?=[、，,和与及/]))`，杜绝匹配“优先/优质/优惠/择优/优于”；`一般` 必须使用负向零宽断言 `一般(?!纳税人)`，排除“增值税一般纳税人”；
  2. **量化财务特征扩充**：`QUANTIFIED_PATTERN` 覆盖财务量化指标（`营业额|营业收入|营收|合同额|合同金额|开票额|开票金额|纳税额|资产总额|净资产|注册资本|注册资金` 等）与阈值比较词（`[≥>≤<]|不低于|不少于|不小于|超过|达到|满足`）；
  3. **门槛与分值隔离**：抽取静态常量 `QUALIFIED_NUMERIC_PATTERN`，严禁在量化门槛正则中引入 `满` 或分值 `分`，防止常规主观方案（如“售后服务方案满分10分”）被反向误判为客观分；无法明确判定时坚守保守原则归为主观分。
  4. **方案性与分档打分主观识别（2026-11-06）**：`DESCRIPTIVE_PATTERN` 扩充招投标方案评审核心特征，覆盖分档打分（`分档打分|分档评审|按[A-D]+分档`）、专家评审主体（`专家打分|评标委员会评审`）及方案定性评价（`综合评价|综合评审|针对性|可行性|合理性`）。遵循核心判定准则：需要专家评审的方案性、设计性内容归为主观分，可通过比对标书内容和招标要求验证是否满足的归为客观分；主观规则优先于量化特征匹配，避免方案细则中偶然提及的“认证/数量”被贪婪误判为客观分。
- **确定性优先 + 章节定位约束的整表 LLM 智能抽取（2026-09-01）**：针对传统行级正则特征工程与非标复杂表格的冲突，确立「确定性启发式/网格抽取主路优先 + 评分章节约束下的整表 LLM 智能兜底」架构。对于标准招标表格优先走确定性网格解析（0 网络开销、毫秒级响应、100% 规则保真）；当非标复杂排版（跨行合并单元格、异构列头）未命中时平滑启用 `LlmTableScoreExtractor`。LLM 抽取时强制以 `MarkdownScoreSectionLocator` 章节定位（如第三章 评标办法）为范围边界，并严格拦截 `isProcedureNoticeTable`（前附表）与 `CONTRACT_ASSESSMENT_HEADER`（合同履约考核表），彻底防止非评分表格混入候选池导致总分膨胀与裁决失真。
- **字段长度安全截断与双层防御（2026-08-31）**：大模型结构化抽取评分项时，`location`、`dim`、`code` 字段可能产生多段详细长文本。`ScoreCandidateMapper` 在映射源头做安全长度截断（`code: 50`，`dim/location: 200`，`source_text: 500`），`ScoreItemPersistenceService` 在落库前增加 `resolveLocation` 兜底截断，`ScoreParseTaskStateService` 对 `stage` 描述截断（50 字符），彻底根治 MySQL 严格模式下的 `DataTruncationException`。
- **评分细则（detail）保真升级与候选池完整透传（2026-09-01）**：`ScoreGlobalMergeExecutor` 将候选池单项 detail 截断上限提升至 4000 字符，避免大模型全局裁决时复述截断摘要；`ScoreMergeResultGuard` 在采纳项中引入 `upgradeWithLongestPoolDetail` 机制，当候选池中存在更长、更完整的逐字原文版本时自动升级，彻底解决 1/2/3 等多子要点条款被截断丢失的问题。
- **Sidecar 截断 DOCX 自愈与 Apache POI XWPF 原生降级（2026-09-18）**：针对招标文件在 OSS 存储或网络传输中可能发生的尾部截断（缺少 ZIP End of Central Directory 记录 `PK\x05\x06`），Sidecar `repair_truncated_docx` 在捕获 `BadZipFile` 时自动扫描 Local File Headers，在内存中动态重建 Central Directory 与 EOCD，实现破损文档的透明无损自愈；同时 Sidecar `_safe_convert_worker` 脱敏异常回传，杜绝 Python 3.11 `ProcessPoolExecutor` 因无法 pickle `traceback` 导致的进程挂死与 60s 硬超时；Java 后端 `MarkItDownSidecarExtractor` 彻底废除 `new String(content, UTF_8)` 强转二进制乱码的反模式，改用 Apache POI `XWPFDocument` 原生提取段落与表格，构筑双层防御体系。
- **Sidecar PDF 转换保真（2026-08-24）**：针对复杂排版 PDF，Sidecar 优先使用 `convert_pdf_to_markdown` 通过 `pdfplumber` 抽取表格为 Markdown 管道表格，单元格内换行转 `<br>`，段落换行 `\n` 严格保真，彻底解决 `markitdown`/`pdfminer` 单行化（0 换行）缺陷。
- **跨维度去重防碰撞（2026-08-24）**：`ScoreItemMergePolicy` 区分同维度同编号合并与跨维度同编号保留，避免多标的/多部分招标文件（如商务表 1 与技术表 1）因编号相同被误吞。
- **LLM 全文聚焦切片（2026-08-24）**：`OpenAiScoreAnalyzer` 在多 chunk 时基于 sectionPath 和评分关键词过滤高信号 chunk，避免数十个无关合同 chunk 盲目调用 LLM 稀释信号。
- **正文来源（2026-08-16）**：立项 `TENDER` 优先，其次 Bid Agent `TENDER_FILE`，再兜底旧快照。不再要求走「启动 AI 生成初稿」。
- **自动解析门闩（spec 043）**：仅当从未有过 PARSE 任务且无评分项时自动新建；PENDING/PROCESSING 跟随已有任务；FAILED 展示原因、不自动重打。无源 400 会落 FAILED，避免每次打开抽屉再 POST。
- **读取上限（spec 065，2026-09-18 起按文本量）**：OBS 下载流式自适应落盘（≤50MB 内存快路径，超即 spill 临时文件，用毕即释；单文件配额 512MB）。可解性判定口径为提取后文本量（默认约 500 万字，配置项 `app.score-parse.tender-file.max-text-chars`）。文本超限且无底稿提示「招标文件文本量超过上限（约 500 万字），无法解析，请检查是否误传或拆分文档」；有底稿则回退。`hasSource` 与 `resolve` 同一套成功条件。旧 50MB 字节硬限（`BoundedHttpDownloader`/`capSize`）已废止；超大文件结构化证据管线降级回落 markdown 文本召回。
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
- **类别只看因素名 `dim`，不扫细则 `detail`（1371）**：细则里的「人员配置 / 品牌覆盖 / 授权」不得把财务项送进人员库、把技术方案送进品牌库。`KnowledgeCategoryPolicy.categorize` 对空白 `dim` 返回 OTHER。
- **仓库估计不以招标长句 LIKE 仓名，也不按「有在用仓」打满分（1371）**：`EstimatedScoreService` 不把细则切词当 `nameKeywords`；只有 `extractMinArea` 抽到正数才调 `WarehouseMatchService`。抽不到面积 → PENDING，禁止存在性 FULL。覆盖：`EstimatedScoreServiceTest`（无约束不调 match；5000㎡ 锁 `minArea`）。HTTP `/warehouse/match` 仍接受调用方自己的名称/面积。`Project1371ColumnInvariantsTest` 只锁召回权重/分类/物理序，不锁仓库估计。
- **前端行身份用 `item.id`**：局部编号 `1/2` 会跨组重复，`ScoreParseTable` 禁止 `:key="item.code"`；打分结果 `lookupScoreResult` 优先 `scoreItemId`。

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
- **生产大文件配额边界（2026-09-20，项目 1120）**：评分处理在 OBS 下载预检阶段按 `Content-Length` 拒绝 1,039,096,020 字节投标文件，生产默认 `512MiB` 小于文件约 `990.96MiB`，因此任务进入 FAILED，尚未进入解析算法。修复将 Java `app.score-parse.bid-file.max-file-bytes` 默认值提升为 `1GiB`（`1073741824`），并将 Sidecar 默认 `SIDECAR_MAX_UPLOAD_MB` 对齐为 `1024`；生产部署脚本当前显式使用 `3072MB`，两端配置必须保持兼容。`ram-buffer=50MiB`、临时文件总配额 `4GiB`、下载/提取超时 `600s`、异步任务超时 `30min` 保持不变。容量评估必须同时核对 JVM 可用内存、临时磁盘、Sidecar worker/cgroup 和超时，不能只提升单一字节阈值。
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

## 结构化通道补齐与归一化告警显式化（Phase 1-5，2026-09-02）

根因分析（`docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md`）确认零号病人是 markdown 表格转换丢失合并单元格结构。Phase 1a/1b/3a 已随 PR !2505 合入，本轮补齐审计发现的 7 个缺口：

- **结构化通道分层完整（Phase 1）**：`StructuredTableCandidateExtractor` 利用 rowspan 展开后的父级信息填充 `parentDim`，`ScoreCandidatePostProcessor` 感知 `STRUCTURED_TABLE` 标记——结构化通道产出的候选不再被 LLM 通道的后处理逻辑改写。
- **全局归并降级为排序/过滤（Phase 3）**：`ScoreGlobalMergeOrchestrator` 对结构化通道结果只做排序与过滤，禁止 LLM 替换其字段值，防止归并环节把已保真的结果洗成概括文本。
- **归一化告警不再静默洗白（Phase 2）**：`WeightNormalizationPolicy.WARNING_THRESHOLD = 0.05`，缩放幅度 >5% 时产出 warning。落库走 `V1206__add_score_task_normalization_warning.sql` **独立列**（非塞进 `run_summary`）→ DTO Summary 字段 → 前端告警条。
  - **边界决策**：`run_summary` 是内部遥测（开发者查库复盘 lanes/aligned/merge 计数），`normalization_warning` 是用户可见的正确性信号，受众不同故通道不同。`RunSummaryAssembler` 无需改动。依据 `docs/reviews/057-design-detour-review.md` P5。

## PR #2620 Sidecar 协议漂移与结构缺失保留策略（2026-09-12）

- **根因**：`SidecarTableExtractor` 仍按 v1 `/extract-tables` 表格协议消费响应，但 sidecar 可能返回 v2 `DocumentArtifact` 形态或 `schemaVersion` 非 1.x。旧实现把协议漂移当普通失败降级 markdown，导致已知结构缺失仍继续走启发式召回，最终可能覆盖既有评分项。
- **协议防线**：`SidecarSchemaContract` 是唯一 v1 表格协议守卫。它同时检查响应头 `X-Schema-Version`、响应体 `schemaVersion` 和 v2 `artifact` 形态；发现不匹配立即 fail-fast，并给出升级到 `SidecarDocumentArtifactExtractor` 或回退 sidecar 版本的明确提示。
- **业务语义**：docx 结构化通道失败不是“没有表格”，而是“结构不可信”。`StructuredTableExtractionHandler` 将这类失败映射为 `STRUCTURE_INCOMPLETE / STRUCTURE_DEFECT`，任务可 `markCompleted`，但禁止 `persistItems` 和 `estimateForProject`，以保留旧 `score_item`，避免用不完整结果覆盖可用数据。
- **编排边界**：`ScoreParseAppService` 只保留流程编排，结构化抽取和结构缺失任务收口放在同包 Handler；主服务维持 300 行 line-budget 内（当前约 282 行），后续不要把 Sidecar 协议判断或 run summary 组装塞回 AppService。
- **金标维护性**：`GoldenCasesReplayTest` 只保留回放流程；匹配、PRF、报告 JSON、`strictDim`、`sourceText`、`expectTotal` 等口径集中到 `GoldenCasesReplaySupport`。新增金标断言必须先进入 Support，避免测试类重新膨胀。
- **验证锚点**：`ScoreParseAppServiceTest.parse_docxStructuredMismatch_marksStructureIncompleteAndSkipRecall` 锁定 v2 mismatch 不再进入 markdown/LLM 召回；`SidecarTableExtractorTest` 锁定协议 mismatch；`GoldenCasesReplayTest` 覆盖 PR #2620 新增 1374 与 3 列内嵌权重夹具。
- **Prompt 硬编码治理（Phase 3）**：`ScoreParsePrompts` 三个 build 方法（`buildCandidateExtractionPrompt` / `buildGapRecheckPrompt` / `buildGlobalMergePrompt`）清除项目 1358 特定维度名，项目词汇改由注册表注入。回归守卫 `prompts_containNoProjectSpecificDimensionNames` 反向断言三个 prompt 均不含项目特定维度名。
- **escape-hatch 词汇注册表（Phase 5）**：`ScoreVocabularyRegistry`（Builder + 不可变 `Entry` record，global/project 两级）提供 `matches` / `isWeightInRange` / `getEntries` / `matchesAny` / `findMatch` / `toPromptExamples`；`ScoreVocabularyConfiguration` 注册 8 条全局词汇 Bean。纯核心无框架依赖（FP-Java，受 `preflight-domain-purity` 守卫）。
- **真实文档金标 CI（Phase 4）**：`RealProjectScoreParsingTest` 去掉 `@Disabled`，入库 case-1347 / case-1362 真实文档，建立回归防御。

## 客户侧展示边界（2026-09-13）

- `score_parse_fact`、`score_parse_evidence`、`score_parse_review` 继续作为解析安全与复核审计的内部数据链路保留；客户侧评分抽屉不展示事实条数、审计条数、Pipeline、Artifact、单元格 ID、提取方式、置信度或权重依据。正式评分项、解析状态、原文位置与评分依据保持不变。
- 回归锚点：`ScoreParseDrawer.spec.js`、`ScoreItemDetailModal.spec.js`；API 与 composable 仍保留复核数据读取/提交契约。

## Task2 Evidence Pipeline 主链路收敛与复核态保留旧项（2026-09-12）

- **根因**：PR #2620 只解决了 Sidecar 协议漂移后的保留策略，但 `SidecarDocumentArtifactExtractor` + `ScoreFactShadowPipeline` 仍未进入生产主链路；docx 解析继续绕回 legacy v1 table extractor / markdown / LLM 后处理。与此同时 legacy `REVIEW_REQUIRED` 仍会覆盖 `score_item` 并触发估分，把不完整结果伪装成正式评分标准。
- **主链路接入**：`StructuredTableExtractionHandler` 在存在 v2 artifact extractor 时优先执行 `DocumentArtifact → CanonicalTableGrid → ScoreFact → ScoreCompletenessPolicy`。成功后写入 `pipelineVersion=EVIDENCE_PIPELINE_V1`、`decisionStatus` 与 `fallbackReason`；v2 成功路径不再调用 `scoreAnalyzer.recallCandidates`，避免回到 markdown/LLM legacy 召回。
- **声明总分参与完整性**：Evidence Pipeline 将招标正文传入 `ScoreCompletenessPolicy`，按“总分/满分/合计 X 分”解析声明总分；没有声明时才回退默认 100 分，避免 80 分制文件被误判为复核态。
- **落库纪律**：`ScoreParseDecision.AUTO_APPROVED` 是覆盖 `score_item` 与触发 `estimateForProject` 的唯一门槛。`REVIEW_REQUIRED` 只写 facts/evidence 与任务复核状态，保留旧 `score_item`；`STRUCTURE_INCOMPLETE` 与 `PARSE_FAILED` 同样不得覆盖旧项。
- **复核证据保留**：Evidence Pipeline 的 `REVIEW_REQUIRED` 仍调用 `persistFactsAndEvidence`，保留单元格证据、权重证据与完整性缺陷，供复核 UI/API 使用；但不写兼容 `score_item`，避免半成品进入阶段 1/2 打分链路。
- **编排边界**：Evidence 完成收口放在 `StructuredTableExtractionHandler.markEvidencePipelineCompleted`；`ScoreParseAppService` 保持纯编排并回到 300 行以内（当前 291 行）。
- **回归锚点**：
  - `ScoreParseAppServiceTest#parse_docxEvidencePipelineAutoApproved_skipsLegacyRecallAndPersistsFacts`
  - `ScoreParseAppServiceTest#parse_docxEvidencePipelineReviewRequired_preservesExistingItemsButPersistsFacts`
  - `ScoreParseAppServiceTest#parse_reviewRequired_preservesExistingItemsWithReviewFlag`
  - `ScoreCompletenessAcceptanceTest#invariant3_reviewRequired_preservesExistingItemsWithReviewFlag`

> 后续维护规则：任何人想让 `REVIEW_REQUIRED` 再次覆盖 `score_item`，必须先改产品决策并补人工复核闭环；不能只改测试断言绕过。

## Task3 人工复核闭环与 score_parse_review 审计入口（2026-09-12）

- **闭环缺口**：task2 已规定 `REVIEW_REQUIRED` / `STRUCTURE_INCOMPLETE` 不覆盖正式 `score_item`，但如果前端只展示状态 badge、后端没有 `score_parse_review` 写入入口，业务上仍无法完成“看证据 → 做决定 → 留审计”的复核闭环。
- **API 契约**：
  - `GET /api/projects/{projectId}/score-parse/review`：返回最新 PARSE 任务的 `facts`、`evidences` 与既有 `audits`；
  - `POST /api/projects/{projectId}/score-parse/review`：写入 `score_parse_review`，只记录 `RETAIN_ORIGINAL / MANUAL_ADJUST / EXCLUDE` 等复核动作，**不覆盖正式 `score_item`**。
- **前端行为**：`ScoreParseDrawer` 在 `REVIEW_REQUIRED` / `STRUCTURE_INCOMPLETE` 时加载复核快照；`ScoreParseReviewPanel` 展示 facts/evidence 摘要与审计数量，并提供“记录已复核，保留现有项”动作。
- **状态残留防线**：抽屉每次加载都重置 `decisionStatus/reviewReason/fallbackReason/pipelineVersion/artifactSha256`，避免上一个项目的待复核状态串到下一个项目。
- **金标补强**：新增 `case-review-required-80-gap`，锁定“默认百分制下 facts 合计 80 → REVIEW_REQUIRED 复核缺口”场景，避免复核态被误改回自动可信覆盖。
- **回归锚点**：
  - `ScoreParseReviewAppServiceTest#getReviewSnapshot_returnsFactsEvidenceAndAuditsForLatestParseTask`
  - `ScoreParseReviewAppServiceTest#submitReview_savesAuditOnlyAndReturnsSnapshot`
  - `useScoreParseDrawer.spec.js#loads review facts for REVIEW_REQUIRED and records retention audit`
  - `GoldenCasesReplayTest#case-review-required-80-gap`

### 陷阱：结构化通道对列布局高度敏感

| 列布局 | 结果 |
|---|---|
| 4 列 `[编号, 评分类别(merged), 评分因素（N分）, 评分标准]` + 括号权重解析 | ✅ 正确 |
| 5 列 `[编号, 评分类别, 评分因素, 评分标准, 权重]` | ❌ weight 从 detail 文本的"得100分/得50分"提取（信用评价得 weight=100 而非 5），dim 顺移到评分标准列（技术 6 项 dim 全变成"按A、B、C、D分档打分"） |

金标夹具设计必须匹配此约束——项目 1358 金标首次失败即因此，改布局后 `StructuredTableCandidateExtractorTest` 17/17 通过。

### 副作用（Cross-Module Impact）

- **前端**：`ScoreParseDrawer.vue` + `useScoreParseDrawer.js` 为纯增量（`v-if="normalizationWarning"` 块 + prop 解构 + 3 条 CSS），默认态不渲染、DOM 不变，故 e2e 布局用例无需联动。
- **数据库**：`V1206` 加列 + `U1206` 回滚脚本成对。
- **测试签名**：`ScoreGlobalMergeExecutor.resolve` 由 2-arg 变 3-arg（新增 `vocabularyExamples`）。Mockito strict stubbing 下**所有** stub 与 `verify(..., never())` 必须同步迁移到新签名——留在旧签名上的 `never()` 会退化为永真断言，等于删掉防护。参见 `docs/lessons/lessons-learned.md` §205。

### 验证基线

`testing-gate: passed` — 架构测试 12 + scoreparse 114（AppService 20 / PostProcessor 5 / Orchestrator 7 / Prompts 15 / MergeExecutor 6 / ItemMergePolicy 18 / VocabularyRegistry 14 / NormalizationPolicy 12 / StructuredTableExtractor 17）+ 前端 27（useScoreParseDrawer 22 / ScoreParseDrawer 5）。


## isCredit 救援收窄与编号分隔符补齐（follow-up，2026-09-05）

- **isCredit 从裸子串收窄为"主体词 + 失信"**：`DeductionItemPolicy.isCreditRescue(texts)` 要求同时含
  `诚信|信用` 与 `失信` 才豁免程序性过滤。否决"信用 + 评价/评分"交集方案的原因：
  反向例"推荐成交候选供应商三名信用评价程序按规定执行"同时含信用与评价，交集照样泄漏。
  `GhostCandidateFilter` / `CellAnchoredScoreExtractor` / `ScoreTableRowParser` 三处同调一处
 （RowParser 为新增救援口径）；两处 EXPLICIT 恢复 `扣\s*\d+\s*分` 空格容忍。
- **正反单测锁**：纯 isCredit 正向用例刻意不含不扣/扣减/扣除/得分/扣分/数字/括号；
  反向用例在旧裸 isCredit 逻辑下必红。
- **编号分隔符反斜杠**：Java 字符类源码中 `\\-` 只是转义连字符，类内本无字面反斜杠
  （字面反斜杠在 Java 字符串需写成 `\\\\`），已补上（`null\1`→`1` 单测钉死）；
  前端本就支持（JS 正则字面量 `\\` 即字面反斜杠）。
- **前端 NFKC 对齐**：手写 `FF01-FF5E` 映射换成标准 `normalize('NFKC')`，与后端
  `java.text.Normalizer` 对齐；U+3001 、不受影响（中文编号保留），零宽仍需显式剥除。

## 审查返工：救援谓词收敛与测试诚实化（2026-09-05）
- `hasExplicitScoreRule` 并入 `DeductionItemPolicy`，Ghost/Cell 去本地别名层；
  RowParser 的 EXPLICIT 救援仅作用于程序性侧（考核表侧仍过滤，
  `parse_contractAssessmentRowWithoutScore_filtered` 锁定）。
- 纯救援正向用例注释修正：只证"删掉救援即变红"，收窄语义由反向用例锁定；
  NFKC 用例补真增量（圈号/上标），旧三断言标注为回归锁。

## 10. Sidecar 容器发布闭环与原生结构化表格跨行分值消重（2026-09-07）

### 背景与根因
用户在项目 1371（通用技术MRO项目）中反馈 AI 评分项标准解析不准（82 分缺失或 104 分膨胀）：
1. **Sidecar 独立容器发布断链**：`package-release.sh` 历史脚本遗漏了 `document-converter-sidecar/` 打包；部署脚本健康检查仅探测通用 `/health`（返回 200 即放行），而业务层新增的 `/extract-tables` 接口报 404，导致系统静默降级为 lossy 的 Markdown 解析，表格合并结构全失、评分细则丢失（总分仅 82 分）；
2. **跨行合并单元格（`rowspan > 1`）分值重复累计**：“资源实力（6分）”跨 3 行合并单元格，内含 3 个 2 分子规则。提取器在首行提取括号大项分 6 分，后两行提取子项各 2 分，导致 6+2+2=10 分，总分膨胀为 104 分。

### 根治措施
1. **发布流程闭环**：
   - `package-release.sh` 自动归档 `document-converter-sidecar/` 为 `sidecar.tar.gz` 并记录 `sidecarSha256`；
   - `remote-deploy.sh` 对比远端 sha256，变更时自动执行 `docker build -f Dockerfile.cn` 并重启 `xiyu-sidecar` 容器；
   - `deploy-test.sh` / `deploy-prod.sh` Step 6 增加 `POST http://127.0.0.1:8000/extract-tables` 探活强阻断，遇 404 直接阻断部署。
2. **`StructuredTableCandidateExtractor` 算法消重与规范化**：
   - **细则分优先消重**：检测跨行单元格（`rowspan > 1`），当提取到的 `bracketWeight` 属于该跨行大项且行内存在细则得分（`maxGradeScore < bracketWeight`）时，单项优先取 `maxGradeScore`（2分），消除 6+2+2 膨胀；
   - **条款编号过滤**：清洗形如 `3.3(1)` 等非语义条款编号，防止污染父维度；
   - **维度防自包含**：将最内层跨行父级（如“资源实力”）作为该行 `dim`，上级分类作为 `parentDim`，消除自引用；
  - **金标测试保障**：固化真实 14 张表格入参为金标 `1371_tables_v1.json`，全表提取严格 16 项（商务 56 分 + 技术 44 分 = 100.0 分严格收敛）。

## 真实语料金标录制与 nightly green path（2026-09-12）

- **边界**：真实客户文件、真实原文与 `candidates.json` 均只允许放在仓库外的 `XIYU_EVAL_CORPUS_DIR`；仓库只保留录制工具、脱敏期望和测试入口。
- **录制入口**：`npm run eval:scoreparse:record-real-corpus` 调用 `scripts/eval-scoreparse/record-real-corpus.sh`，默认读取 `$XIYU_EVAL_CORPUS_DIR/incident-1358/source.md` 并写出 `$XIYU_EVAL_CORPUS_DIR/incident-1358/candidates.json`。
- **验证入口**：`npm run eval:scoreparse:real-corpus` 运行 `Incident1358RealCorpusGoldenTest`，将本地候选快照送入生产后处理链，断言真实 1358 形态保持 `AUTO_APPROVED`、11 项全部落库、总分 100。
- **nightly 行为**：`scripts/eval-scoreparse/nightly-full.sh` 若发现 `incident-1358/candidates.json` 会自动追加运行真实语料 green path；缺失时明确 SKIP，不阻塞 CI。

## 11. PDF 评分表进入 Evidence Pipeline 与 REVIEW_REQUIRED 候选可见（2026-09-12）

### 根因

项目 1381 类 PDF 招标文件在前端只显示 1 条旧评分项，不是部署漏发，而是两条链路断裂叠加：

1. `StructuredTableExtractionHandler` 过去只允许 `.docx` 进入 `DocumentArtifact → CanonicalTableGrid → ScoreFact` 证据链，`.pdf` 一律短路回 legacy markdown/LLM；
2. Sidecar `/extract-tables` 过去只支持 docx，PDF 表格只能转成 markdown，单元格换行被写成 `<br>`，复杂评分表会被压成 lossy 文本；
3. `REVIEW_REQUIRED` 为保护旧 `score_item` 不覆盖正式评分项，但旧链路候选没有写入 `score_parse_fact`，导致复核 UI 看不到最新候选，只能继续展示旧的 `score_item`。

### 修复契约

- **Sidecar**：`extract_pdf_tables_artifact(pdf_path)` 输出 v2 `DocumentArtifact`，`artifact.fileType=PDF`，每个 cell 带 `sourceXmlPath=pdf:page=...;table=...;row=...;col=...`；rawText 保留换行语义，不再把证据文本固化成 `<br>`。
- **后端路由**：`.docx` 与 `.pdf` 都可进入 `StructuredTableExtractionHandler` 证据链；如果 PDF 没有可抽取评分表，则回落 legacy markdown 召回，避免扫描件/纯文本 PDF 被误判结构异常。
- **复核可见性**：`ScoreParseAppService` 在 `REVIEW_REQUIRED` 且有有效候选时调用 `ScoreItemPersistenceService.persistReviewCandidateFacts`；候选通过 `ScoreReviewCandidateFactMapper` 写入 `score_parse_fact`，供 `/score-parse/review` 展示，**不覆盖正式 `score_item`**。

### 回归锚点

- `PdfEvidencePipelineTest#pdfTender_usesDocumentArtifactEvidencePipelineInsteadOfSkippingToLegacyMarkdown`
- `PdfEvidencePipelineTest#pdfTenderWithoutExtractableScoreTables_fallsBackToLegacyRecallInsteadOfFailingTheTask`
- `ScoreItemPersistenceServiceTest#reviewRequiredCandidates_arePersistedAsReviewFactsWithoutOverwritingScoreItems`
- `document-converter-sidecar/tests/test_pdf_table_artifact.py`
- 金标：`case-pdf-cert-list-single-item`

## 12. 项目 1381 PDF 分页续表验收：最终 items 必须等于评分表全量项（2026-09-12）

### 业务验收口径

评分解析的验收不能停在 Sidecar、facts 或复核快照局部可见。对项目 1381 这类招标文件，期望输出就是招标文件“综合评分法”表里的全部评分项：

| 编号 | 评分项 | 权重 |
|---|---|---:|
| 1 | 投标报价 | 40 |
| 2 | 公司综合实力 | 6 |
| 3 | 类似业绩 | 10 |
| 4 | 技术方案 | 10 |
| 4 | 供货方案 | 10 |
| 5 | 人员配备 | 6 |
| 6 | 质量管理方案 | 10 |
| 7 | 售后服务方案 | 8 |

原文存在两个编号 `4`，系统应保留原文编号；验收重点是 8 个叶子评分项与合计 100。

### 根因与修复

- **分页续表断裂**：第 25 页无表头续表被当成新表，首行“类似业绩（10分）”被跳过。Sidecar 现在在列数一致、上一表有评分表头、下一表首列继续编号或合计时合并为同一逻辑评分表。
- **初步评审表误入评分事实池**：资格评审、形式评审、响应性评审表不属于打分评分项。PDF score table 识别现在要求“评分表头 + 权重评分项/合计行”，并显式排除初步评审表。
- **局部满分覆盖整表总分**：报价规则内“满分30分/满分10分”只是局部分值，完整性策略现在扫描全文声明总分并取合理最大值，使表尾“满分（100分）”成为完整性基准。
- **汇总行防污染**：`合计/满分/总分/得分` 行不是评分项，事实抽取阶段直接跳过。

### 回归锚点

- `test_pdf_score_table_detection_excludes_preliminary_review_table`
- `test_pdf_score_table_continuation_is_merged_for_project_1381_shape`
- `PdfEvidencePipelineTest#project1381LikePdfSplitScoreTable_extractsAllEightScoreItemsAndTotal100`
- `ScoreCompletenessPolicyTest#evaluate_declaredTotalPrefersOverallTotalOverLocalFullScorePhrases`

### 已验证证据

- 真实 PDF smoke：`extract_pdf_tables_artifact` 输出 20 张表、0 warning；第 20 页初步评审表 `isScoreTable=false`；第 24/25 页评分表合并为 10 行，含 8 个评分项 + 合计行。
- Sidecar：19 个相关测试通过。
- 后端：55 个 scoreparse 相关测试通过；1381 类 artifact 输出 8 项、权重合计 100、`AUTO_APPROVED`。

## 13. 项目 1381 声明总分结构化单一事实源（2026-09-12）

### 最终根因

分页续表修复后，项目 1381 的 `/review` 已有 8 个事实且叶子权重合计 100，`/items` 仍只有旧的 1 项。直接证据是完整性结果为 `REVIEW_REQUIRED / WEIGHT_SUM_MISMATCH`，声明总分被误判为 30。

原因不是 Artifact 缺数据，而是完整性策略继续从 `/convert` Markdown 解析声明总分。真实表尾在结构化 cell 中是 `合计\n得分 | 满分\n（100分）`，Markdown 则为 `合计<br>得分 | 满分<br>（100分）`；正则无法跨 `<br>` 和列边界命中 100，却命中了报价细则中的局部“满分30分”。旧合成测试把文字写成连续的 `合计 得分 满分 （100分）`，掩盖了生产输入差异。

### 结构化总分契约

- `ScoreTableSummaryPolicy` 从 `CanonicalTableGrid` 汇总行提取声明总分，并保留 `tableId/row/sourceCellId/quotedText`；它返回全部证据，不自行取最大值。
- `DeterministicScoreFactExtractor` 与总分策略共享汇总行判定，保证汇总行既不会成为评分项，又能成为完整性证据。
- `ScoreCompletenessPolicy` 的优先级固定为：结构化汇总行唯一值 → 招标正文兼容兜底 → 默认 100。
- 多个不同结构化总分触发 `DECLARED_TOTAL_CONFLICT` 并进入人工复核，禁止选择最大值后自动覆盖。
- `declaredTotalSource` 写入复核快照和服务日志；1381 同形态的正确值应为 `STRUCTURED_SUMMARY_ROW`。

### 端到端回归锚点

- `ScoreTableSummaryPolicyTest#extractsDeclaredTotalFromProject1381SummaryCells`
- `PdfEvidencePipelineTest#project1381LikePdfSplitScoreTable_usesStructuredTotalWhenMarkdownBreakTagsHide100`
- `PdfEvidencePipelineTest#project1381LikePdfWithoutStructuredSummary_keepsLossyMarkdownFallbackBehavior`
- `PdfEvidencePipelineTest#conflictingStructuredSummaryTotals_requireReviewInsteadOfGuessing`
- `ScoreParseAppServiceTest#parse_project1381PdfStructuredSummary_persistsAllEightItems`

> 维护纪律：评分表总分属于表格结构事实。只要结构化汇总行可用，就不得再让 Markdown 正文正则覆盖它；正文解析仅是无结构化证据时的兼容通道。

## 14. 错位合计行与无固定分值价格公式防复发（2026-09-12）

### 生产证据

- 项目 1377 新招标文件上传成功，`/review` 返回 `COMPLETED`、41 条 facts；失败发生在完整性裁决，不是上传、Sidecar 或 HTTP 请求失败。
- 结构化评分表实际包含 `合计 | 12 | 100`；列映射按固定 code/dimension 列读取时无法识别该行，旧逻辑把合计数值泄漏进叶子事实，叶子权重累计为 381。
- `折扣率评价` 是有规则但无固定最高分的价格公式。原始 `ScoreFact.weight` 为 null，持久化/前端展示阶段才可能显示为 0；在持久化前把该事实一律当作缺证据，导致 `MISSING_EVIDENCE`，最终 `/items` 为空。

### 修复契约

- `ScoreTableSummaryPolicy.isSummaryRow(rowCells)` 跨越列映射，按“汇总标签 + 总分标记或纯数字单元格”识别错位合计行；提取行内最大合理纯数字作为总分证据，并保留物理单元格出处。
- `DeterministicScoreFactExtractor` 使用同一汇总行判定，汇总行只进入总分证据，不进入评分 facts。
- `ScoreCompletenessPolicy` 仅豁免“价格公式语义命中且无固定分值”的事实；正分评分项、未知权重事实和其他零分事实仍要求可定位证据。

### 防复发边界

- 单元测试覆盖错位合计行、合计行不泄漏、原始 null 分值价格公式不阻断正分项；golden 夹具 `case-misaligned-summary-row` 保留脱敏版式样本。
- 门禁要求评分解析主代码变更同步夹具或回放测试；禁止通过 `SCOREPARSE_GATE_SKIP` 绕过新版式回归。
- 这能根治本次“合计错位 + 价格公式误阻断”故障类，但任意新文档版式仍需新增脱敏夹具和结构化证据验证；发布后必须对项目 1377 重解析，验收 `/review`、`/items` 与页面三层结果。


## 15. 多表评分标准自动拆分（spec 062，2026-09-13）

### 生产问题

一份招标文件含多张独立评分表（如技术 60 / 商务 40 两张）时，旧实现按整份文档合并校验声明总分：文档级 declaredTotal 取一张表的 60，而叶子权重合计是 60+40=100，触发"声明总分冲突 / 权重失配"→ REVIEW_REQUIRED，正式评分项列表为空，用户看到"未识别出评分项"。

### 方案 A：按表自动拆分

- `ScoreTableSplitPolicy` 以 `sourceTableId` 分组评分项，每张表按本表声明总分独立校验；各表自洽即本表全部评分项落库为正式 score_item。
- `TableCompleteness` / `TableSplitResult` 承载每张表的独立完整性结果；文档级聚合按最差决策（PARSE_FAILED > STRUCTURE_INCOMPLETE > REVIEW_REQUIRED > AUTO_APPROVED）仅用于任务状态标记，不阻塞按表落库。
- `run_summary` 新增 `tableSplits` 明细，逐表输出 tableId / declaredTotal / decision，供前端复核展示。

### 归一化告警判据修正

多表场景下文档级 declaredTotal 与全表 totalWeight 无直接可比性，原比较必现误报。`StructuredTableExtractionHandler` 改为以 `weightWarning`（分表后 leafWeightSum vs 本表声明总分）为唯一判据；单表失配仍保留原告警语义。

### 回归锚点

- `ScoreTableSplitPolicyTest`（纯核心：分组 / 独立校验 / 文档级聚合）
- `PdfEvidencePipelineTest#multiTableTender_eachOwnTotalSelfConsistent_allTablesAutoApproved`
- `StructuredTableExtractionMarkCompletedTest`（落库层：多表自洽不误报 + tableSplits 明细 + 单表失配告警）
- golden 夹具 `case-multi-table-tech-commercial`（两张独立评分表输入形态锁定，F1=1.0）

> 维护纪律：多表拆分以"表"为基本校验单元，文档级聚合只做状态标记。新增多表形态回归必须同步金标夹具或回放测试；`case-multi-table-tech-commercial` 已立基线。

## 16. 合并维度起始行的父事实与首个叶子双产物（项目 1377，2026-09-13）

### 故障链

| 层级 | 证据 |
|---|---|
| UI | 评分标准 0 项 |
| 持久化门禁 | `REVIEW_REQUIRED / WEIGHT_SUM_MISMATCH`，安全策略不覆盖正式项 |
| 表内完整性 | 叶子合计 95 / 86，声明总分 100 |
| 事实抽取 | 合并维度起始行只生成一个父事实，同行第一条显式分值细则消失 |
| 零号病人 | “一物理行一事实”无法表达“父标题总分 + 同行首个叶子” |

### 领域契约

- 合并起始行只有在 `rowspan > 1`、唯一标题总分、起始行非空细则、起始行正显式分值、且合并范围内存在带正分的真实续行时，才拆成父事实与首叶事实。
- 父事实：标题总分证据、`REVIEW_REQUIRED`、不进入自动评分；首叶事实：显式列证据、`AUTO_APPROVED`、`parentFactId` 指向父事实。
- 所有续行叶子复用同一父事实；父链只索引映射后的维度列合并区域，避免同一行的编号列纵向合并覆盖维度 origin；事实 ID 加入 `parent/leaf` 角色，避免同一物理行碰撞。
- 空白续行不构成评分组，必须保持单事实，防止仅凭合并版式误拆。

### 副作用（Cross-Module Impact）

- `ScoreCompletenessPolicy` 无需特判：它继续只累计叶子，完整的 5 + 5 等子项自然闭合到父总分。
- `ScoreTableSplitPolicy` 无需修改：每张表拿到完整叶子后按表校验，避免 95 / 86 假性缺口。
- 历史 1347 / 1362 golden 口径同步升级：防重复计父分与防吞首叶必须同时成立。
- 无数据库、API 或前端契约变化；合入部署后需重新解析项目 1377 才会更新页面。

### 验证锚点

- `DeterministicScoreFactExtractorTest#merged_origin_with_group_total_and_first_leaf_emits_both_facts`
- `DeterministicScoreFactExtractorTest#merged_origin_requires_unambiguous_title_first_detail_and_scored_continuation`
- `ScoreFactShadowPipelineTest#merged_origin_fixture_preserves_parent_total_and_all_leaf_weights`
- `GoldenCorpusBaselineTest#incident_1347_parent_and_children_never_scored_together`
- `GoldenCorpusBaselineTest#incident_1362_category_weight_not_summed_with_children`

## 17. 评分证据持久化原生 JSON 转义与抽取源头空白清洗（2026-09-16）

### 故障链

| 层级 | 证据 |
|---|---|
| 异常报错 | `could not execute statement [Data truncation: Invalid JSON text: "Invalid encoding in string." at position 55 in value for column 'score_parse_fact.weight_evidence'.]`（MySQL Error 3140） |
| 生产影响 | 生产项目 33 招标文件表格包含跨行单元格“商务及技术评分（10\n分）”，解析落库失败中断 |
| 零号病人 | `ScoreItemPersistenceService` 使用 `String.format` 手写拼接 JSON 字符串，仅转义 `"` 未转义 `\n`/`\r`/`\t`/`\\` 等 ASCII 控制字符；MySQL 8.0 原生 JSON 解析器（RapidJSON）根据 RFC 8259 严格拒绝字符串内的未转义控制字符 |

### 领域治理与契约

1. **序列化强制收敛至 Jackson `ObjectMapper`**：
   - 在 `ScoreItemPersistenceService` 中提取 `serializeCellIds` 与 `serializeWeightEvidence` 方法，将 `source_cell_ids` 与 `weight_evidence` 列统一通过 `objectMapper.writeValueAsString(...)` 原生生成标准 JSON 字符串，所有控制字符与特殊字符均由标准序列化库转义。
   - 依赖注入 Spring 容器托管的 `ObjectMapper`，并提供无参兜底兼容。
2. **抽取端源头空白标准化清洗**：
   - 在 `DeterministicScoreFactExtractor.evidence(...)` 构造 `ScoreEvidence` 前，对 `quotedText` 做空白与换行归一化：`quoted.replaceAll("\\s+", " ").trim()`；
   - 依据原始未清洗的 `cell.rawText()` 计算 `offset` 与 `endOffset`，确保前端高亮定位不发生坐标漂移。
3. **真实 MySQL 8.0 容器物理集成测试守卫**：
   - 在 `FlywayMysqlContainerTest` 中新增 `scoreParseFactPersistsWithNewlineQuoteAndBackslashOnRealMysql`，向 MySQL 8.0 物理容器插入含 `\n`、`\"`、`\\` 的评分事实，断言 `JSON_VALID(weight_evidence) == 1` 以及 `JSON_EXTRACT` 正确取值。

### 验证锚点

- `DeterministicScoreFactExtractorTest#extracts_title_maximum_with_newline_and_normalizes_quoted_text`
- `ScoreItemPersistenceServiceTest`（10/10）
- `FlywayMysqlContainerTest#scoreParseFactPersistsWithNewlineQuoteAndBackslashOnRealMysql`（5/5）

## 18. 评分任务异常清理与横幅表头/内嵌分值列映射修复（2026-09-17）

### 故障链与根因

1. **Redis 进度泄漏**：`ScoreParseTaskStateService.failTask` 三层降级分支中仅兜底分支清理 Redis，层 1/2 成功时直接 return 导致 `score_parse:progress:{taskId}` 残留 `PROCESSING` 状态（TTL 5.7天）；`markTimeout` 同样遗漏清理。
2. **横幅表头误判与细分分值归零**：生产项目 33 表格首行是总分横幅行（`总分100分，其中商务70分技术30分`），真正列名在第二行。启发式推断器误将首行作为表头导致 `weightCol` 错位到第一列，所有细分子项分值被判为 0 并触发 `REVIEW_REQUIRED`，页面根据防污染契约保持 0 项展示。
3. **父子维度列与内嵌分值绑定反转**：在 `供应商业绩（7分）` 等内嵌分值列左侧存在父大类维度列时，`dimCol` 预先被父列名占用且未做位置感知重分流，导致子维度名被覆盖为 null。

### 领域治理与契约

1. **状态机终态收尾**：`failTask` 与 `markTimeout` 统一在 `finally` 块中无条件调用 `progressService.clearProgress(taskId)`。
2. **表头多行安全探测**：仅当 `row(0)` 表头特征不足或包含超长单元格（`>40` 字符）时才在前 3 行中探测真表头，候选行必须满足 `noneMatch(cell.length > 40)` 且 `matches >= 2`；一旦命中立即锁定。
3. **父子维度层级绑定**：当识别到 `bracketCol` 且其左侧已有 `dimCol < bracketCol` 时，自动升级左侧为 `parentDimCol`，并由 `bracketCol` 同时承载 `dimCol` 与 `weightCol`。
4. **合同履约考核表隔离**：对非显式评标表格增加 `containsAssessmentPattern(grid)` 全表扫描，彻底阻断履约扣款与 SLA 考核表干扰。

### 验证锚点

- `ScoreParseTaskStateServiceTest`（覆盖全部 failTask 降级与超时清理断言）
- `HeuristicTableColumnMapperTest#infer_bannerRowAboveHeader_mapsGenuineHeaderRowCorrectly`
- `RealProjectScoreParsingTest#testProject21ScoreParsing`
- `Project1371ColumnInvariantsTest`
- `DeterministicScoreFactExtractorTest#extract_skips_unmerged_empty_dimension_and_unscored_overflow_row`

## 19. 跨页续表残行 Title Weight Fallback 劫持大类总分与无表头分值列推断（2026-09-18）

### 故障链与根因

1. **跨页续表残行 Title Weight Fallback 劫持大类总分（湖北中烟项目 33）**：
   - 招标文件第 39 页“总体服务方案”细则跨页溢出到第 40 页首行；第 40 页首行左侧为跨行父大类单元格 `技术部分（30分）`，右侧显式分值列为空；
   - `DeterministicScoreFactExtractor` 中的 `resolveWeight` 遇到分值列为空时，贪婪地从左侧维度文本通过 `parseTitleWeight` 提取了 30 分作为残行的分值，导致该表格总分虚增 30 分（总分 130 分）；
   - 触发防污染拦截 `WEIGHT_SUM_MISMATCH`，决策判定为 `REVIEW_REQUIRED`，未写入 `score_item` 表，前端抽屉展示为空。
2. **无显式表头分值列误判与修 A 破 B 防范（招商积余项目 1117）**：
   - 第 43 页表格（5 列：序号、因素、细则、分值、备注）无显式表头，启发式推断器默认取最后一列（备注列“否”）作为分值列，分值全为 0.00；
   - 若贪婪地扫描整表把纯数字列（如第 0 列编号 1..5）当分值列，会导致项目 1371 目录表等大量非评分表格被误判为评分表并召回 80+ 条虚假评分项，引发 NPE 和顺序错乱；
   - 根治方式：仅当末列无有效分值且表格 `>= 4` 列时，在 `[1, colCount - 2]` 有界区间（严格排除列 0 序号列与 detailCol 细则列）推断纯数字密度最高的列作为分值列。

### 领域治理与契约

1. **跨行大类单元格 Title Weight Fallback 阻断与 DOTALL 跨行正则**：
   - 在 `DeterministicScoreFactExtractor.resolveWeight` 中明确契约：当表格存在显式分值列且当前单元格为跨行单元格（`dimCell.rowspan() > 1`）或匹配大类特征时，严禁 fallback 提取标题分值；
   - 将 `CATEGORY_TITLE_PATTERN` 升级为 `(?s)` 显式 DOTALL 模式，彻底解决单元格内含有换行（如 `部分\n（30\n分）`）时 `.*` 无法跨行匹配导致漏判大类的问题；
   - 主循环过滤无独立细项维度名的跨页残行。
2. **有界安全分值列推断与列 0 隔离**：
   - `HeuristicTableColumnMapper` 维持末列优先；仅在末列非分值时在 `[1, colCount - 2]` 区间按有效分值密度推断分值列，坚决排除列 0 序号列；
   - 无表头表格若列 0 为纯序号且列 1 为文本，推断 `codeCol = 0`，确保编号维度正确对齐。

### 验证锚点

- `ScoreParseContinuationRowTest#testContinuationRowDoesNotStealParentCategoryScore`
- `ScoreParseContinuationRowTest#testContinuationRowWithNewlineAndNoMergeDoesNotStealParentCategoryScore`
- `ScoreParseContinuationRowTest#testNumericWeightColumnDensityInference`
- `Project1371ColumnInvariantsTest`（0 回归）
- `CellAnchoredScoreExtractorTest#extract_fiveColumnTableWithoutExplicitHeader_infersWeightColumnCorrectly`
- `com.xiyu.bid.scoreparse.**` 全量 792 项单测 100% 通过

## 20. 跨页深层表头探测与长细则防误吞（招商积余项目 1117，2026-09-18）

### 故障链与根因

1. **Sidecar 表头扫描深度不足与跨页续表断裂**：
   - 招商积余项目 1117 招标文件在第 42 页前 7 行均为前置条款及条款说明，真正 5 列评分表头位于第 8 行；
   - Sidecar 原表头扫描深度仅硬编码前 2 行（`range(min(2, len(rows)))`），导致第 42 页评分表无法识别出有效表头而被当作普通非评分表丢弃；
   - 第 43、44 页为无表头跨页续表，因第 42 页未建表，续表无法回溯合并，导致整份招标文件评分表提取为空。
2. **长细则关键字触发表头误判与项 2（公司业绩）误吞**：
   - 第 43 页首行（项 2 公司业绩）细则内容为：`评审标准：1）每提供一个有效业绩得5分，最高得分30分...`；
   - `HeuristicTableColumnMapper.countHeaderMatches` 原本无单元格长度上限，该长文本同时命中 `评审标准` 与 `最高得分/得分`，触发 `matches >= 2`；
   - 若启发式扫描未阻断该行或外层未限制超长单元格，项 2 就会被当作表头行吞噬，导致 30 分大项遗漏。

### 领域治理与契约

1. **Sidecar 真表头判定与深层跨页续表合并**：
   - `converter.py` 引入 `_is_genuine_header_row` 强化真表头校验：列数限制在 3~15 列、首列排除纯数字序号、所有单元格长度 `<= 40`，且必须显式命中表头关键词；
   - 表头扫描深度由前 2 行放宽至前 10 行；分值列识别支持纯数字分值项；
   - 跨页表格探测到无表头续表且列数兼容时，自动回溯合并到上一页已识别的评分表主体。
2. **Java 后端长细则忽略约束与深层表头探测**：
   - `HeuristicTableColumnMapper.countHeaderMatches` 增加单格长度约束：`if (cell.length() > 40) continue;`，细则长文本即使包含表头关键词也绝不计入匹配数；
   - 表头扫描安全放宽至前 10 行，候选行严格要求 `matches >= 2` 且整行不得包含超长单元格（`!hasOverlyLongCell`）。

### 验证锚点

- `converter.py` + `test_pdf_table_artifact.py#test_pdf_score_table_deep_header_and_multipage_continuation_merged_for_project_1117_shape`
- `HeuristicTableColumnMapper` + `CellAnchoredScoreExtractorTest#extract_deepHeaderAndLongDetailContinuationRow_extractedCorrectlyWithoutSwallowing`
- `ScoreParseContinuationRowTest#testProject1117DeepHeaderDetectionAtRow8`
- 生产文件 `1117.pdf` 真实测试：42-44 页 16 行完整评分表提取，全量 7 项 100.0 分。

## 27. 人机协同候选评分标准采纳闭环（2026-09-18，REVIEW_REQUIRED 自愈）

### 业务背景与问题根因
针对五矿（如项目 49）等包含多个表格或附带响应格式模板（含有 `XX~XX` 分占位）的招标文件，解析引擎在多表拆分策略（`ScoreTableSplitPolicy`）下判定为 `REVIEW_REQUIRED`（待人工复核）。此时由于未达到 `AUTO_APPROVED`，系统未自动落库 `score_item`，导致前端抽屉展示空白（0 项评分标准），用户无法推进后续阶段 2 投标文件实际对标打分。

### 领域治理与闭环设计
1. **纯核心领域采纳策略（`ScoreReviewAdoptionPolicy`）**：
   - 遵循 FP-Java 规范，纯核心无框架依赖；
   - 过滤有效事实（`weight > 0` 且文本完备），支持人工微调权重，并严格校验总分合法性；
   - 输出结构化的 `ScoreCandidate` 集合。
2. **应用服务入库与自动化触发（`ScoreParseReviewAppService`）**：
   - 支持 `ADOPT_CANDIDATES` 动作，调用 `ScoreItemPersistenceService.persistItems` 批量持久化到 `score_item`；
   - 更新任务决策状态为 `MANUAL_APPROVED`，写入审计日志 `score_parse_review`；
   - 自动联动触发 `EstimatedScoreService.estimateForProject` 完成阶段 1 预估打分。
3. **前端人机协同交互（`ScoreCandidateReviewCard.vue` + `useScoreCandidateAdoption.js`）**：
   - 抽屉在 `scoreItems.length === 0 && candidateFacts.length > 0` 时，呈现候选细则卡片与 `[ ⚡ 一键采纳为正式评分标准 ]` 按钮；
   - 满足 300 行 line-budget 预算拆分。

## 28. 上游评分完整性边界（2026-09-22）

- 模型父子评分项编码只在同一评分表、同一章节内解析；章节标题不能拼入 `parentCode`，无法唯一挂接时保留原值并进入人工复核。
- 汇总项与明细项分离，权重闭合只统计真正叶子项，避免父级汇总分被重复计入。
- 多评分表按表建立独立总分口径；缺少表级总分证据时不默认归一化，结果保持 `REVIEW_REQUIRED`。

## 29. 纯文本候选的方案范围（2026-09-24）

`text:line[n]` 事实必须按 locator 对应行的 Markdown 标题栈证明属于当前评分方案。评分办法下的子标题继承方案范围；同级“履约考核”等标题关闭前一方案范围。全文引文匹配不能代替归属验证；没有可识别方案标题的文本证据不得自动发布。回归覆盖同方案子标题正例与异方案同级标题反例，pre-push 由 `check-scoreparse-text-scope.mjs` 防止无条件接受文本 locator。


## 30. 可选提取版本功能关闭时的页面兼容（2026-10-08）

最新提取快照读取返回 409 `EXTRACTION_FEATURE_DISABLED` 时，评分抽屉继续读取原有评分项与结果，无需启用提取版本功能。`src/api/modules/scoreParse.js` 的 `getLatestExtraction` 使用 `skipGlobalErrorMessage: true`，防止 HTTP 拦截器在页面捕获前弹错；真实读取错误仍由 `useScoreExtraction.js` / 评分抽屉显示。此配置只作用于最新快照读取，不影响激活写入。根因与回归证据见 `docs/lessons/lessons-learned.md` §419。


评分页面兼容改动的交付验证还需检查后端安全测试是否跟随当前契约：匿名请求断言 401 JSON，竞品创建成功身份显式携带 `competitor.create`。同一 worktree 的后端测试串行运行，损坏的 JaCoCo 构建产物通过干净构建重建；诊断时关闭覆盖率不算报告通过。详见 `docs/lessons/lessons-learned.md` §423。


评分小计列应先依明确最高/满分表头定位；合法表格若把单项分值内嵌于“评分因素”列，且合计行只在该列给出唯一明确满分，可依该来源声明验证。存在多个显式总分或只有无标签数值时，继续保留复核状态。回归契约与测试证据见 `docs/lessons/lessons-learned.md` §424。

## 31. 评分小计列排除单项满分与冲突声明保持未决（2026-10-09）

在缺少明确最高分/分值表头时，若合计行文本包含“单项满分10分，共10项，合计100分”，小计列匹配正则必须通过负向后行断言严格排除“单项/每项/每条/每个/本项”等修饰，严禁误将单项满分作为整表满分。若同一单元格或同一列包含多个不同数值的显式总分/满分声明（如“满分100分，总分90分”），`ScoreSubtotalColumnPolicy` 必须主动放弃并返回 `-1` 维持未决，严禁猜测采信。详见 `docs/lessons/lessons-learned.md` §425。

## 32. 单一表格全池唯一编号时按编号数值排序（项目 580，2026-10-21）

- **症状与根因**：项目 580 落库 `score_item.item_index` 为 `1,2,6,8,9,3,4,7,5`，而 `score_parse_fact` 中原文编号 `1..9` 顺序正常。根因是 §247 的四层排序纪律里，第一关键字“顶级大类首现序”由 `normalizeCategoryName` 的关键词启发式决定——「商品管理方案 / 技术运营服务方案 / 仓储配送方案」被判为“技术评分”、“信用额度”被判为“诚信评价”、其余无关键词归为空大类，于是同一张表内的 1..9 被按大类首现序拆成 `1,2 / 6,8,9 / 3,4,7 / 5` 四段。维度名里的“方案/信用”是可编辑的自由文本，用它反推大类结构本质上不可靠。
- **修复（规则 5）**：`ScoreItemOrderPolicy.sort()` 增加前置判定——候选池内编号**全部可解析**（`ScoreCodeKey.parse` 非 null）、**两两唯一**、且**无中文章节序号**时，直接按编号数值稳定排序并返回；否则完整落回 §247 的四层逻辑。判定条件是“全局唯一”，跨大类复用 `1、2` 的场景（项目 1371 等）天然被排除，因此不覆盖既有语义。
- **作用域澄清**：抽取结果未携带 `sourceTableId`，判定实际作用于整个候选池而非单张表；“编号全局唯一”是可观测的替代信号。
- **编号解析抽离**：解析/比较由 `ScoreCodeKey` record（`parse` / `compare`，支持 `4 / 4-1 / 4.1 / 2.2.4(1)`）承载，`ScoreItemOrderPolicy` 253 行（基线 281 行；新增规则 5 后一度涨到 302 行，抽离后回落），避免触碰 300 行硬门禁。
- **消费方一致性**：`ScoreItemPersistenceService`（落库 `item_index`）与 `ScoreReviewCandidateFactMapper`（复核面板）共用同一策略实例，两条路径顺序同步变化；但**存量 REVIEW_REQUIRED 快照不回填**，需重新解析才对齐。
- **前端列语义（A 方案）**：`ScoreParseTable` 表头“编号”改为“序号”，单元格统一渲染行号 `index + 1`，不再直接展示 `item.code`（`title` 悬停仍可见“标书原文编号：X”，详情弹窗标题也保留 code）。理由是后端行序已保证等于编号序，列里再显示可编辑的 code 会让“序号跳号”与“编号乱序”两种病象互相掩盖。
- **验证锚点**：`ScoreItemOrderPolicyTest#sort_project580SingleTableUniqueCodes_followsNumericOrder`（RED 时精确复现 `1,2,6,8,9,3,4,7,5`）与 `#sort_uniqueCodesWithChineseChapter_keepsChapterOrder`（中文章节仍优先）；前端 `ScoreParseTable.spec.js` / `ScoreParseV3.qa.spec.js` / `e2e/score-parse-candidate-review.spec.js` 同步为行号断言。
- **门禁说明**：本次改动属“非版式逻辑变更”（不触碰 Locator/Extractor/RecallLane/Merge/Filter 抽取链路），金标夹具的匹配谓词本身不比较 code 与顺序，无法覆盖该回归，故推送时按 README §146 走 `SCOREPARSE_GATE_SKIP=1` 逃生阀（PR 描述已说明理由）。
- **存量数据**：生产项目 580 的 9 行 `item_index` 已手工重排为 `CAST(code AS UNSIGNED)`（备份于服务器 `/tmp/score_item_580_20261021203733.sql`）；代码未部署前重新解析会复原乱序，此为已知代价。

## 33. 泛表头多级管道符维度解构与财务/信用知识库分类扩充（2026-10-24）

- **症状与根因**：生产环境（如项目 30/33/1119 等）多级复杂评分表格经 OCR/Markdown 提取后，一级大项表头（`商务部分`、`技术服务部分`、`商务评分`）落入 `dim`，真实业务维度（`业绩`、`企业资质与体系认证`、`仓储保障能力`）落入 `detail` 首行 Markdown 管道符格式。原有 `KnowledgeCategoryPolicy` 为防细则词干扰仅看 `dim`，导致大量客观项被判定为 `OTHER`；同时知识库分类体系缺失财务指标（速动比率、营业收入、现金流量等）与信用评价（纳税信用、不良记录等），75% 客观评分项显示“未识别到知识库匹配类别，待人工确认预计得分”。
- **修复措施**：
  1. **泛表头管道符解构**：仅当 `dim` 命中 `GENERIC_HEADERS` 集合时，解构 `detail` 首行提取管道符分列词；首列即真实子维度，短路判定，禁止回退穿透细则文本；若无管道符分列，仅允许召回高特异性被动指标（财务与信用），主动库类别严格保持 OTHER 降级保护；普通非泛表头（如“供应商业绩”）绝不扫描 `detail`，确保项目 22 防回归约束。
  2. **分类体系扩充**：新增 `CATEGORY_FINANCIAL` 与 `CATEGORY_CREDIT`，优先级置顶（`FINANCIAL > CREDIT > BRAND > WAREHOUSE > PERSON > PROJECT > CERT > OTHER`）。
  3. **服务编排层保守守卫**：`EstimatedScoreService` 针对 FINANCIAL 与 CREDIT 项明确标注“待人工确认预计得分”，置空预计得分并标 PENDING，杜绝假阳性。
  4. **领域抽取逻辑拆解**：将约束提取方法（面积、金额、资质、数量等）抽取至 `ScoreItemConstraintExtractor`，满足 300 行 line budget 门禁要求。
- **关联教训**：`docs/lessons/lessons-learned.md` §446。

## 34. 跨页评分表人工复核方案白名单死锁与双重错误弹窗消除（项目 1117，2026-11-03）

- **症状与根因**：项目 1117 属于跨页多物理表格，初始提取的 7 项评分事实均被打上 `OUT_OF_SCHEME` 待审标记。用户在前端人工将全部 7 项纳入方案（合计 100 分）并点击一键采纳时，后端报 400 错误：`一次采纳只能包含所选评分方案的事实`，且前端并排弹出两条重叠的错误提示。
  1. **后端死锁**：`ScoreParseReviewAppService.handleAdoptCandidates` 在统计合法方案池 `documentSchemes` 时执行了 `.filter(q -> !"OUT_OF_SCHEME".equals(q.scopeStatus()))`，导致当所有抽取事实初始均为方案外时，合法方案池被过滤成空集合；此时前端传入目标方案 ID，触发 `!requested.isEmpty() && !documentSchemes.contains(requested)` 强制抛出异常，封死人工兜底采纳路径。
  2. **前端重合弹窗**：Axios 全局响应拦截器（`src/api/client.js:254`）与业务 catch 块（`useScoreCandidateAdoption.js:86`）同时弹窗。
- **修复措施**：
  1. **后端白名单解除死锁**：引入 `allDocumentSchemes`，当 `verifiedDocumentSchemes` 为空时自动回退扩展至全量候选方案，只要人工指定的 `requestedScheme` 属于文档候选方案池（或文档无限定方案），均放行归入该方案生效，彻底消除死锁，同时保留多方案互斥混采防御。
  2. **前端请求跳过全局拦截弹窗**：在 `scoreParseApi.submitReview` 请求配置中注入 `{ skipGlobalErrorMessage: true }`，跳过 Axios 全局弹窗，错误统一由业务层单一管控。
- **验证与门禁**：
  - 新增最小失败测试 `submitReview_adoptCandidates_whenAllFactsOutOfSchemeAndRequestedSchemeProvided_allowsAdoption`（Red → Green）；
  - `ScoreParseReviewAppServiceTest` 全量 26 项测试 100% 通过；
  - 前端组件测试 `ScoreCandidateReviewCard.spec.js` 24 项全量测试 100% 通过；
  - 详见 `docs/lessons/lessons-learned.md` §487。

## 35. 评分模型 Fallback 异常击穿与入口路由器严格防倒退降级保护（项目 35，2026-11-04）

- **症状与根因**：生产环境项目 35（郑州市轨道交通2026年甄云平台办公类物资电商引入项目）评分表跨第 38–51 页，结构化提取获得了 4 项评分事实（欠分 `WEIGHT_SUM_MISMATCH`），`ScoreParseEntryRouter.isSatisfactory(result)` 返回 `false` 并触发懒加载调用大模型 fallback（`LegacyTextScoreExtraction`）。大模型对全文长文本进行 15 个分片提取，持续 6 分钟后在第 15 个分片因返回长 JSON 截断抛出 `ScoreModelRequestException`。由于 `ScoreParseEntryRouter.extract` 中调用 `fallback.get()` 缺乏异常捕获保护，异常一路穿透至应用层将任务强制标记为 `FAILED`，原本已提取出的结构化事实全部被废弃，违背了入口路由契约中的“严格防倒退（Monotonicity & Non-Regression）”原则。
- **修复措施**：
  1. **入口路由异常保全降级**：在 `ScoreParseEntryRouter.extract` 中增加对 `fallback.get()` 的 `try-catch` 保护。当 fallback 发生任何 `RuntimeException` 时，检查主结果是否已有内容（`hasContent(result) == true`）。若已有结构化提取事实，捕获异常、记录 warning 日志，并将异常信息作为诊断写入 `result.sourceDiagnostics`（前缀 `FALLBACK_MODEL_FAILED:`），平稳降级返回原结构化结果，确保任务顺利流转至 `REVIEW_REQUIRED`（人工复核），人工复核面板可正常呈现候选项供用户采纳；若主结果无有效内容，则原样抛出异常由任务失败边界收口。
- **验证与门禁**：
  - 新增单测 `fallbackExceptionDegradesToStructuredResultWhenContentExists`（Red → Green）与 `fallbackExceptionEscapesWhenNoStructuredContent`；
  - `ScoreParseEntryRouterTest`（15项）、`ScoreModelFailurePropagationTest`（3项）、`ScoreParseAppServiceTest` 及 `ScoreParseReviewAppServiceTest` 全量 74 项单测 100% 绿灯；
  - 本次改动属“非版式逻辑变更”（不触碰版式定位与抽取器逻辑），按门禁规范使用 `SCOREPARSE_GATE_SKIP=1`；
  - 详见 `docs/lessons/lessons-learned.md` §488。

## 36. 0分审查表污染多表判定与 parseText 作用域重扫 O(N²) 假死（全项目系统性根因，2026-11-05）

- **症状与根因**：生产环境（如项目 1120 中国雅江集团采购文件、项目 35 等），标书内仅有一张真正的 100 分综合评分表（商务+报价+技术闭合），解析完成后状态被误判为 `REVIEW_REQUIRED / DECLARED_TOTAL_UNKNOWN`，无法单表 100 分自动通过。同时长文本解析出现 CPU 假死超过 67 分钟。
  1. **0分审查表污染多表判定**：`ScoreTableSplitPolicy` 在计算 `leafBearingTablesCount` 时，未排除 weight 全为 0 的资格审查/形式审查/合规性表格，导致实际只有 1 张有效评分表的文档被误判为多表场景（`multipleTables == true`），逐表要求声明总分未果后转入 `MULTI_TABLE_DECLARED_TOTAL_UNKNOWN` 阻断自动采纳。
  2. **parseText 兜底全文重扫 O(N²)**：`OriginalScoringContext.parseText` 兜底窗口遍历中，内层循环每行调用 `textScopeAt(text, i)`，逐行触发 `textScopes(text)` 全文扫描，12 万字文档引发数十万次全文正规化与章节匹配，导致线程高 CPU 假死 67 分钟。
  3. **生产环境版本化开关未开启**：`/etc/xiyu-bid/backend.env` 缺少 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED=true`，导致版本化快照不写入，前端返回 409。
- **修复措施**：
  1. **过滤 0 分审查表**：`ScoreTableSplitPolicy` 新增 `scoringLeafBearingTablesCount`，仅统计包含至少一个 `weight > 0` 末级评分项的表格，无正分表时安全回退原有逻辑，彻底隔离审查表干扰。
  2. **有序二分作用域定位**：`OriginalScoringContext.parseText` 提升 `textScopes(text)` 至外层单次预计算（O(N)），内层调用 `OriginalScoringTextScopePolicy.findScopeAt` 二分搜索（O(log N)），将整体耗时从小时级降至毫秒级。
- **验证与门禁**：
  - 新增 2 个回归测试用例覆盖 0 分审查表场景；
  - `ScoreTableSplitPolicyTest`（9/9）、`OriginalScoreFactIdentityTest`（13/13）等 26 项单元测试 100% 绿灯；
  - 详见 `docs/lessons/lessons-learned.md` §490。

## 37. 0分审查表事实进入 noEvidenceFacts 导致 MISSING_EVIDENCE 阻断（2026-11-05 补充根因）

- **症状与根因**：
  1. 尽管在 `ScoreTableSplitPolicy` 中新增了 `scoringLeafBearingTablesCount` 判定单表，但在单表无单独总分证据分支（`ownTotals.isEmpty()` && `!multipleTables`）中，循环遍历所有表格时，仍然把所有表格（包含 t-0005/0006/0008/0028 等 0 分审查表）的抽取事实全部无条件 `noEvidenceFacts.addAll(table.facts())` 加入评分事实池。
  2. 审查表的事实缺失分值定位证据（`weight=null` 且 `weightEvidence=null`），导致进入 `ScoreCompletenessPolicy` 后被判定为 `missingEvidenceCount > 0`（出现 73 个缺失证据事实），触发 `MISSING_EVIDENCE` 阻断，使得任务最终降级为 `REVIEW_REQUIRED`，未能执行 `AUTO_APPROVED` 写入 `score_item` 表，页面呈现历史脏数据。
  3. 前端交互变更原因：生产环境此前误开启了实验性的多方案版本化特性开关 `APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED`，导致渲染了多方案激活卡片替代了一键采纳卡片；已将其关闭并恢复稳定版本。
- **修复措施**：
  1. `ScoreTableSplitPolicy` 中增加 `isScoringTable` 判断，在 `ownTotals.isEmpty()` 分支仅将实质评分表加入 `noEvidenceFacts`，彻底排除无分/0分审查表对完整性证据池的污染；
  2. 保持 `OriginalScoringTextScopePolicy.findScopeAt` 首匹配语义，避免二分搜索在重叠作用域下跳过父章节；
  3. 新增回归测试 `singleScoringTable_surroundedByAuditTablesWithoutWeightOrEvidence_evaluatedCleanlyWithoutAuditDefects`，保证审查表不污染单表 AUTO_APPROVED。




