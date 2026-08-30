---
title: 标书质量核查实质化架构（spec 049）
space: engineering
category: module
tags: [质量核查, compliance, LLM 混合裁决, 证据链, FP-Java]
created: 2026-08-20
updated: 2026-08-30
health_checked: 2026-08-30
sources:
  - specs/049-bid-quality-check-realization/spec.md
  - src/views/Project/stages/components/QualityCheckDrawer.vue
  - src/views/Project/stages/components/QualityCheckDetailDialog.vue
  - src/views/Project/stages/composables/qualityCheckDetail.js
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckProgressUpdater.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BidDocumentQualityEngine.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/LlmAdjudicator.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/KnowledgeBaseOnDemandLoader.java
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
  - docinsight-engine
  - ai-provider-configuration
---
# 标书质量核查实质化架构（spec 049）

spec 049 将标书质量核查从"固定文案证据 + page=1 硬编码"的验收失真状态实质化为全真实链路：真实投标文件 → 真实招标要求 → 四知识库真实查询 → 真实页码/原文定位 → LLM 混合裁决。旧同步链路已删除，唯一主线为异步任务链（trigger → 轮询 → 报告）。

## 1. 唯一主线（五步真实数据流）

| 步骤 | 组件 | 产物 |
|------|------|------|
| 1 投标文件 | `ComplianceTargetLoader` + Sidecar 解析 | `DocumentContent`（分页 PageContent[]，真实 fileHash） |
| 2 招标要求 | `TenderRequirementsExtractor`（LLM 结构化提取） | `TenderRequirements`（硬性资质/评分项/否决条款；失败降级 `missing()` 哨兵不阻断） |
| 3 知识库 | `KnowledgeBaseOnDemandLoader` 四库按需查询 | `KnowledgeQueryOutcome`（`KnowledgeMatch` 列表 + `failedSources` 失败源） |
| 4 证据定位 | `EvidenceLocator` 关键词真实定位 | 命中=真页码+原文摘录；未命中 page=null+空 quote（**禁止填 1**） |
| 5 裁决 | `BidDocumentQualityEngine` 编排 | 25 项 PASS/FAIL/MANUAL_REVIEW + reasonCode |

## 2. 混合裁决架构（引擎三路）

- **确定性 lane**（`DeterministicCheckPolicy`，18 项）：毫秒级规则核查，不依赖 LLM。
- **语义规则 lane**（`SemanticRulesLane`，7 项）：硬信号先行——资质匹配/否决条款/负偏离等可由规则直接裁决；无硬信号返回 empty 移交 LLM。
- **LLM 裁决 lane**（`LlmAdjudicator`）：只做语义项裁决层，**不做全量直出**；prompt = 招标要求 + 知识库真实记录 + 投/招标原文摘录，固定 JSON 输出契约。

## 3. 失败传导纪律（FP-Java 值返回）

通道一切失败在边界适配器（executor `buildLlmPort`）吸收为 `LlmVerdict.ChatOutcome` sealed 四值，纯核心无异常流：

| 通道结果 | 语义 | 单项处理 |
|----------|------|----------|
| `Success` | 调用成功且响应已解析 | 按 verdict 出 PASS/FAIL/MANUAL |
| `Invalid` | 响应不可解析 | MANUAL + LOW_CONFIDENCE |
| `Noop` | AI 未启用（IllegalStateException） | MANUAL + MANUAL_REVIEW_REQUIRED，任务仍 COMPLETED |
| `Failure` | 调用异常/超时 | MANUAL + LOW_CONFIDENCE，不阻断整体 |

reasonCode 五值（PRD §3.5）：EVIDENCE_INSUFFICIENT / AMBIGUOUS / LOW_CONFIDENCE / REFERENCE_MISSING / MANUAL_REVIEW_REQUIRED；MANUAL 态 conclusion 带 `[CODE] ` 前缀（`ManualReasons` 统一格式化）。知识库单库异常同理：记入 `failedSources` → 依赖该库的检查项 MANUAL + REFERENCE_MISSING。

## 4. 关键陷阱

1. **证据失真比无证据更危险**：固定文案/硬编码页码让验收误判功能完成。G5 门禁用 grep 零命中验收（固定文案 / `pageNumber, 1` / isDemoMode，见 quickstart §5）。
2. **switch 模式匹配触发 FP 门禁**：Java 21 对 sealed 接口的 switch 模式匹配编译产物含 `new MatchException`，被"纯核心禁止构造 Throwable"判据命中；纯核心分派用 instanceof 序贯链（`docs/lessons/lessons-learned.md §141`）。
3. **JSON 解析不进纯核心**：`LlmVerdictParser` 置 service 边界层（Jackson checked 异常），同 `TenderRequirementsParser` 先例。
4. **存储口径已定案**（G6，2026-08-20 产品确认）：PRD §4.4"不持久化 + Redis TTL"废弃，采纳方案 B——DB 保存 + 文件 hash 缓存 + force 重跑（spec §5.2，待下次 PRD 修订同步）。
5. **确定性 lane 误判修复**（2026-08-21 审查跟进）：`declaredDeviation` 的 `contains("负偏离")` 把"偏离表：无负偏离"（标准合规表述）误判 FAIL → 改三分支（否定前缀豁免 → PASS，未提及交 LLM）；`vetoFirstStage` 精确匹配 LLM 提取资格全文，空白/标点差异误判缺失 FAIL → 匹配前归一化（去空白/全半角/标点）。一票否决项（FAIL=废标级）禁止裸 contains 直出。
6. **规则 lane 含否定语义用紧邻前缀判断**：否定词（无/未/零/没有/不存在/不含/无任何）须落在关键词紧邻前缀（前 4 字符），避免"付款方式存在负偏离"（前缀"存在"）被误豁免。
7. **大文件与二进制降级禁止调用 Files.readString**：非纯文本格式（`.docx`/`.pdf`/`.zip`）或大文件（$>5\text{MB}$）本地降级时禁止调用 `Files.readString`（防 DirectBuffer 堆外 OOM），必须只计算哈希并返回空文本。
8. **超大 Docx 标书媒体前置过滤**：超过 20MB 的 `.docx` 标书在向 Sidecar 发送前由 `DocxMediaFilter` 流式滤除 `word/media/` 媒体流，请求体缩小 99%（1.66GB $\to$ 1.5MB），彻底消除 Sidecar 512MB 上传限制与网络传输超时（`specs/053-docx-media-stream-filter-fast-intake/`）。
9. **异步触发接口禁止长耗时同步 I/O**：`POST /api/compliance/bid-quality/trigger/{projectId}` 端点禁止在主线程同步加载与解析大文件（避免击穿前端 Axios 30s 超时阈值）；必须在 10ms 内完成元数据校验并入库返回 `taskId`，文档加载/Sidecar解析/LLM分析全部在异步线程完成。
10. **异步任务中间进度使用独立事务即时提交**：`BidQualityCheckProgressUpdater` 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，避免整个长任务被顶层大事务包裹导致中间进度更新滞留在缓冲区，确保前端轮询秒级感知 20%/60%/80% 真实进度。
11. **LLM 裁决响应解析三层防御**（2026-08-30）：模型常以 ```json 围栏包裹/JSON 前后加解释文字/返回中文状态（通过/待确认）/MANUAL 缺 conclusion，旧版 `readTree(原始响应)` 直接失败并把"响应结构非法"技术文案暴露给用户。修复分三层：① `LlmVerdictParser` 配平扫描提取首个 JSON 对象 + status 中文同义归一 + MANUAL 空结论兜底；② 边界适配器仅解析失败追加强约束重试 1 次（Noop/Failure 不重试）；③ 纯核心兜底结论用户可读（"AI 本次未能自动裁决该条款…已转人工确认"），任何路径都有有意义的检查结论。历史报告快照在 `toReportDTO` 按 itemCode 回填标准名称/说明（`withCanonicalDefinition`），无需数据迁移。
12. **LLM 裁决解析失败全链路可观测**（2026-08-30）：#11 的三层防御上线后，Invalid 路径一条日志都没有——解析失败与重试都是静默的，上线后若仍有解析失败将无从排查。补齐：`chatAndParse` 解析失败时 `log.warn` 原始响应（截断 500 字）；`buildLlmPort` 重试前 `log.warn`；重试契约由 `BidQualityCheckAsyncExecutorTest` 四个单测锁定（解析失败重试成功 / 重试后仍非法 Invalid / Noop 不重试 / Failure 不重试）。

## 5. 前端契约（2026-08-25 UI 改造：紧凑行 + 居中详情弹窗）

drawer 结构：汇总平铺卡（左上投标文件/核查时间，右上「重新核查」二次确认 + 「AI 评分」）→ 4 统计卡（点击筛选状态，「待确认项」）→ 三分类定位条（基本信息/分项合规/附件清单，点击展开分组并平滑滚动，**不**改状态筛选）→ 分组紧凑行。

- **紧凑行**（`QualityCheckItemCard`）：状态图标 + 检查项 + 结论（2 行省略，已剥离 reasonCode 前缀，前置"检查结论："）+ 右侧状态标签；整行可点击，不再平铺证据。
- **详情弹窗**（`QualityCheckDetailDialog`，560px 居中）字段对照表：检查状态/检查项说明（优先 `description` 即 PRD 2.3 检查内容，旧快照缺失时回退名称）/AI 检查结论恒显；问题定位（BID 证据页码去重「第 12 / 56 页」）/原文摘录（BID quote，不高亮）/联动数据来源（KNOWLEDGE_BASE 证据→四库链接，点击 `router.push /knowledge/*`）仅非 PASS 显示；建议操作按状态固定文案。
- **SCORING_RESPONSE 项**：客观项得分合计非空时结论前置「客观项得分：XX分 ↗」链接（复用 `scoreParseApi.getItems/getResults` + `normalizeScoreItem`/`mapScoreResults`；静默降级，未打分不展示）；点击经事件链 QualityCheckDrawer → DraftingStage → ProjectDetailMainColumn 透传 `{stage:2, autoScore:true}` 打开评分抽屉并自动打分。
- **展示纯核心**：`composables/qualityCheckDetail.js` 单一数据源（`STATUS_META`/`stripReasonCode`/`extractBidPages`/`extractBidQuote`/`extractKbLinks`/`sumObjectiveActualScore`），紧凑行与弹窗共用，禁止在组件内各写一套状态映射。
- **保留纪律**：trigger 同步 4xx（无投标文件）提示后端消息且不进入轮询；429 走全局友好提示（catch 体内禁用 ElMessage.error）。
- **展示文案单一数据源**（2026-08-30）：25 项检查项名称与检查项说明（检查内容）以 `BidQualityCheckItem` 枚举为唯一源（对齐 PRD 2.3，`BidQualityCheckItemDefinitionTest` 防漂移）；报告读取侧 `toReportDTO` 按 itemCode 回填，历史快照展示口径自动对齐。

## 6. 后续任务（Backlog）

- **LLM 裁决切换结构化输出通道（response_format=json_object）**：当前裁决走裸 `AiProvider.chatCompletion` + prompt 约定，陷阱 #11/#12 的三层防御 + 日志均属事后容错；消灭此类解析失败的终态是裁决调用改走结构化输出。仓库已有先例基建：`biddraftagent` 的 `OpenAiSdkStructuredOutputTransport`（json_schema 优先、网关不支持时降级 json_object，一次性降级标记避免双倍调用）与 `OpenAiJsonObjectPayloadReader`。迁移要点：① `AiProvider` 接口新增结构化调用方法（Mock/Routing/OpenAI 兼容客户端同步实现）；② `buildLlmPort` 复用降级策略——部分网关不支持 response_format，必须保留 `LlmVerdictParser` 容错解析作为最后一道防线，不得拆除；③ 跨 ai/compliance 两模块且涉及网关兼容性验证，按 spec 流程立项执行。

