---
title: 标书质量核查实质化架构（spec 049）
space: engineering
category: module
tags: [质量核查, compliance, LLM 混合裁决, 证据链, FP-Java, 语义RAG, 结构化输出, 资质智能匹配, 三级穿透]
created: 2026-08-20
updated: 2026-11-11
health_checked: 2026-11-11
sources:
  - specs/049-bid-quality-check-realization/spec.md
  - src/views/Project/stages/components/QualityCheckDrawer.vue
  - src/views/Project/stages/components/QualityCheckDetailDialog.vue
  - src/views/Project/stages/components/qualityCheckDetailDialog.css
  - src/views/Project/stages/composables/qualityCheckDetail.js
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/CaCertificateEvidence.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/CaCertificateEvidencePort.java
  - backend/src/main/java/com/xiyu/bid/resources/application/CaCertificateEvidenceQueryAdapter.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/QualificationQueryDelegate.java
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckProgressUpdater.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/QualificationDetail.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationMatchPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicQualificationExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationDetailReconciler.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BidDocumentQualityEngine.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BasicInfoCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/LlmAdjudicator.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SemanticPageRetriever.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/TenderDocumentLoader.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/KnowledgeBaseOnDemandLoader.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/LlmVerdictParser.java
  - backend/src/main/java/com/xiyu/bid/ai/client/AiProvider.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DepositRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BidLetterRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/FinancialRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicDepositExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicBidLetterExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicAuthorizationExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/AuthorizationLetterCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/AuthorizationRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicBusinessLicenseExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BusinessLicenseCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BusinessLicenseRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicIdAuthLetterExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/IdAuthLetterCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/IdAuthLetterRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/FinancialStatementCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/PriceRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicPriceExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/PriceConsistencyCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BidOpeningTableCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/CaCertificateRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicCaCertificateExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/CaCertificateCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/FrontTableRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicFrontTableExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/FrontTableResponsePolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/ResponsivenessReviewPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeviationTableExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicRequirementsFuser.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/AdjudicationGuidelines.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicVetoExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationGradePolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationVetoPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/QualificationReviewPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/RequirementSectionParser.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/ScoringResponsePolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/ClarificationResponsePolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BusinessTermsRequirement.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/DeterministicTermsExtractor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/ItemizedPriceCalculator.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/TermConsistencyCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/TableCellIntegrityCalculator.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SealIntegrityCheckPolicy.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/FinancialStatementAccuracyCalculator.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/TimelineReasonablenessPolicy.java
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
  - bid-quality-check-pagination-fix
  - bid-quality-multifile-check
---
# 标书质量核查实质化架构（spec 049）

> spec 049 将标书质量核查从"固定文案证据 + page=1 硬编码"的验收失真状态实质化为全真实链路：真实投标文件 → 真实招标要求 → 四知识库真实查询 → 真实页码/原文定位 → LLM 混合裁决。旧同步链路已删除，唯一主线为异步任务链（trigger → 轮询 → 报告）。
> 核心目标：构建可溯源证据链（页码+原文切片）、严格区分系统判定与人工复核，杜绝假通过与假不通过。

## 2026-10 复核增量（09-18 后合入的行为变更，阅读下文时以本节为准）

| 变更 | commit | 要点 |
|---|---|---|
| 保证金凭证视觉核验 | `623caf5e5` | 保证金回执接页面视觉通道（与签章视觉同源 `/convert-images`） |
| CA 平台身份核实 | `7de6fee9b` | CA 证书检查核实平台身份并保留原文证据 |
| 证据边界 + 财务人工复审 | `e20fc9a22` | 跨源证据穿透治理，财务审查强制人工复审边界，详见 [[bid-quality-check-evidence-conflicts]] |
| 承诺函签字检查契约化 | `a881dbfd5` / `2e645058c` | commitment signature check 显名与契约说明 |
| 资质文案/目录页治理 | PR !2872 | 根治资质核查文案自相矛盾、目录页干扰与知识库来源杂音 |
| 解析失败分类化（spec 066） | `6da93ac38` 等 | `SidecarStreamDocumentAdapter` 返回 `SidecarParseOutcome` 五分类，失败不再静默空册，详见 [[bid-quality-check-parse-timeout-fix]] |

## 1. 25 项检查项架构布局与数据流

25 项标准检查项（PRD 2.3）按核查实现手段划分为两条车道：
- **确定性 lane**（`DeterministicCheckPolicy`，18 项）：毫秒级规则核查，不依赖 LLM。
  - 基本信息（9 项）：封面要素（项目名/投标人/日期）、投标函格式规范（含《报价函》/《响应函》/公文称谓）、授权委托书（法定代表人+受托人）、营业执照（三证合一/统一代码）、报价一致性（大写转数值与小写绝对比对）、计价货币单位（人民币/元）、工期响应、签署盖章占位、联系人信息（含有效电话校验）。
  - 分项合规（4 项）：文件完整性（多册合并总页数 >= 3）、模板格式规范（DOCX/PDF 格式合规）、废标敏感词扫描（含否定词/承诺修饰语义反转识别）、承诺书签章规范。
  - 附件清单（5 项）：法定代表人身份证明、开标一览表、投标保证金凭证（免收标免除）、CA 证书有效性（非网标免除）、保证金退还账户信息（免收标免除）。
- **语义/知识库 lane**（`SemanticRulesLane`，7 项）：大模型语义比对 + 四知识库检索。
  - 四知识库联动：资质要求审查（对齐资质库三方比对基准）、业绩要求响应（案例库业绩特征提取与加权比对）、财务指标审查（财务数据提取）、实质性条款响应（负偏离判定）、服务方案与技术响应（方案契合度）、人员配置合规性（项目经理/技术负责人证书比对）、评分标准逐条应答（PRD 2.3 评分点提取与响应度打分）。

## 2. 真实数据流五步闭环

```mermaid
sequenceDiagram
    participant User as 前端 (QualityCheckDrawer)
    participant AppService as BidQualityCheckTaskAppService
    participant Executor as BidQualityCheckAsyncExecutor
    participant Engine as BidDocumentQualityEngine
    participant Storage as OSS / MinIO
    participant Sidecar as DocumentConverterSidecar
    participant DB as MySQL

    User->>AppService: POST /projects/{id}/compliance/tasks (trigger)
    AppService->>DB: INSERT task(status=INIT)
    AppService-->>User: 200 {taskId, status: INIT}
    AppService->>Executor: executeTaskAsync(taskId)
    Executor->>Storage: 下载投标文件与招标文件
    Executor->>Sidecar: POST /convert 转换多文档
    Sidecar-->>Executor: {fullText, pages, pageCount}
    Executor->>Engine: executeChecks(bidDoc, tenderDoc, projectContext)
    Engine->>Engine: 确定性 Lane (18 项)
    Engine->>Engine: 语义/知识库 Lane (7 项)
    Engine-->>Executor: List<QualityCheckItemResult>
    Executor->>DB: INSERT compliance_check_results
    Executor->>DB: UPDATE task(status=COMPLETED)
    User->>AppService: GET /projects/{id}/compliance/latest
    AppService-->>User: 200 QualityCheckReportDTO
```

## 3. 证据链规范（QualityEvidence）

每一个检查项结果必须携带 `List<QualityEvidence>`，严禁空证据假判定：
- `BID_DOCUMENT`：标书原文证据，包含 sourceTitle（如"报价函章节"）、pageNumber（物理页码或虚拟块页码）、quote（原文摘录上下文）。
- `TENDER_REQUIREMENT`：招标文件条款证据，摘录招标方原始要求。
- `KNOWLEDGE_BASE`：企业知识库参考证据，包含 entityType（QUALIFICATION/CASE/FINANCIAL/STAFF）及 referenceId，支持前端一键跳转。

## 4. 关键陷阱与架构演进教训

1. **多文件合并与 Loader 边界**（2026-08-20）：真实项目投标文件分多册（价格/商务/技术）。多文件在 Loader 边界合并为单个逻辑 `DocumentContent`，页码采用全局连续编号，核查引擎零感知文件分裂。
2. **Sidecar 分页缺失与虚拟分页兜底**（2026-08-22）：Sidecar 对长 docx/pdf 转换的 markdown 经常缺失物理分页符 `\f`，整篇文本退化为单页导致页数判断与证据定位失真。引入 `DocumentContent.virtualPages` 标题与字符预算混合分块机制，恢复页码有效性。
3. **废标词反向修饰与正向承诺语境识别**（2026-08-25）：标书中常包含“我方承诺不发生任何串通投标、行贿等违法违规行为”，若直接正则匹配“串通投标”会造成假阳性误报。引入 `isNegatedOrCommitment` 窗口探测，消除误判。
4. **附件免收场景自适应通过**（2026-09-02）：招标文件若明确“免收投标保证金”或为线下纸质标无需 CA 锁，保证金与 CA 检查项自适应判定为 PASS 并提示“免收/不涉及”，防止机械阻断。
5. **异步任务中间进度独立事务即时提交**（2026-08-30）：长任务采用独立事务更新进度，避免顶层事务包裹导致前端轮询卡死。
6. **LLM 结构化输出解析三层防御**（2026-08-30）：JSON 括号配平提取 + 中文状态归一化 + 空结论兜底，消灭网关异常与解析报错暴露给用户。
7. **大模型语义 RAG 全文无损透传与自然页码重排**（2026-08-30）：废弃前缀硬截断，超预算采用 Top-N 语义打分筛选与页码顺序重排，保证中后段关键资质与方案对大模型可见。
8. **网关不支持 response_format 的原子降级标记**（2026-08-30）：`AtomicBoolean` 标记网关兼容性，首遇 400 立即降级并持久化状态，杜绝重复 400 网络惩罚。
9. **前端轮询超时放宽至 10 分钟与进行中状态隔离**（2026-08-31）：前端轮询超时放宽至 600s，抽屉在核查进行中不渲染旧报告，杜绝状态漂移与旧报告残留。
10. **谈判/非招采购场景《报价函》与公文称谓支持及抗目录干扰**（2026-09-03）：基本信息类核查「投标函格式规范」原先硬编码仅检索《投标函》与字面「致：」，在竞争性谈判、竞争性磋商、询价等法定使用《报价函》/《响应函》的采购场景中发生假阴性误报；同时关键词命中首页目录（TOC）超链接时，因目录页缺少实质称谓和正文承诺导致误判。修复扩充识别词表为包含投标函/报价函/响应函等多文书类型的 `BID_LETTER_KEYWORDS`，选页逻辑优先匹配同时具备称谓与承诺要素的实质正文章节页（跳过目录链接），称谓判定扩充支持 `（采购人名称）：` 等标准公文抬头与占位符规范。
11. **投标保证金两阶段结构化契约抽取与凭证事实要素校验**（2026-09-05）：旧判定在招标文件采用前附表 Markdown 表格形式免除保证金时失效，且标书首页目录（TOC）命中宽泛词“汇款凭证”造成假通过。重构为两阶段契约抽取：阶段一在招标文件提取结构化 `depositRequirement`（是否免收/金额/形式/依据），配合 `DeterministicDepositExtractor`（支持投标担保与中文大写金额）做硬事实交叉检验，防止 LLM 幻觉免收放大；若明确免收则硬信号直出 PASS 并联动第 25 项豁免；若需缴纳，标书端接通 `LlmAdjudicator` 深度裁决与 `SemanticPageRetriever` 候选页检索，且确定性初筛强制要求具备凭证类型与交易事实要素（银行流水号/收款人/印章等），坚决剔除目录点线与无要素口头声明。
12. **投标函格式规范结构化契约抽取与要素反假**（2026-09-05）：旧逻辑在仅命中目录页点线索引时，因回退逻辑缺陷退回返回目录页并误判为 MANUAL（‘已找到投标函，仅称谓不规范’），且无法对抗未填写的占位符模板（如【采购人名称】、下划线留白）及承诺有效期不足的合规风险。重构为：招标端扩展 `BidLetterRequirement` 契约（函件类型/采购人全称/有效期天数/原文章节），建立 `DeterministicBidLetterExtractor` 双轨融合；标书端 `findBestBidLetterPage` 与 `isActualBidLetterPage` 正向事实要素检验（严格拦截目录点线 `......` 与未填写占位符），并比对采购人称谓一致性与有效期天数；引擎落实规范正文强凭证直通 PASS（不调 LLM，防抖动与降本），未检索到或存疑场景接通 `LlmAdjudicator` 深度裁决。
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
16. **前端轮询超时放宽与未完成长任务状态隔离**（2026-08-31）：前端长耗时 AI 任务（含多轮 LLM 语义裁决）轮询上限必须 >= 10 分钟（`MAX_POLL_TIMEOUT_MS = 600000`），禁止设为 1~2 分钟导致任务在后台正常完成时前端误报超时；同时抽屉在 `loadLatestReport` 中对 `INIT`/`PARSING`/`CHECKING` 等进行中状态直接进入轮询而不给 `report.value` 赋值，重新核查（`force=true`）显式置空 `report.value = null`，彻底杜绝超时/失败时 UI 状态错乱与旧报告残留。
17. **正向履约承诺误判与附件免除项假通过治理**（2026-09-02）：废标敏感词扫描必须结合前置窗口识别否定/承诺修饰语境（`isNegatedOrCommitment`），杜绝将正向合规承诺/违约条款确认误判为违规反标；附件核查（保证金/CA/退还账户）必须前置解析招标文件适用性（免收/纸质标），对免除场景准确判定 PASS 并标注“免收/不涉及”，凭证核查必须匹配核心特征组合词，防止单字包含导致虚假通过；资质匹配说明文案对齐三方比对基准，未提取到要求时友好降级提示人工核验。
18. **资质匹配"0 匹配"根因与三级穿透及结构化比对清单**（2026-09-03）：
    - **反向包含假阴性消除**：`KnowledgeBaseOnDemandLoader` 旧版使用 `q.getName().contains(kw)`，将短证书名反向包含大模型提取的长句导致恒为 false（资质库 0 匹配）。重构为纯核心 `QualificationMatchPolicy`，实现双向包含、标准代号提取（`ISO9001/ISO27001/CMMI/ITSS` 等）、专有资质等级对齐与 LCS 判定，并在资质库与人员库统一复用。
    - **招标文件三级优雅回退**：`TenderDocumentLoader` 支持一级查 `project_documents` 标准 `TENDER` 分类；二级按文件名启发式识别项目文档；三级向上穿透回退到 `project -> tender.source_document_file_url`，彻底打通仅在标讯带入文件的立项场景。
    - **结构化比对清单透传与弹窗呈现**：新增 `QualificationDetail` 领域模型并集成到 `QualityCheckItemResult` 与报表 DTO，前端在 `QualityCheckDetailDialog.vue` 中渲染结构化比对表格（招标资质要求 vs 我司持有证书 vs 标书位置 vs 核查说明），并 100% 接入全局设计系统令牌。
19. **招标资格与资质要求"漏识别"根因与双轨融合架构**（2026-09-03）：
    - **公文强规范与常识性必然存在**：依据《招标投标法》第 18 条，任何招标文件必然包含资格要求章节。系统提示"未在招标文件中提取到明确的硬性资格要求"纯属系统提取缺陷，不可向用户暴露虚假的不存在。
    - **`TenderRequirementsParser` 代码块围栏容错**：大模型常用 ````json` 围栏包裹或附加前后说明文字，旧版 `OBJECT_MAPPER.readTree` 直接抛 checked 异常降级为 `missingSentinel`。通过引入 `extractJsonObject` 括号配平扫描，成功剥离围栏与对话文字，杜绝格式噪声引发的降级。
    - **确定性规则提取器（`DeterministicQualificationExtractor`）**：建立纯核心正则章节锚定与标号切分策略，自动识别专用资质（增值电信业务许可证、等保三级、ISO、安全生产等）与通用资格（法人资格、失信限制），过滤占位符（如 `人员要求：/`）。
    - **质检流水线双轨融合**：`BidQualityCheckAsyncExecutor` 将规则提取的确定性资格与 LLM 提取的资格并集去重（`withMergedQualifications`）。只要规则提取出资质，即可解除 `missing` 标记，确保资质比对与结构化清单 100% 稳定生成。
    - **代码去重与常数收敛（Anti-Slop 治理）**：`TenderRequirementsParser` 统一复用同包 `LlmVerdictParser.extractJsonObject` 配平扫描，消除 35 行重复实现；`DeterministicQualificationExtractor` 移除 `LinkedHashSet.add` 前多余的 `contains` 检查，并将证书特征词抽为不可变常量 `CERTIFICATE_CORE_KEYWORDS`；前端弹窗移除空包装函数 `qualStatusBadge`。
20. **企业资质库底层表纠偏与国字头前缀剥离穿透**（2026-09-04）：
    - **数据源对齐**：将 `KnowledgeBaseOnDemandLoader` 对接真实业务资质库 `BusinessQualificationRepository`（读取 `business_qualifications` 物理表 62 条有效资质，状态为 `IN_STOCK` 且 `retired = false`），彻底消除读取历史单数 `qualifications` 废弃空表导致的“资质库未维护该证书”虚假误报；
    - **行政前缀剥离**：在 `QualificationMatchPolicy` 建立 `GOV_PREFIX_RE` 正则（剥离“中华人民共和国/全国/中国/国家”等行政修饰前缀），支持核心法定证书名穿透包含，并扩充电信业务与互联网信息服务专有领域词表；标书页码检索 cues 同步引入核心证书名，实现标书页码精准定位。
21. **LCS 停用词收紧与多候选打分优先排序及章节导语清洗**（2026-09-04）：
    - **LCS 通用停用词阻断**：扩充 `STOP_WORDS`（纳入“经营许可证”、“许可证”、“业务经营许可证”、“生产许可证”、“中华人民共和国”、“管理体系认证”等），`isStopword` 优先保护 `DOMAIN_TERMS` 核心专有名词，对其余通用词严格拦截，彻底阻断仅因共享通用证件后缀而引发的跨类别错配（如增值电信错配医疗器械）；
    - **打分第一主键原则（Relevance-First）**：`findBestMatchingCert` 建立 `calculateMatchScore` 多级相关度打分（包含1000+ > 标准代码500+ > 行业专有名词200+ > LCS100+），匹配打分作为绝对第一排序键，有效期仅作为相同打分梯队内的决胜依据（Tie-breaker），杜绝有效期长的弱相关证书反超核心资质；
    - **章节导语与噪点过滤**：在 `DeterministicQualificationExtractor` 与 `TenderRequirements` 中引入 `isIntroductoryHeading` 结构化判断（过滤以冒号结尾或含“以下证书/下列条件”的导语段落，如 `2.1 资质要求：供应商应具有以下证书`），并强化前导 `;` 等标点符号清洗，确保比对清单 100% 纯净。
22. **专用资质证书与通用主体资格边界厘清与分流治理（方案 A）**（2026-09-04）：
    - **法理与业务本质划界**：基于《招标投标法》第 18 条与《政府采购法》第 22 条，区分“通用主体资格条件”（独立法人、民事责任能力、财务会计制度、无违法失信等）与“专用行业资质许可”（增值电信、安全生产、等保三级、ISO 认证等）；企业通用主体资格由《营业执照》及承诺函证明，由 Rule 4（营业执照审查）等确定性规则闭环；企业资质库（`business_qualifications`）仅维护专业许可证，无营业执照实体。
    - **提取端分类归一（`DeterministicQualificationExtractor`）**：建立 `SPECIFIC_CERTIFICATE_KEYWORDS` 与 `GENERAL_CAPACITY_KEYWORDS` 双轨识别体系；`extract` 仅返回专用行业资质证书，彻底过滤法人及信用等通用条款；同时提供 `extractGeneralCapacities` 供通用规则复用；
    - **领域模型与匹配策略纵深防御**：`TenderRequirements.sanitizeQualifications` 与 `QualificationMatchPolicy.buildDetails` 双重校验 `isSpecificCertificate`，即使大模型提取误包含法人条款，在进入领域模型与比对清单时即被净化过滤；从根源上杜绝了“用增值电信业务许可证证明企业是独立法人”的荒谬错配，以及通用条款因资质库未维护而误报废标风险的问题。
23. **投标函格式规范(BID_LETTER_FORMAT)两阶段结构化契约与要素反假重构**（2026-09-05）：
    - **两阶段结构化契约**：在 `TenderRequirements` 与 `BidQualityCheckItem` 中建立投标函格式专属检查契约，覆盖格式规范、金额、工期与质量标准的一致性；
    - **目录行与虚假占位模板拦截**：在 `BidLetterCheckPolicy` 建立点线目录扫描与虚假模板拦截，杜绝仅有目录或占位声明的虚假通过；
    - **确定性与 LLM 协同**：在非免除且缺乏确定性强凭证事实时，由 `BidDocumentQualityEngine` 调用 LLM 裁决并保留完整比对事实链。
24. **质检领域纯核心反 AI 异味（Anti-Slop）治理与代码消歧微重构**（2026-09-05）：
    - **逻辑去重与抽取**：`DeterministicBidLetterExtractor.parseDigitsSafely` 提为包级方法，`BidLetterCheckPolicy` 直接复用，彻底删除策略类内部 15 行重复解析数字逻辑；
    - **冗余子串条件折叠**：消除带书名号及后缀的冗余条件（如 `contains("《谈判响应函》") || contains("谈判响应函")` 统一折叠为 `contains("谈判响应函")`），简化规则维护复杂度；
    - **消除空 if 分支占位**：消除 `BidDocumentQualityEngine` 中两个空 `if` 占位块（`if (hasStrong...) { } else if (...)`），合并为 `if (!hasStrong... && llm != null)`，避免无意图分支增加圈复杂度；
    - **死代码清理与行预算防爆**：删除 `TenderRequirements.BidLetterRequirement.standard` 零引用的死工厂方法，紧凑化 `with` 方法行格式，使 `TenderRequirements.java` 安全控制在 277 行（远离 300 行硬门禁）；
    - **正则静态预编译**：`BidLetterCheckPolicy` 预编译 `MULTI_DOT_PATTERN` 与 `CATALOG_DOT_LINE_PATTERN`，避免高频逐行判定时反复编译正则，保障纯核心高吞吐。
25. **法定代表人授权委托书(AUTHORIZATION_LETTER)两阶段结构化契约与要素反假重构**（2026-09-05）：
    - **两阶段结构化契约**：在 `TenderRequirements` 接入 `AuthorizationRequirement` 契约（是否必须提供、法定代表人亲自参加是否免除、指定模板标题、要求说明），并建立纯核心 `DeterministicAuthorizationExtractor` 提取豁免条款与模板要求；
    - **目录点线拦截与未填模板占位符反假**：标书端 `AuthorizationLetterCheckPolicy` 严格排除目录点线页（`MULTI_DOT_PATTERN`、`CATALOG_DOT_LINE_PATTERN`，`isCatalogOrOutlinePage`），并拦截未填写的占位符模板（【委托代理人姓名】、下划线留白等）；
    - **双角色要素正向检验与法人亲签豁免**：核查委托人（法定代表人）、受托人（委托代理人）、身份证号、授权权限与期限等事实要素。若招标文件明确法人亲自参加免除授权书，标书附法定代表人身份证明与法人亲笔签署时直通 PASS；
    - **强事实直通与存疑裁决接通**：强凭证齐全直通 PASS（LLM 0 调用防抖动），存疑或仅有占位符时接通 `LlmAdjudicator` 准则 5 深度裁决并调用 `SemanticPageRetriever` 候选页定位。
    - **返工修复（2026-09-06）**：目录判定复用投标函整体词逻辑，消除单字目加录误判。放行收紧为强授权要素或身份证事实，签署投标二词不再单独算数。删除零引用死工厂。自然人免除变体与掩码身份证口径补齐。
26. **营业执照有效性(BUSINESS_LICENSE)两阶段结构化契约与要素反假重构**（2026-09-05）：
    - **两阶段结构化契约**：在 `TenderRequirements` 接入 `BusinessLicenseRequirement` 契约（是否必须提供、允许的主体证照类型清单、原件/复印件/公章要求、要求说明），并建立纯核心 `DeterministicBusinessLicenseExtractor` 提取证照类型与公章要求；
    - **目录点线拦截与未填模板占位符反假**：标书端 `BusinessLicenseCheckPolicy` 物理排除目录点线页（`MULTI_DOT_PATTERN`、`CATALOG_DOT_LINE_PATTERN`，`isCatalogOrOutlinePage`），并拦截未填写的占位符模板（【此处粘贴营业执照】、下划线留白等）；
    - **GB 32100-2015 18位标准代码与登记要素校验**：严格校验 18 位统一社会信用代码（剔除易混淆字母），综合核验法定代表人、经营范围、营业期限、登记机关/公章等登记事实要素；正向兼容事业单位法人证书等替代证照；
    - **强事实直通与存疑裁决接通**：真实 18 位代码与登记事实齐全直通 PASS（LLM 0 次调用防抖动），存疑或仅命中目录/占位符时接通 `LlmAdjudicator` 准则 6 深度裁决并调用 `SemanticPageRetriever` 候选页定位。
    - **Follow-up 返工（2026-09-06，Code Review !2557）**：`check` 落实契约消费（`required=false` 直接 PASS 且引擎跳过 LLM；`allowCertificates` 非空时过滤直通页）；PASS 新增同页证照关键词必要条件（堵业绩合同页仅凭对方 USCC 直通）；占位符补 `此处粘贴` 变体与 `91…X` 空白容忍；目录散点判据遇 USCC 豁免；登记要素去掉裸 `长期`/`地址` 弱信号；纯数字 18 位串仍可命中 USCC 正则，防线为三重 AND（已同步修正 lessons §231 表述）。
27. **法定代表人及代理人身份证明(ID_AUTH_LETTER)两阶段结构化契约与要素反假重构**（2026-09-05）：
    - **两阶段结构化契约**：在 `TenderRequirements` 接入 `IdAuthLetterRequirement` 契约（是否必须提供、是否要求正反面、是否要求社保证明、原件/复印件/公章要求、要求说明），并建立纯核心 `DeterministicIdAuthLetterExtractor` 正则提取身份证正反面与社保证明要求；
    - **目录点线拦截与未填模板占位符反假**：标书端 `IdAuthLetterCheckPolicy` 物理排除目录点线页（`MULTI_DOT_PATTERN`、`CATALOG_DOT_LINE_PATTERN`，`isCatalogOrOutlinePage`），并拦截未填写的占位符模板（【此处粘贴委托代理人身份证】、下划线留白等）；
    - **GB 11643-1999 18位标准身份证号与人像/国徽面要素核验**：严格校验 18 位公民身份号码并进行号码脱敏；综合核验有效期限、签发机关、人像面/国徽面等真实版面要素；招标明确要求社保时联动核验社保证明，缺失时降级人工复核；
    - **强事实直通与存疑裁决接通**：真实 18 位身份证号与身份要素齐全直通 PASS（LLM 0 次调用防抖动），存疑或仅命中目录/未填模板时接通 `LlmAdjudicator` 准则 7 深度裁决并调用 `SemanticPageRetriever` 候选页定位。
28. **财务状况证明与审计报告(FINANCIAL_STATEMENT)两阶段结构化契约与要素反假重构**（2026-09-06，!2561 follow-up 收紧）：
    - **两阶段结构化契约与顶层 Record 解耦**：在 `TenderRequirements` 接入 `FinancialRequirement` 契约（是否必须提供、是否要求审计报告、所需年份列表、所需报表清单、是否要求事务所印章及说明），同时将累积的内部类 `DepositRequirement`、`BidLetterRequirement` 连同 `FinancialRequirement` 彻底解耦为同包顶层 Record，守护 300 行红线；建立纯核心 `DeterministicFinancialExtractor` 正则提取年份、三表与审计印章要求；
    - **目录点线拦截与未填模板占位符反假**：标书端 `FinancialStatementCheckPolicy` 物理排除目录点线页（`isCatalogOrOutlinePage`）；仅当「有占位且无任何有效报表/审计页」时以 `[UNFILLED_PLACEHOLDER]` 硬 FAIL；占位页与真报表并存降级 MANUAL，不因残留模板一票否决；
    - **财务三表与年份覆盖**：报表识别为标题 ∧ 表内要素；绝对年份必须全部命中，抽不到年份不得真空通过，相对年份（近三年）不在规则层直通 PASS；
    - **资格审查第一阶段只 FAIL 不 PASS**：财务三表齐全或招标免除不得结束 `QUALIFICATION_REVIEW`（spec 047 本项是企业信誉与类似业绩门槛）。造假硬信号上浮后，其余场景交 LLM 准则 8（必须覆盖业绩/信誉，不得只查财务报表）。
29. **报价一致性(PRICE_CONSISTENCY)与开标一览表(BID_OPENING_TABLE)两阶段结构化契约与反假重构**（2026-09-06）：
    - **两阶段结构化契约**：在 `TenderRequirements` 接入 `PriceRequirement` 独立顶层 Record 契约（最高限价金额、限价原文、报价方式、明细表/一览表要求、要求说明），并建立纯核心 `DeterministicPriceExtractor` 正则提取最高投标限价/控制价/采购预算（支持万元折算、纯数字及中文大写），与 LLM 提取双轨融合；
    - **开标一览表反假与总价提取**：标书端 `BidOpeningTableCheckPolicy` 物理排除目录点线页（`isCatalogOrOutlinePage`），并拦截未替换模板占位符（【此处粘贴开标一览表】等直接 FAIL 拦截），严格核验序号、名称、规格型号、单价、合价等表头实质结构，提取表格总报价供跨表勾稽；
    - **报价一致性与限价核验策略**：`PriceConsistencyCheckPolicy` 支持公文带括号格式，大写折算数字与小写阿拉伯数字核验；**最高限价超限硬核验**（高于最高限价立即判定法定废标 FAIL）；**表函总价一致性核验**（开标一览表总价与投标函总价冲突立即 FAIL）；逐页未命中时自动跨页全文回退；
    - **强事实直通与存疑双层裁决**：规范标书大写小写一致且未超限价强凭证直通 PASS（LLM 0 次调用防抖动）；硬性超限价、表函冲突或占位符未填直接 FAIL 阻断，禁止大模型覆盖；存疑或未提取场景接通大模型准则 9（开标一览表反假）与准则 10（限价超限与报价一致性）深度裁决并调用 `SemanticPageRetriever` 候选页检索。
30. **电子标 CA 证书(CA_CERTIFICATE)两阶段结构化契约与反假重构暨阶段 1 全面大收官**（2026-09-06）：
    - **两阶段结构化契约**：在 `TenderRequirements` 接入 `CaCertificateRequirement` 独立顶层 Record 契约（是否必须提供、交易平台名称、是否要求证书复印件/有效性证明、是否要求电子签章、要求说明），并建立纯核心 `DeterministicCaCertificateExtractor` 正则识别纸质标/线下标等免收信号，提取政采云/公共资源交易平台等指定平台，支持 DocumentContent 与 String 双重重载；
    - **目录点线拦截与未填模板占位符反假**：标书端 `CaCertificateCheckPolicy` 物理排除目录点线页（`isCatalogOrOutlinePage`），并拦截未替换模板占位符（【此处粘贴CA数字证书】等直接 FAIL 反假拦截）；
    - **权威机构与证书序列号/有效期事实核验**：严格核验 CFCA/沃通/契约锁/北京CA/天威诚信等权威认证机构，以及证书序列号、国密/公钥算法、有效期至等真实证书事实特征；
    - **强事实直通与双层裁决闭环**：招标文件明确免除或纸质标时直接直出 PASS（0 次 LLM 耗时抗抖动）；标书附真实完备证书时强凭证直通 PASS（0 次 LLM 耗时）；未附带凭证或存疑场景流转至大模型准则 11 深度裁决并调用 `SemanticPageRetriever` 候选页检索；
    - **阶段 1（6 项文书附件凭证类）100% 全面大收官**：至此，投标函、授权书、营业执照、身份证件、开标一览表、电子标 CA 证书全部完成两阶段双轨重构，全面摆脱脆弱纯正则与目录占位假通过。
31. **前附表关键条款(FRONT_TABLE_RESPONSE)与偏离表响应(RESPONSIVENESS_REVIEW)两阶段契约与反假重构（阶段 2 启动）**（2026-09-06）：
    - **前附表结构化契约与提取**：在 `TenderRequirements` 接入 `FrontTableRequirement` 独立顶层 Record 契约（工期、质保期、投标文件有效期、交货/服务地点、付款方式等条款定义与说明），并建立纯核心 `DeterministicFrontTableExtractor` 正则提取核心事实，双轨融合；
    - **全量确定性融合器解耦（`DeterministicRequirementsFuser`）**：将分散在 `BidQualityCheckAsyncExecutor` 的 7 大提取器及合并逻辑解耦收敛至纯核心融合器，消除执行器 50+ 行胶水代码，行数从 295 行降至 243 行；
    - **目录点线拦截与未填模板占位符反假**：标书端 `FrontTableResponsePolicy` 物理剔除目录点线页（`isCatalogOrOutlinePage`），正则拦截下划线未填留白（如 `工期：______日历天`）与括号占位符（如 `【请填写】`），直接 FAIL 反假拦截；规范标书具备实质条款与强响应词直通 PASS；
    - **偏离表响应反假与负偏离硬拦截**：标书端 `ResponsivenessReviewPolicy` 检验章节与表头结构；检测到负偏离关键词（“有偏离/负偏离/部分满足/无法满足”）直接判定 FAIL 阻断废标风险；全项“无偏离/完全响应”强事实直通 PASS；
    - **准则解耦与解析器拆分**：将准则 1~13 提取为 `AdjudicationGuidelines` 纯值接口，满足 FP-Java 纪律；拆分 `RequirementSectionParser` 守护 `TenderRequirementsParser` 108 行安全水位；
    - **双层裁决接通**：未提取到前附表要求或偏离表存疑场景流转至大模型准则 12（前附表）与准则 13（偏离表）深度裁决并调用 `SemanticPageRetriever` 候选页检索。
32. **一票否决(QUALIFICATION_VETO)与资格性评审(QUALIFICATION_REVIEW)两阶段深度穿透与反假重构（阶段 2 深入推进）**（2026-09-06）：
    - **废标专章确定性提取（`DeterministicVetoExtractor`）与融合**：建立纯核心提取器，支持从招标文件中定位否决投标/废标条款专章与行内具有否决后果的条款，提取核心场景关键词（分包、转包、串通投标、弄虚作假、最高限价、签章等），并在 `DeterministicRequirementsFuser` 统一融合；
    - **一票否决反假与合规承诺保护策略（`QualificationVetoPolicy`）**：物理剔除目录点线页（`isCatalogOrOutlinePage`）；拦截资格声明或否决条款响应中的未填占位符（`_{3,}`、`【请填写】` 直接 FAIL）；精准区分正向合规承诺（“承诺不转包”等放行）与实质性违规（“拟将主体工程分包”等硬阻断 FAIL）；专用资质正文缺失核验；无硬信号移交 LLM lane；
    - **资格评审反假与反短路策略（`QualificationReviewPolicy`）**：继承财务审计报告硬造假拦截；覆盖依法缴纳税收、社保清单、信用中国等资格证明页占位符与空白留白反假拦截；严格执行准则 8「反短路」硬约束，无硬造假信号一律返回 empty 移交 LLM lane 穿透核验信誉、业绩、税收、社保等全部条款；
    - **外壳减重与大模型准则升级**：`SemanticRulesLane` 行数从 168 降至 106 行；`AdjudicationGuidelines` 增加准则 14（一票否决合规承诺保护与违规硬阻断）并强化准则 8；`EvidenceAssembler` 增强废标条款与资格材料证据检索。
33. **资质要求智能匹配(QUALIFICATION_MATCH)多维升级与穿透核验（阶段 2 持续深化）**（2026-09-06）：
    - **独立资质等级层级判定策略（`QualificationGradePolicy`，157 行）**：建立四大主流资质等级层级体系（工程施工承包特级>一级>二级>三级、设计/咨询综合甲级>甲级>乙级>丙级、等保四级>三级>二级、CMMI5>4>3）；支持等级向下兼容覆盖（高等级满足低等级要求，低等级明确判定等级不足）；
    - **跨专业领域词冲突防护与 LCS 停用词优化**：在 `QualificationMatchPolicy`（290 行）扩充承包类别词（“施工总承包”、“专业承包”、“承包一级”等纳入 `STOP_WORDS`），并引入 `hasConflictingDomainTerms` 机制：当招标要求与证书分属不同专业（如机电安装 vs 电力工程）时严格阻断 LCS 错配；
    - **标书真实页码穿透与目录过滤**：在 `locateInBid` 中先经 `BidLetterCheckPolicy.isCatalogOrOutlinePage` 过滤点线目录页，确保页码穿透至真实证书正文页，杜绝目录页欺骗；
    - **第一阶段确定性反假核验（`checkFirstStage`）**：在标书资质证书章节扫描未填模板留白（`【此处粘贴...】`、`_{3,}`），直接硬阻断输出 FAIL；正常内容平滑移交 LLM lane；`SemanticRulesLane` 正式接通；
    - **准则 15 与证据链升级**：`AdjudicationGuidelines` 新增《准则 15：资质匹配核验准则》；`EvidenceAssembler` 增强招标具体资质条款与标书证明正文的真实定位。
34. **评分项响应(SCORING_RESPONSE)与补遗澄清响应(CLARIFICATION_RESPONSE)深度穿透升级（阶段 2 胜利收官）**（2026-09-06）：
    - **独立评分响应策略（`ScoringResponsePolicy`，128 行）**：构建纯核心核验策略，定位评分标准对应表、技术方案、实施组织设计、管理机构等核心章节；前置执行目录点线过滤（`isCatalogOrOutlinePage`）与占位留白反假（`_{3,}`、`【请填写...】` 直接 FAIL 拦截）；无硬伤平滑移交 LLM 语义裁决深层打分；
    - **独立补遗澄清响应策略（`ClarificationResponsePolicy`，161 行）**：识别确认函（`澄清文件确认函`、`答疑纪要确认函`、`补遗书确认函`）与招标澄清发布状态；目录点线与未填模板硬拦截 FAIL；真实签署确认函直接直通 PASS；若招标文件未发布澄清答疑，自动免除并直通 PASS；若招标有澄清但标书无独立确认函，移交 LLM 语义核验技术/商务是否吸收答疑参数；
    - **规则 Lane 与证据链集成**：`SemanticRulesLane` 接通两项的第一阶段确定性核验；`EvidenceAssembler` 增强招标评分办法与澄清要求、标书对应正文真实页码定位，过滤目录页干扰；
    - **准则库修复与准则 16/17 追加（`AdjudicationGuidelines`，42 行）**：移除重复方法定义，追加准则 16（评分细则逐项对应与支撑材料）与准则 17（补遗/澄清文件响应）；
    - **跟进修复（2026-09-06，Lesson 242）**：占位正则增补 `此处|粘贴`（`【此处填写/粘贴…】` 不再逃逸）；澄清编号留白分支允许前缀（`【确认收到第__号澄清】` 可拦截）；确认函直通 PASS 仅认强签署信号（盖章/签署/公章/法定代表人/授权代表/电子签章，删裸投标人/日期与 `无异议`）；展示证据经 `locateSkippingCatalog` 穿透正文页；
    - **阶段 2 宣告完成**：7 项深度语义项（`QUALIFICATION_MATCH`、`QUALIFICATION_VETO`、`FRONT_TABLE_RESPONSE`、`RESPONSIVENESS_REVIEW`、`QUALIFICATION_REVIEW`、`SCORING_RESPONSE`、`CLARIFICATION_RESPONSE`）全部完成两阶段穿透与反假重构。
35. **报价一致性(PRICE_CONSISTENCY)分项算术与商务条款一致性(TERM_CONSISTENCY)深度重构（阶段 3 启动）**（2026-09-06）：
    - **商务条款结构化契约与确定性提取**：定义顶层 `BusinessTermsRequirement` 纯核心 Record 契约（工期上限日历天 `maxScheduleDays`、质保期下限月数 `minWarrantyMonths`、投标有效期天数 `bidValidityDays`、付款方式、履约担保比例等）；建立纯核心 `DeterministicTermsExtractor` 正则抽取招标文件商务要求，并接入 `DeterministicRequirementsFuser` 统一融合；
    - **分项报价计算与多表交叉校验（`ItemizedPriceCalculator`）**：支持逐行抽取分项明细（序号、名称、数量、单价、合价），进行行级算术核验（$数量 \times 单价 = 合价$）与表内分项合价累加总和核对（$\sum 合价 = 表内合计$），拦截下划线与【待报价】留白占位；
    - **报价一致性策略联动升级（`PriceConsistencyCheckPolicy`）**：在既有大小写一致性与最高限价超限硬核验基础上，联动分项报价表未填占位拦截、行内乘积错误拦截、以及分项总和与开标一览表/投标函总报价跨表一致性勾稽核验；
    - **商务条款一致性策略独立构建（`TermConsistencyCheckPolicy`）**：物理过滤目录点线页（`isCatalogOrOutlinePage`）；拦截工期与质保未填占位留白（直接 FAIL）；严格数值比对：标书工期承诺大于招标文件上限判定 FAIL、质保期短于招标下限判定 FAIL、投标有效期短于招标要求判定 FAIL；规范承诺且满足上限强事实直通 PASS；
    - **FP-Java 纯核心无异常控制流严格落地**：提取器与计算器全面废除 `try-catch` 解析异常控制流，采用 `parseSafeInt` 与 `parseSafeDecimal` 纯函数无异常校验解析，100% 满足 `FPJavaArchitectureTest.pure_core_must_not_use_exceptions_for_business_flow`；
    - **准则 18 落地**：`AdjudicationGuidelines` 追加《准则 18：报价分项与商务条款一致性核验准则》。
36. **签章完备性(SEAL_INTEGRITY)与表格填写完整性(TABLE_CELL_INTEGRITY)深度重构与反假（阶段 3 推进）**（2026-09-06）：
    - **表格填写完整性助手（`TableCellIntegrityCalculator`，192 行）**：构建纯领域 Markdown 表格逐行解析器，精准识别空单元格、无效斜杠/横杠留白（`/`、`\`、`--`、`——`）及模板未填占位符；创新引入语义列豁免机制（对“偏离说明”、“偏离情况”、“备注”、“说明”等列放行正常无偏离表述）；结构化输出页码、行号、列名与缺陷原因；
    - **签章完备性独立策略（`SealIntegrityCheckPolicy`，168 行）**：全面识别法定代表人印章/签名、企业公章、防伪验签标记（国密SM2/CA数字证书）与骑缝章；对未替换占位模板（`法定代表人：______`、`【此处盖公章】` 等）实施硬核反假拦截 FAIL；具备防伪验签或双要素齐全直通 PASS；
    - **业务层三路联动**：`BasicInfoCheckPolicy.checkSignatureSeal` 委托至 `SealIntegrityCheckPolicy`；`FrontTableResponsePolicy` 接入 `TableCellIntegrityCalculator` 实现表格填写缺陷行级拦截；`SubItemAndAttachmentRules.checkCommitmentSeal` 接入真实签章反假与完备性校验；
    - **准则 19 落地**：`AdjudicationGuidelines` 扩充《准则 19：签章完备性与表格填写完整性核验准则》，保持 LLM fallback 规则双轨一致。
    - **审查跟进收紧（2026-09-06）**：纯模板落款说明文字（如「法定代表人（签字）」「（盖章）」未填姓名）不再计入签章证据直通 PASS，降级 MANUAL_REVIEW 并在提示语中说明文本层边界；承诺书反假复用 `SealIntegrityCheckPolicy` 共享占位/强证据判定（消除两套关键词不一致）；前附表表格缺陷 conclusion 携带违规总数并逐条输出证据；无分隔行 Markdown 表格首行不再被误判为表头漏检。
37. **财务三表平衡勾稽(FINANCIAL_STATEMENT_ACCURACY)与进度计划逻辑合理性(TIMELINE_REASONABLENESS)深度重构（阶段 3 攻坚）**（2026-09-06）：
    - **财务三表平衡勾稽助手（`FinancialStatementAccuracyCalculator`，243 行）**：构建纯核心无异常会计恒等式校验器，覆盖资产负债平衡式（资产总计 = 负债合计 + 所有者权益合计）、利润平衡式（营业利润 $\le$ 营业收入 - 营业成本）、净利润合理性、现金流量期末余额非负及大额净流出预警；引入双重自适应容差（绝对差 $\le 1.0$ 元或相对差 $\le 1\%$），精准隔离四舍五入尾差与严重失衡；
    - **进度计划逻辑合理性独立策略（`TimelineReasonablenessPolicy`，198 行）**：构建纯核心无异常进度合理性判定，识别开竣工倒挂（直接 FAIL）、自述工期超出招标最大工期限制（直接 FAIL）、横道图/甘特图模板未填占位符（直接 FAIL 反假）；采用无异常纯整数公历日历算法计算纪元跨度天数，对比自述工期（差额超 10 天触发人工复核预警）；
    - **业务层穿透与准则 20 升级**：`FinancialStatementCheckPolicy` 接入 `FinancialStatementAccuracyCalculator` 实现三表齐全时的穿透勾稽；`BasicInfoCheckPolicy.checkScheduleResponse` 串联商务条款与进度计划逻辑；`AdjudicationGuidelines` 追加《准则 20：财务三表平衡勾稽与进度计划合理性核验准则》；
    - **FP-Java 零异常纯核心保证**：严格遵守无 `try-catch` 纯算术逻辑，100% 通过 `FPJavaArchitectureTest` 与单文件行预算守卫。

39. **质检结果长详情持久化扩容(MEDIUMTEXT)与前端失败态兜底（2026-09-06）**：
    - **数据库与实体层扩容**：`compliance_check_results.check_details` 字段由 `TEXT`（最大 64KB）正式迁移至 `MEDIUMTEXT`（最大 16MB，Flyway `V1212` + 回滚 `U1212`），实体类 `ComplianceCheckResult.java` 同步将 `@Column(columnDefinition = "TEXT")` 修改为 `MEDIUMTEXT`，彻底解决大标书在 25 项深度比对后 JSON 详情超长导致的 `Data truncation: Data too long for column 'check_details'` 写入崩溃；
    - **前端失败态持久化与交互兜底**：`useQualityCheckDrawer.js` 新增并维护 `errorMessage` 响应式变量，在轮询遇到 `FAILED`、超时或报告拉取失败时保留错误，杜绝 3 秒全局 Toast 一闪而过；`QualityCheckDrawer.vue` 新增基于 `el-result` 的持久化错误卡片（`v-else-if="errorMessage"`），内嵌可滚动错误详情代码框，并提供「重新核查（跳过缓存）」和「尝试恢复（检查缓存）」明确操作入口，杜绝失败态回退为空白态导致无法重试的问题。

## 5. 前端契约（2026-08-25 UI 改造：紧凑行 + 居中详情弹窗；2026-09-06 失败卡片持久化）

drawer 结构：
- **运行中**：异步轮询进度条（`polling`）
- **失败态**：持久化错误状态卡片（`errorMessage`），展示错误详情代码框、重新核查（跳过缓存）与尝试恢复按钮，杜绝仅靠 3 秒 Toast 导致用户误判为超时的假象；
- **成功态**：汇总平铺卡（左上投标文件/核查时间，右上「重新核查」二次确认 + 「AI 评分」）→ 4 统计卡（点击筛选状态，「待确认项」）→ 三分类定位条（基本信息/分项合规/附件清单，点击展开分组并平滑滚动，**不**改状态筛选）→ 分组紧凑行；
- **空白态**：尚未执行标书质量核查时展示空状态与立即核查按钮。

- **紧凑行**（`QualityCheckItemCard`）：状态图标 + 检查项 + 结论（2 行省略，已剥离 reasonCode 前缀，前置"检查结论："）+ 右侧状态标签；整行可点击，不再平铺证据。
- **详情弹窗**（`QualityCheckDetailDialog`，560px 居中）字段对照表：检查状态/检查项说明（优先 `description` 即 PRD 2.3 检查内容，旧快照缺失时回退名称）/AI 检查结论恒显；问题定位（BID 证据页码去重「第 12 / 56 页」）/原文摘录（BID quote，不高亮）/联动数据来源（KNOWLEDGE_BASE 证据→四库链接，点击 `router.push /knowledge/*`）仅非 PASS 显示；建议操作按状态固定文案。
- **SCORING_RESPONSE 项**：客观项得分合计非空时结论前置「客观项得分：XX分 ↗」链接（复用 `scoreParseApi.getItems/getResults` + `normalizeScoreItem`/`mapScoreResults`；静默降级，未打分不展示）；点击经事件链 QualityCheckDrawer → DraftingStage → ProjectDetailMainColumn 透传 `{stage:2, autoScore:true}` 打开评分抽屉并自动打分。
- **展示纯核心**：`composables/qualityCheckDetail.js` 单一数据源（`STATUS_META`/`NOT_APPLICABLE_STATUS_META`/`isNotApplicableItem`/`stripReasonCode`/`extractBidPages`/`extractBidQuote`/`extractKbLinks`/`sumObjectiveActualScore`），紧凑行与弹窗共用，支持在 status 为 PASS 且结论标明免收/不涉及时自适应渲染专属的【不涉及】状态标签。
- **保留纪律**：trigger 同步 4xx（无投标文件）提示后端消息且不进入轮询；429 走全局友好提示（catch 体内禁用 ElMessage.error）。
- **展示文案单一数据源**（2026-08-30）：25 项检查项名称与检查项说明（检查内容）以 `BidQualityCheckItem` 枚举为唯一源（对齐 PRD 2.3，`BidQualityCheckItemDefinitionTest` 防漂移）；报告读取侧 `toReportDTO` 按 itemCode 回填，历史快照展示口径自动对齐。

40. **政采与非招标“响应担保”同义词盲区与【不涉及】结论前缀规范**（2026-09-06）：竞争性谈判、询价等采购模式法定使用“响应担保/响应保证金”（如“不要求递交响应担保”）；`DeterministicDepositExtractor` 遗漏该词表导致确定性免收识别失效、第 24 项退款信息表因缺乏联动误报待确认。解法：词表扩充“响应担保/不要求递交”，全链路免收项统一输出 `【不涉及】` 结论前缀，消解用户对未附回单为何通过的困惑。
41. **质检引擎保留大模型深度条款证据链与前端自适应【不涉及】标签**（2026-09-06）：大模型对免收项的深度裁决具有不可替代的证据价值（精确提取招标文件第3.4款与标书文件五“本项目不适用”对应关系）。严禁用生硬确定性模板覆盖大模型输出；后端保留完整证据并冠以统一 `【不涉及】` 前缀，前端通过 `isNotApplicableItem` 自适应将标签渲染为 `不涉及`（info tag），彻底消解“未附回单却显示通过”的用户误解。
42. **标书大图片证据保留与证照凭证放行机制**（2026-09-10）：
    - **Sidecar 图片占位符保留语义**：`document-converter-sidecar/app.py` 中 `_suppress_docx_image_inlining` 改造为返回带 `alt="已附图片"` 和 `src="embedded-image"` 的占位元素，解决 `markdownify` 将空 `<img src="" />` 剔除导致表格单元格完全留白（`| | |`）的问题。
    - **确定性核查策略支持图片凭证**：`IdAuthLetterCheckPolicy`、`BusinessLicenseCheckPolicy`、`AuthorizationLetterCheckPolicy` 引入 `IMAGE_EVIDENCE_PATTERN`，识别到表格或正文中粘贴的证照图片或 `已附图片` 标记时判定 PASS，消除无外部公安库对比下硬性要求纯文本 18 位号码的局限。
    - **通用示范称谓与后缀放行**：`BidLetterCheckPolicy` 与 `AdjudicationGuidelines` 准则 4 放行“致采购人或采购代理机构：”等标准招标文件模板抬头及说明性项目名称后缀（如“（项目名称）”），避免误判为占位模板。
    - **百度 PaddleOCR-VL 客户端集成**：引入 `BaiduPaddleVlClient`，支持将文档及图片提交至百度 PaddleOCR-VL 进行版面分析与结构化 Markdown 提取，支持优雅降级。
43. **资质比对明细与总项状态一致性校准（`QualificationDetailReconciler`，2026-09-11）**：
    - **双轨脱节根治与强制状态校准**：大模型综合裁决输出整体结论，资质比对策略（`QualificationMatchPolicy`）在后处理中独立生成 `qualDetails` 明细清单。建立纯核心策略 `QualificationDetailReconciler`，在结果持久化前执行确定性校准门禁：若明细存在任一 `EXPIRED`（过期）或 `UNMATCHED`（未匹配），总项强制降级为 `FAIL`，任务整体合规状态同步置为 `NON_COMPLIANT` 并累加风险分；若存在 `ATTENTION`（需关注/标书有材料但资质库缺失）且无硬性失败，总项降级为 `MANUAL_REVIEW`；杜绝“明细未匹配但结论显示通过”的逻辑割裂；
    - **长句说明剥离与标书检索候选词展开**：针对招标专用资质要求常附带长句括号说明（如“银行基本账户开户许可证（取消开户许可证的须提供基本存款账户银行备案资料...）”）导致 60 字精确匹配致盲问题，`QualificationDetailReconciler.buildBidSearchCues` 自动剥离括号注释，提取核心词，并对银行基本账户开户类资质自动展开“基本存款账户”、“开户许可证”、“开户证明”等候选词列表，确保标书正文包含有效开户凭证时能准确定位并置为 `ATTENTION` 待复核，消除漏检误报。
44. **资质提取物理结构切块与评分细则/失信分流及表格结构化防误杀（2026-09-13）**：
    - **物理版式边界强截断**：`DeterministicQualificationExtractor.BULLET_PATTERN` 正则引入 `\n\s*#{1,6}\s+|\n\s*\|`，任何 Markdown 标题或表格换行均作为物理块断点，彻底杜绝全文扫描时前附表履约提示跨章节粘连整套商务/技术评分细则（长达 5229 字符）的缺陷；
    - **长度上限与评分/模板特征过滤**：规定 `MAX_QUALIFICATION_LENGTH = 100`（法定资质名称硬上限），并增加 `SCORING_AND_TEMPLATE_EXCLUDE_KEYWORDS` 排除评分标准、评审细则、打分、应答格式与编制说明等；
    - **概念清晰分流**：将失信惩戒关键词（`PENALTY_AND_DISCREDIT_KEYWORDS` 如吊销许可证、停产停业等）从专用资质 `isSpecificCertificate` 中剔除，但保留在 `isGeneralCapacity` 通用主体资格中，保证失信被执行人等条款正常核验；
    - **结构化标记过滤替代词汇黑名单**：杜绝将表格表头拆解词（如“认证机构”、“有效期限”、“佐证材料”）作为全局黑名单导致的误杀合法资质缺陷；采用结构化表格标记过滤（`clean.contains("|")` 强阻断表格跨列行），并补充正向防误杀单测；
    - **一票否决核验层二次守卫与 Nullability 防护**：`QualificationVetoPolicy.checkMandatoryQualifications` 增加 `qualification == null || qualification.isBlank()` 严防 NPE，且跳过超长文本（>100字）与非专用资质项的 contains 阻断，杜绝不可控外部长文本导致一票否决误判。
45. **资格条件一票否决检查项结论精简与确定性闭环规范（2026-09-13）**：
    - **确定性自闭环消除 LLM 冗长输出**：旧逻辑在标书满足全部硬性资质要求时直接返回 `Optional.empty()`，移交第二阶段 LLM lane 裁决，导致大模型生成数百字冗长废话；重构后 `QualificationVetoPolicy.checkMandatoryQualifications` 形成确定性闭环：全部资质满足时直接判定 `PASS` 并格式化输出 `招标文件要求的一票否决资格项均已满足：【...】。`，并关联招标与标书正文证据；
    - **不通过分支聚合说明**：旧逻辑在遇到首个未满足项时直接 break 单项报错；重构后对清洗后的专用资质集合进行全量比对，汇总 `validQualifications` 与 `unsatisfied`，不通过时精准输出 `招标文件要求了一票否决资格项【...】，标书中未满足【...】。`；
46. **电子标 CA 证书质检项联动 CA 管理台账与知识库按需检索依赖治理（2026-09-14）**：
    - **CA 证书台账深度联动与一键闭环跳转**：
      - 核心痛点：标书质检 25 项检查中的“电子标 CA 证书”质检项此前脱离系统真实的 `/resource/ca-management` 台账，仅进行静态文本文案比对，无法校验企业是否真实持有该平台的有效 CA 锁及证书状态；
      - 领域模型与端口倒置：定义不可变纯领域模型 `CaCertificateEvidence`（支持平台模糊匹配、有效期与借出状态判定）与端口 `CaCertificateEvidencePort`；由 `resources` 模块提供 `CaCertificateEvidenceAdapter`，真实查询系统有效在库/借出中的 CA 证书（平台名称、状态、证书类型、到期日、保管人等）；
      - 质检策略升级（`CaCertificateCheckPolicy`）：
        1. 真实匹配平台有效在库：直通 PASS 并组装知识库证据；
        2. 证书借出中：置 MANUAL_REVIEW 提示「CA证书当前处于借出状态（保管人: XX），请在投标/截标前及时催还」；
        3. 证书已过期：置 FAIL 提示过期阻断废标风险；
        4. 未匹配台账平台：置 MANUAL_REVIEW 并引导维护；
        5. 免收/纸质标直通：保持确定性免收通过；
      - 前端一键闭环：`extractKbLinks` 扩展支持 `ca-certificate`，输出「CA管理台账 ↗」链接，点击一键平滑跳转至 `/resource/ca-management?tab=ca`；
    - **知识库按需加载器多源依赖膨胀治理与架构守卫**：
      - `KnowledgeBaseOnDemandLoader` 接入 CA 端口后依赖字段达 6 个，超出 `MaintainabilityArchitectureTest.MAX_SERVICE_DEPENDENCIES = 5`；
      - 下沉抽取 `QualificationQueryDelegate`（持有新业务资质库与旧资质库），解耦新旧库回退逻辑；
      - `KnowledgeBaseOnDemandLoader` 依赖字段数收敛至 5 个，彻底清退架构豁免，保持代码高内聚低耦合。
47. **电子标 CA 证书质检项纠偏：彻底解除标书正文贴图约束并切断大模型错误覆写（2026-09-14）**：
    - **业务纠偏与真实源锁定**：明确 CA 锁是投标系统登录、标书加密与开标电子解密的媒介工具，核查唯一源为企业知识库台账（`/resource/ca-management`），与投标文件正文无关，标书正文根本无需粘贴证书扫描件或复印件；
    - **解除标书正文贴图约束**：标书正文仅保留对未替换占位模板（如【此处粘贴CA数字证书】）的反假拦截 FAIL，绝不因正文未提及证书信息或无截图而报错；
    - **切断大模型误覆写通道**：`BidDocumentQualityEngine` 移除了大模型覆写 CA 确定性台账结论的通道，并在招标要求缺失时兜底确保 `kbEvidences` 透传；同时纠偏 `AdjudicationGuidelines` 准则 11，从大模型知识库层面彻底杜绝误判；
48. **CA 证书平台双向归一化、通用分支假通过治理与供需比对看板（2026-09-14）**：
    - **平台双向对称归一化与别名机制（`CaCertificateEvidence`）**：
      - 根因剖析：招标文件要求“河北 CA / 河北省公共资源交易中心”，台账可能录入“河北CA”或“河北省交易平台”或“河北CA锁”，原有单向 `contains` 极易因噪音后缀或字序差异匹配落空；
      - 双向归一化（`normalizePlatform`）：对招标要求与台账名称双向剔除噪音词（交易中心/交易平台/服务平台/电子交易系统/CA锁/数字证书/省/市等）提取核心词根（如“河北”）；
      - 别名与跨省防冲突守卫：构建央企及政采别名映射（国网/南网/政采云）；同时引入跨省地域冲突防护（如“河北”不匹配“河南”），兼顾灵活性与安全性；
    - **通用电子标假通过彻底治理（`CaCertificateCheckPolicy`）**：
      - 废除通用分支“库内有任意锁即通过”的盲目放行逻辑；
      - 若招标文件未指定具体平台名称，安全降级为 `MANUAL_REVIEW`，提示项目人员核对招标平台与可用锁具，杜绝用错锁废标；
      - 若指定了平台名称但在库未找到匹配锁，坚决判定为 `FAIL`（不可盲目降级通过）；
    - **前后端口径对齐的三段式结构化输出与证据链强化**：
      - 策略统一输出 `【招标文件要求】`、`【企业CA台账比对】`、`【判定结论】` 三段式结构化文本；
      - 企业台账比对明确输出 `【已拥有】`、`【未拥有】`、`【已过期】`、`【已借出】`、`【在库可用】`，并在证据链中装载 `QualityEvidence.tender` 保证前端提取原文；
    - **前端质检详情弹窗专属【CA 证书供需比对】看板（`QualityCheckDetailDialog.vue`）**：
      - 引入纯函数 `parseCaComparison` 解析三段式结论；
      - 弹窗为 CA 质检项渲染专属左右供需比对卡片：左侧清晰呈现招标文件要求平台与原文摘录，右侧清晰呈现企业 CA 台账现状（明确标注拥有状态、证书信息、到期日及台账直跳链接），一目了然。
49. **项目 1360 标书核查待确认根因根治与纯核心领域规则收口（2026-11-07）**：
    - **资格条件跨章抽取与目录污染根治（`QualificationConditionExtractor`）**：
      - 目录点线行（`...... - 4 -`）与通用格式章节（“投标文件的语言|投标有效期|第二卷 技术标”）结构性过滤截断，防止目录与技术标附件清单被误抽为资格证书；
      - 修复阿拉伯序号子项在父级为中文大写标题时角色推导为 UNKNOWN 的问题，消除 `uncertainScope` 全员连坐；
    - **母子公司授权附注清洗与主语继承（`CertificateRequirementPolicy`）**：
      - 过滤条件性补充证明材料（授权书/证明资料/股权关系），保持子句继承整句主语 `Target.BIDDER`；
      - 清洗 `CERT_PREFIX` 解决标书命中证书但因前缀不一致假未命中缺陷；
    - **评分项留白消除整页关键词连坐与协议模板短路（`ScoringResponsePolicy`）**：
      - 移除整页包含服务方案关键词直接判全页占位留白的贪婪逻辑；识别《延保服务协议书》等合同样板留白并放行，解除对大模型满分标准核查的虚假短路拦截；
    - **响应性差异表与否决警示语放行（`ResponsivenessReviewPolicy` / `RuleCitationContext`）**：
      - 词表支持无表格差异表“无差异/无任何差异”正向识别；正向识别“否则将按弄虚作假进行处罚”为合法规则警示；
      - 投标文件组成对具备商务与技术主章节放行；招标文件未提及保证金场景判定为【不涉及】免收通过；
    - **后置硬编码待确认解封与百度 OCR + 大体积 DOCX 流式抽图闭环（`SemanticEvidenceBoundary` / `DocxComplianceImageExtractor` / `OpenAiDocumentPageVisionDetector`）**：
      - 解封 `SemanticEvidenceBoundary`、`QualificationMatchPolicy`、`QualificationVetoPolicy`、`IdAuthLetterCheckPolicy` 对已具备同页证书编号+有效期限实证、多维资格实证、或身份证图像+正反面完整要素场景的硬编码 `MANUAL_REVIEW` 降级；支持《投标人承诺函》作为投标函变体核验；
      - 新增 `DocxComplianceImageExtractor`（`ZipFile` + StAX 常数内存流式扫描），解决 600MB+ 大体积 `.docx` 超出 Sidecar 64MB 媒体限制跳过抽图及缺少 `pageHint` 问题；
      - 在 `OpenAiDocumentPageVisionDetector` 接入已配置的 `BaiduTableOcrClient`（单图级回退多模态并按页合并），打通内嵌扫描图片页的文字转写与下游规则核验闭环。
50. **响应性评审偏离表提取语义门禁与 DOCX 虚拟页码展示对齐（2026-11-11）**：
    - **偏离表强语义门禁与误报根治（`DeviationTableExtractor` / `DeviationEvidencePolicy`）**：
      - 强制要求偏离表具备明确标题（`isDeviationTitle`）或表头显式包含“偏离/偏差”，普通自查表、资格审查表、业绩表不再被误判为偏离表；
      - 收敛状态列（`typeCol`）识别，剔除“情况/类型”等泛化词；
      - 扩充规则上下文正则（`RULE_FRAME`），排除“资格要求/否决投标/废标/公平性”等法条引用被误当成负偏离自认；
      - 修复 `ResponsivenessReviewPolicy` 负偏离结论文案兜底（禁止输出冒号后为空的文案）；
    - **DOCX 虚拟页码与前端排版浮动指引（`QualityCheckDetailDialog.vue`）**：
      - 排查确认 DOCX 经 Sidecar 提取后无排版物理页码、退化为虚拟分块（`virtualPages`）机制；
      - 在前端质检弹窗“问题定位”旁增加排版浮动与结合原文摘录核对的明确提示，引导用户以最权威的原文摘录为准。

## 6. 后续任务（Backlog）

- **长文档动态自适应 Token 预算与模型上下文窗口感知**：当前 `SemanticPageRetriever` 采用固定字符预算（标书 60K、招标 30K）。后续可根据配置的模型上下文窗口大小（如 128K/256K tokens）动态自适应上调字符预算，并在 Tokenizer 层进行更精准的 token 计数。
- **多模态与表格深度语义增强**：对于长文档中的复杂表格（如设备清单、详细报价明细表），可引入 Markdown 表格结构化保留与表格专用切片算法，进一步提升大模型对复杂表格行列交叉关系的理解准确度。
- **独立 NOT_APPLICABLE 状态枚举支持**：为 `QualityCheckStatus` 评估增加独立的 `NOT_APPLICABLE`（不涉及）状态枚举及前端独立灰色标签展示，进一步提升用户对免除项的直观感知。
