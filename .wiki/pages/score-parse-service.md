---
title: AI 评分标准解析后端服务
space: engineering
category: module
tags: [评分标准, scoreparse, LLM, 知识库匹配, 异步任务, 阶段打分]
created: 2026-08-15
updated: 2026-08-18
health_checked: 2026-08-17
sources:
  - specs/041-ai-score-parse-backend/spec.md
  - specs/041-ai-score-parse-backend/plan.md
  - specs/045-bid-file-streaming-scoring/spec.md
  - specs/046-fix-score-parse-ghost/spec.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/
  - docs/implementation-notes/ai-score-parse-backend.md
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
      四路召回（关键词/文档结构/评分规则语义/LLM全文）→ RecallOutcome(candidates, skippedChunks)
      → ScoreItemMergePolicy 合并去重
      → GhostCandidateFilter 幽灵过滤（spec 046 FR-001）
      → WeightSumCheck + ItemCountCheck 闭环校验（差异触发二次回补）
      → WeightOverloadTrimPolicy 超载剔除：合计>100.5 两轮剔嫌疑（spec 046 FR-002）
      → ScoreTypeClassificationPolicy 客观/主观分类
      → ScoreItemPersistenceService 覆盖落库（ScoreItemOrderPolicy 编号数值序，空code落""）
      → EstimatedScoreService 阶段1预计得分（链尾）
```

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
- **幽灵项三重根因与治理（2026-08-18 spec 046，project 212 权重 200 事故）**：LLM 把表头/汇总行当评分项召回（如 dim=detail="得分"、weight=100），去重不滤、权重校验只补漏不剔除，三项叠加导致落库权重合计 200。治理（三个 domain 纯函数，TDD 全覆盖）：
  - `GhostCandidateFilter`（FR-001）：规则1 dim 与 detail 归一化（trim+去空白）相同且 ≤4 字 → 表头回声滤除；规则2 code 空白且 sourceText 空白 → 非条款行滤除；有 code 或 sourceText 任一即保留（真实项零误删）。插在 merge 之后、weight 校验之前。
  - `WeightOverloadTrimPolicy`（FR-002）：合计 >100.5 时两轮剔嫌疑（先无 code → 再无 sourceText），逐个剔+重算达标即停；剔尽仍超保留现状（weightWarning 兜底）。插在完整性回补之后。
  - `ScoreItemOrderPolicy`（FR-003/004）：落库按编号数值序（`^\d+(-\d+)?$` major/minor，4-1<4-2），不可解析稳定置后；空 code 落 `""` 不再补行号假编号（旧逻辑假编号会与真实编号冲突）。
  - **LLM chunk 失败可见性（FR-005）**：单 chunk 失败重试 1 次，两连败跳过并计数 `RecallOutcome.skippedChunks` → `score_parse_task.skipped_chunks`（V1193）→ Meta 透出 `skippedChunks`。此前 chunk 静默跳过用户无感知，解析质量问题不可见。
  - **@Async 落库事务缺失第二处（2026-08-18 spec 046 部署验证发现）**：`ScoreItemPersistenceService.persistItems` 内的 `deleteByScoreItemIdIn`/`deleteByProjectId` 同样在 `@Async` 线程抛 `No EntityManager with actual transaction`，全任务在落库阶段终止（召回结果全丢）。修复：`persistItems` 加 `@Transactional`（跨 bean 调用代理生效）。与 §7 `ScoreResultOverwriter` 同类根因——**@Async 管线里所有含 bulk delete/save 的落库方法都必须显式开事务**，save 单条可侥幸、delete 必炸。
  - **LLM 网关"成功返回空候选"不触发重试也不计 skip（2026-08-18 测试环境事故）**：ai-tech.ehsy.com 的 qwen3.7-max 自某时刻起对候选提取 prompt 持续返回空 `candidates`（HTTP 200 + 合法 JSON + 空数组），召回四 `count=0, skipped=0` 零异常，三次稳定复现；切 `gemini-3.5-flash` 后同文档恢复 count=10。排查要点：skippedChunks=0 且 count=0 ≠ 代码回归，先换模型对照再查代码；此形态 FR-005 覆盖不到（重试只兜 HTTP/异常失败），只能靠 weightWarning + 完整性回补兜底。

## 8. 验证基线

- 单测：解析编排/持久化覆盖/匹配五类/预计得分/阶段2打分/超时扫描/启动恢复 全绿（~143 用例含 biddraftagent 回归）
- **spec 046 测试环境真实验证（2026-08-18，project 212）**：7 项全真实评分项（幽灵"得分 得分 100"行已消失）、DB item_index 1-7 与 code 数值序一致、`skipped_chunks=0` 落库且 meta 透出、事务修复后落库 COMPLETED。totalWeight=90≠100（LLM 提取完整性缺口 10 分，`weightWarning=true` 兜底透出）——对照修复前 200 事故已根治；权重缺口属解析质量问题非 spec 046 范围。
- 架构：ArchitectureTest + FPJavaArchitectureTest + ProjectAccessGuardCoverageTest 通过
