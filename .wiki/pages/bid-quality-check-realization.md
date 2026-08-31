---
title: 标书质量核查实质化架构（spec 049）
space: engineering
category: module
tags: [质量核查, compliance, LLM 混合裁决, 证据链, FP-Java, 语义RAG, 结构化输出]
created: 2026-08-20
updated: 2026-08-31
health_checked: 2026-08-31
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
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SemanticPageRetriever.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/KnowledgeBaseOnDemandLoader.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/LlmVerdictParser.java
  - backend/src/main/java/com/xiyu/bid/ai/client/AiProvider.java
  - backend/src/main/java/com/xiyu/bid/ai/client/OpenAiCompatibleClient.java
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

## 2. 混合裁决架构（引擎三路与语义 RAG）

- **确定性 lane**（`DeterministicCheckPolicy`，18 项）：毫秒级规则核查，不依赖 LLM。
- **语义规则 lane**（`SemanticRulesLane`，7 项）：硬信号先行——资质匹配/否决条款/负偏离等可由规则直接裁决；无硬信号返回 empty 移交 LLM。
- **LLM 裁决 lane 与全标书语义 RAG**（`LlmAdjudicator` + `SemanticPageRetriever`）：只做语义项裁决层，**不做全量直出**；彻底废弃前缀硬截断（6000/4000 字符），改走全标书语义 RAG 检索与结构化输出通道：
  1. **语义页面检索（SemanticPageRetriever）**：纯领域核心（FP-Java 无依赖）。提取 7 项检查项静态线索（`addStaticItemCues`，如资质证书/偏离表/付款方式/审计报告等）与招标要求动态线索（`addDynamicRequirementsCues`），对 `DocumentContent.pages()` 进行相关性打分（标题匹配 +50、正文关键词 +10、词频 +2/次上限 20）。
  2. **大预算与全文透传**：投标文件默认预算 60,000 字符（>=60K 红线），招标文件默认预算 30,000 字符。若文档总字符量在预算内，100% 全文无损透传；超预算则选取得分最高的 TOP-N 页面，并按自然页码升序重新排序装配。
  3. **物理页码元数据保留**：上下文输出强制保留 `--- [第 X 页] ---` 物理页码标识，确保 AI 在生成裁决结论与证据引用时具备真实页码锚点。
  4. **结构化输出通道（chatCompletionJsonObject）**：通过 `AiProvider.chatCompletionJsonObject` 注入 `response_format: {"type": "json_object"}`，从协议层保证大模型输出标准 JSON 对象；网关不支持时通过 `AtomicBoolean` 单向原子降级并缓存，结合 `LlmVerdictParser` 容错提取与仅针对 `Invalid` 的 1 次追加严格约束重试，构成端到端四层防护。

## 3. 失败传导纪律（FP-Java 值返回）

通道一切失败在边界适配器（executor `buildLlmPort`）吸收为 `LlmVerdict.ChatOutcome` sealed 四值，纯核心无异常流：

| 通道结果 | 语义 | 单项处理 |
|----------|------|----------|
| `Success` | 调用成功且响应已解析 | 按 verdict 出 PASS/FAIL/MANUAL |
| `Invalid` | 响应不可解析（经 1 次重试后仍非法） | MANUAL + LOW_CONFIDENCE |
| `Noop` | AI 未启用（IllegalStateException） | MANUAL + MANUAL_REVIEW_REQUIRED，任务仍 COMPLETED |
| `Failure` | 调用异常/超时 | MANUAL + LOW_CONFIDENCE，不阻断整体 |

结构化调用通道的多层防御契约：
- **第一道防线**：`aiProvider.chatCompletionJsonObject`（`response_format: {"type": "json_object"}`），消除大部分格式漂移。
- **第二道防线**：网关 400 不支持时自动捕获，`responseFormatUnsupported` 原子标记置 true，平滑单向回退到普通 `chatCompletion`。
- **第三道防线**：`LlmVerdictParser` 在 service 边界层吸收 checked 异常，利用括号配平扫描提取首个 JSON 对象、归一化中英文状态并兜底空结论。
- **第四道防线**：`buildLlmPort` 仅对解析失败（`Invalid`）追加严格 JSON 约束重试 1 次（`Noop` / `Failure` 不重试），确保瞬态波动可自愈。
- **终极兜底**：纯核心生成用户可读文案（`FALLBACK_INVALID_CONCLUSION` / `FALLBACK_FAILURE_CONCLUSION`），任何异常路径均不向前端暴露技术堆栈。

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
13. **标书硬截断导致中后段关键内容对大模型不可见**（2026-08-30 架构重构）：旧版 `LlmAdjudicator.buildPrompt` 采用前缀硬截断（投标文件截断前 6000 字符、招标文件截断前 4000 字符），导致几十页上百页标书的技术方案、偏离表、项目业绩、财务报表、资质证书等分布在中后段的核心内容完全无法进入大模型上下文，大模型因缺乏依据大量降级为 MANUAL 待人工确认。解法：设计纯核心 `SemanticPageRetriever`，废弃 `substring` 硬截断；采用“大预算（>=60K 字符）+ 预算内全文无损透传 + 超预算 TOP-N 语义打分筛选 + 自然页码重新排序 + 保留 `--- [第 X 页] ---` 物理页码标识”的语义 RAG 机制，既保证长标书关键章节对 AI 100% 可见，又保留真实证据链定位能力。
14. **网关不支持 response_format=json_object 时的静默 400 与原子降级标记**（2026-08-30 结构化输出演进）：部分 OpenAI 兼容 API 网关或代理服务不支持 `response_format: {"type": "json_object"}` 参数，直接抛出 HTTP 400 Bad Request（如 "unrecognized field", "unsupported parameter response_format"）。若简单捕获重试，会导致每个请求都遭遇 400 失败并产生双倍网络往返延迟。解法：在 `OpenAiCompatibleClient` 中引入 `AtomicBoolean responseFormatUnsupported` 线程安全标记；在 `chatCompletionJsonObject` 捕获到参数不支持的 400 错误时，一次性将标记置为 `true` 并降级为普通 `chatCompletion`；后续所有请求直接走普通调用，彻底消除多余的 400 异常与额外延迟；同时下游保留 `LlmVerdictParser` 的 JSON 括号配平扫描作为兜底解析防线。
15. **异步线程池显式绑定与事务提交时序解耦**（2026-08-31 稳定性加固）：`@Async` 必须显式指定目标线程池 `@Async("aiTaskExecutor")`，杜绝多 Executor 容器下无 `@Primary` 导致的调度歧义以及在 CallerRunsPolicy 下同步占用 Tomcat 主请求线程导致的前端 30s 请求超时；同时 `triggerQualityCheck` 在 `save(taskEntity)` 后必须使用 `TransactionSynchronizationManager` 的 `afterCommit` 钩子安全触发异步流水线，消除主事务与 `BidQualityCheckProgressUpdater` 独立事务（`Propagation.REQUIRES_NEW`）之间的行可见性竞态。


## 5. 前端契约（2026-08-25 UI 改造：紧凑行 + 居中详情弹窗）

drawer 结构：汇总平铺卡（左上投标文件/核查时间，右上「重新核查」二次确认 + 「AI 评分」）→ 4 统计卡（点击筛选状态，「待确认项」）→ 三分类定位条（基本信息/分项合规/附件清单，点击展开分组并平滑滚动，**不**改状态筛选）→ 分组紧凑行。

- **紧凑行**（`QualityCheckItemCard`）：状态图标 + 检查项 + 结论（2 行省略，已剥离 reasonCode 前缀，前置"检查结论："）+ 右侧状态标签；整行可点击，不再平铺证据。
- **详情弹窗**（`QualityCheckDetailDialog`，560px 居中）字段对照表：检查状态/检查项说明（优先 `description` 即 PRD 2.3 检查内容，旧快照缺失时回退名称）/AI 检查结论恒显；问题定位（BID 证据页码去重「第 12 / 56 页」）/原文摘录（BID quote，不高亮）/联动数据来源（KNOWLEDGE_BASE 证据→四库链接，点击 `router.push /knowledge/*`）仅非 PASS 显示；建议操作按状态固定文案。
- **SCORING_RESPONSE 项**：客观项得分合计非空时结论前置「客观项得分：XX分 ↗」链接（复用 `scoreParseApi.getItems/getResults` + `normalizeScoreItem`/`mapScoreResults`；静默降级，未打分不展示）；点击经事件链 QualityCheckDrawer → DraftingStage → ProjectDetailMainColumn 透传 `{stage:2, autoScore:true}` 打开评分抽屉并自动打分。
- **展示纯核心**：`composables/qualityCheckDetail.js` 单一数据源（`STATUS_META`/`stripReasonCode`/`extractBidPages`/`extractBidQuote`/`extractKbLinks`/`sumObjectiveActualScore`），紧凑行与弹窗共用，禁止在组件内各写一套状态映射。
- **保留纪律**：trigger 同步 4xx（无投标文件）提示后端消息且不进入轮询；429 走全局友好提示（catch 体内禁用 ElMessage.error）。
- **展示文案单一数据源**（2026-08-30）：25 项检查项名称与检查项说明（检查内容）以 `BidQualityCheckItem` 枚举为唯一源（对齐 PRD 2.3，`BidQualityCheckItemDefinitionTest` 防漂移）；报告读取侧 `toReportDTO` 按 itemCode 回填，历史快照展示口径自动对齐。

## 6. 后续任务（Backlog）

- **长文档动态自适应 Token 预算与模型上下文窗口感知**：当前 `SemanticPageRetriever` 采用固定字符预算（标书 60K、招标 30K）。后续可根据配置的模型上下文窗口大小（如 128K/256K tokens）动态自适应上调字符预算，并在 Tokenizer 层进行更精准的 token 计数。
- **多模态与表格深度语义增强**：对于长文档中的复杂表格（如设备清单、详细报价明细表），可引入 Markdown 表格结构化保留与表格专用切片算法，进一步提升大模型对复杂表格行列交叉关系的理解准确度。
