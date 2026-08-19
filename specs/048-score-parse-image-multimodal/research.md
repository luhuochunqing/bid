# Research: 投标文件多模态图片评分（方案A）

**Feature**: 048-score-parse-image-multimodal | **Date**: 2026-08-18

> Phase 0 输出。目标：把 spec 的三处方案A改造成落成可执行的技术决策，消解 NEEDS CLARIFICATION。

## R1. 图片在链路中是何时、何处丢失的？（根因确认）

阶段2 打分链路（`ScoreScoringAppService.doExecuteScoring` → `BidFileLoadingCoordinator.load` →
`SidecarStreamTextExtractor.extract` / InMemory `TenderDocumentTextExtractor.extract` →
`ScoreItemAssessor.assess` → `scoreAnalyzer.assessObjective` → `ScoreParsePrompts.buildObjectiveAssessmentPrompt`
→ `OpenAiStructuredOutputService.request` → `OpenAiSdkStructuredOutputTransport.requestWithChatCompletions`）：

- **丢失点 ①（sidecar 转换）**：`POST /convert-stream` 与 `POST /convert` 只回 markdown 纯文本
  （`app.py`）。markitdown 对图片只嵌占位符，二进制/图片内容完全不进响应。
- **丢失点 ②（transport）**：`OpenAiSdkStructuredOutputTransport.requestWithChatCompletions`
  用 `ChatCompletionCreateParams.builder().addUserMessage(prompt)` 只发单条文本。
- **丢失点 ③（调用层）**：`ScoreItemAssessor.assess` 只算文本 `excerpt`，不打分请求里携带任何图片。

结论：三处都改（sidecar 抽图 → transport 支持图片 content → 调用层按评分项配图），一处不落则图片仍到不了模型。

## R2. transport 只走 CHAT_COMPLETIONS（决定：图片仅支持 chat.completions 多段 content）

`OpenAiBidAgentConfigurationResolver.buildRequestConfig` 恒返回 `CHAT_COMPLETIONS` 风格
（`OpenAiBidAgentApiStyle.CHAT_COMPLETIONS`），`OpenAiStructuredOutputService.request` 因此只命中
`requestWithChatCompletions`。`RESPONSES` 风格在本 feature 不实现图片（`OpenAiSdkStructuredOutputTransport`
未用，评分链路实际不触发）。不影响 FR-002（objective 对标）。

多模态注入点 = `OpenAiSdkStructuredOutputTransport` 内构造 message 的地方，把"单文本"改为
"文本 + `image_url` 分段的 `ChatCompletionContentPart` 列表"。POC 已验证 `data:image/<mime>;base64,<b64>`
内联 `image_url` 对 gemini-3.5-flash（custom provider 网关）可读图。

### 开放项（实现阶段以真实 SDK 编译为准）
- OpenAI Java SDK 里构造"用户消息多段 content"的确切 API（`ChatCompletionUserMessageParam.content` 是
  `List<ChatCompletionContentPart>`；图片 part 用 `ChatCompletionContentPart.ofImageUrl(...)`，
  文本 part 用 `ofText(...)`）。json_schema 路径用 `StructuredChatCompletionCreateParams`、
  json_object 路径用 `ChatCompletionCreateParams`，两路径都要支持多段 content。
- `jsonSchemaUnsupported` 的 fallback（BadRequestException → json_object）对图片路径同样适用：
  若模型/网关报"不支持图片 part"即抛错 → 调用层降级纯文本（FR-005）。

## R3. sidecar 如何抽图并回传？（决定：新增 `POST /convert-images`，OnDisk 专用）

### 为什么新端点，而不是改 /convert 或 /convert-stream
- `/convert` 被 docinsight 用，045 契约显式保留不动；且 `/convert` 走整包内存，GB 投标文件会 OOM——
  这正是 `/convert-stream` 存在的根因。
- `/convert-stream` 契约是 `text/plain` 流，显式"不返回元数据"。
- 因此打分链路 OnDisk 抽图走**新端点**，最小侵入、语义清晰。

### 抽取类型分派
| 类型 | 抽取库 | 图片→章节归属 |
|---|---|---|
| `.docx` | python-docx（遍历 inline image run） | 以该图所在段之前的最近标题为章节；无标题按文档顺序 ordinal |
| `.pptx` | python-pptx（遍历 shape，shape_type=PICTURE） | 以所在 slide 标题为章节；无标题按 slide 序 ordinal |
| `.pdf` | PyMuPDF (fitz)（`page.get_images` 抽取内嵌图 + 页面截图兜底） | **有文本层**：用文本层建"页码→最近标题"映射回填 `section`（页首向上找最近标题，约 30 行）；**纯扫描无文本层**：整页截图仅带 `pageHint`、`section=null`，并在 `warnings` 明示"扫描件无章节归属"（透明降级，review M4/D8） |
| 其它/不支持 | 不抽图，返回 `images=[]`（走纯文本降级） | — |

### 预算控制（避免重蹈 GB 文件整载内存 OOM）
- sidecar 端抽图设**单文件总图预算** `SIDECAR_MAX_IMAGE_BYTES`（默认 `15728640` = 15MB）：累计抽取图字节超限即停，
  返回 `truncated=true` + 已抽图片。首版不做"两阶段抽图"（先清单后按需取图），改为一阶段抽图 + 预算截断，
  控制复杂度（spec 假设"与体积超限按 FR-004 裁剪"落地为 sidecar 预算截断 + Java 配给裁剪两层）。
- **默认值校准（review H1）**：base64 膨胀 ×4/3 —— 若预算 20MB，二进制 ≈ 26.7MB base64 + JSON 包装 >
  Java 响应上限 25MB，会出现"图片预算越用满、Java 侧越必超限降级"的数值矛盾（图最多的文档反而拿不到图）。
  故 sidecar 预算定 15MB（→ ≈20MB base64，留 5MB 余量），Java `max-extract-response-bytes` 维持 25MB；
  上调任一侧须同步校验另一侧。
- 每图 `width/height/bytes` 记录，供 token 估算（R5）。

### 响应契约
```
POST /convert-images  (multipart file; X-Sidecar-Key)
200 -> {
  "images": [
    {"index":0,"mimeType":"image/png","section":"3.2 业绩要求",
     "pageHint":null,"width":800,"height":600,"base64":"iVBORw0K..."}
  ],
  "truncated": false,
  "warnings": []
}
```
`section` 用文档章节标题（与打分链路既有章节口径：`extract_headings` / `BidChapterDirtySet` 复用其字面），
`pageHint` 仅 PDF 有。认证/413/422 语义与 `/convert` 一致（复用 `_check_auth`、size guard 骨架）。

### 服务端调用方
`SidecarStreamTextExtractor` 同目录新增 `SidecarImageExtractor`（HttpClient，`/convert-images`），
复用 `app.score-parse.sidecar-url` 与 `SIDECAR_SHARED_KEY`。请求从 OnDisk 临时文件流式上传（`ofFile`），
响应为 JSON 整包读（受 `max-image-total-bytes` 上限约束）。

## R4. 图片按评分项配给（FR-003/FR-004）——纯核心策略

新增纯核心（Principle I）：
- `DocumentImage`（record：index, ordinal, mimeType, section, pageHint, width, height, base64）。
- `ImageAssigner`（纯策略，无框架依赖）：
  1. 用评分项的 `dim`/`detail`/`code` 作为关键词，对图片的 `section`（+`pageHint`）做**章节匹配**，
     复用既有 `ScoreItemChapterMatch.related` 的字面重叠语义（含"引用含标题""标题含维度"等方向）；
  2. 命中章节 → 选该章节图片；无任何命中 → 空配给（走纯文本）；
  3. 超过单请求预算（最多图数 K / 最多字节 B，来自调用方传入的数值常量）→ 按"章节匹配距离近者优先 +
     ordinal 顺序"确定性裁剪，置 `truncated=true`（保留裁剪信号）。
- `ImageAssignmentPlan`（record：`List<DocumentImage> images, boolean truncated, boolean degraded`）。
- `ImageTokenEstimator`（纯核心）：按"图 tile 计量"启发式估算图片 token，输入 width/height/mimeType →
  返回估算 token 数，供观测（FR-006）。真实定价以 gemini-3.5-flash 现网实测为准（POC 已跑过真实样本），
  首版只做"可观测估算"，不做精确计费。

### 编排改动
- `BidFileLoadingCoordinator.LoadedBidDocument` 增加 `images`（`List<DocumentImage>`，默认空）。
  **InMemory 与 OnDisk 两路径均调 `SidecarImageExtractor`**（review H2 修正：`ScoreBidDocumentLookup` 中
  本地 ProjectDocumentFileStorage 与 doc-insight:// 文档全量 byte[] 走 InMemory 且无字节上限，若仅 OnDisk
  抽图，doc-insight 上传的投标文件将整体静默无图）。`SidecarImageExtractor` 提供 `ofFile`（OnDisk 流式）
  与 `ofByteArray`（InMemory 直传）双入口；任一路径抽图失败 → `log.warn` + 空图降级，不阻断任务。
- `ScoreItemAssessor.assess` objective 分支：`ImageAssigner.assign(item, images, budget)` → 把
  `ImageAssignmentPlan` 传给 `scoreAnalyzer.assessObjective(detail, weight, excerpt, images)`。
- `ScoreScoringAppService.doExecuteScoring`：`assessor.assess(task, item, bidDocText)` 调用点传入
  `loaded.images()`；`ImageBudget`（来自 `app.score-parse.multimodal.*` 配置）经构造器/参数注入——
  `ScoreItemAssessor` 为每任务 `new` 构造、非 Spring Bean（review M1 补齐此前任务清单缺口）。
- `OpenAiScoreAnalyzer.assessObjective` 新过载，`ScoreParsePrompts.buildObjectiveAssessmentPrompt`
  增加图片指引段落；图片以 content part 随 prompt 发送。**US1 措辞中性**（review M3：US1 最简配给按文档顺序
  携带、与评分项无关，若声明"相关节选"会失真诱导误判）："附图为投标文件中的部分图片，请先判断每张图是否与
  评分项相关，再结合相关图片判定满足度"；US2 章节配给落地后换"相关节选图"措辞。
- 降级（FR-005）：抽图失败 / 图片损坏 / 超预算截断后仍超网关限制 / 模型报不支持图片 → 该项走纯文本，
  不阻断任务，`log.warn` + Sentry（Principle VII §3）。

## R5. token/成本可观测与兜底开关（FR-006 / US3, US4）

- 观测量：每次 objective 调用记录 `imageCount`、`estimatedImageTokens`、`textTokens` 占比；
  **仅结构化日志留痕，不动 `ScoreResult` 实体**（加字段=Flyway 迁移，违背 D6 不落库决策；review L3 收紧口径）。
- 兜底：配置开关 `app.score-parse.multimodal.enabled`（默认 true），关闭后打分完全不携带图片（纯文本）。
  图片 token 估算逼近配置上限时触发 R4 裁剪。
- 现网指标缺失处（单请求图片数上限默认值、图片 token 占比阈值）在 tasks/实现阶段按实测定价确定，
  本 feature 定义行为语义而非硬编码具体值（与 spec Assumptions 一致）。

## R6. 图片不落库（决定）

图片属投标文件受控数据，生命周期仅随单次打分任务内存流转，任务结束即释放；`truncated`/`imageCount`
等观测信号随 `ScoreResult` 现有字段（或日志）留痕。**不加 DB 表、不加 Flyway 迁移**（符合"简单优先"、
避免扩 schema 边界）。若后续需要加分项级图片审计再独立讨论。

## 决策清单

| # | 决策 | 依据 |
|---|---|---|
| D1 | transport 仅改 chat.completions；RESPONSES 不做 | config resolver 恒返回 CHAT_COMPLETIONS |
| D2 | 新增 sidecar `POST /convert-images`，不动 /convert、/convert-stream | 045 契约 + OOM 根因 |
| D3 | 抽图一阶段 + 预算截断，不做两阶段 | 复杂度优先，spec 假设落地 |
| D4 | 配给＝纯核心 `ImageAssigner` 复用章节匹配字面语义 | FR-003/FR-007 |
| D5 | **两路径（InMemory/OnDisk）均抽图**：byte[] 直传 / 临时文件流式双入口 | `ScoreBidDocumentLookup` 事实：doc-insight/本地存储全量走 InMemory（review H2 推翻原判"OnDisk 抽图，InMemory 降级"） |
| D6 | 图片不落库，随任务内存释放 | 简单优先，不扩 schema 边界 |
| D7 | 配置开关 + 观测字段，默认开启 | FR-006 |
| D8 | PDF 章节归属：有文本层用"页码→最近标题"映射回填 section；纯扫描件仅 pageHint + warnings 明示降级 | review M4 |

## Review 修正记录（2026-08-18 设计评审）

| 编号 | 问题 | 处置 |
|---|---|---|
| H1 | sidecar 20MB 预算 × base64 4/3 膨胀 > Java 25MB 响应上限，"预算用满必降级"数值矛盾 | 预算降为 15MB 并写入双侧校准规则（R3） |
| H2 | InMemory 路径（doc-insight/本地存储）不抽图，主要文档来源静默无图 | 两路径均抽图，`SidecarImageExtractor` 双入口（R4、D5） |
| M1 | tasks 漏改 `ScoreScoringAppService`，图片流不进 assess() | 补任务：assessor 签名扩展 + doExecuteScoring 接线（R4） |
| M2 | `ScoreItemChapterMatch.related()` 对 quote/evidence=null 恒返回 true，直接复用会"全相关" | `ImageAssigner` 复用字面重叠方向（contains 六向判断），不复用方法本身 |
| M3 | US1 最简配给 + "相关节选"prompt 措辞失真，可能诱导误判满足 | US1 prompt 中性措辞（先判断相关性再判定），US2 落地后换措辞（R4） |
| M4 | 扫描 PDF 图片无章节归属，最需要看图的场景失效 | 有文本层回填 section；纯扫描件 warnings 明示降级（D8） |
| L1 | 同一文件对 sidecar 双上传双解析（文本+图片各一次） | localhost 单用途容器可接受，列技术债（不合并端点，保 045 流式契约） |
| L2 | `multimodal` 配置落点需对齐既有模式 | 配置走 dev yml + 代码默认值，prod 靠 env 注入；核对 prod 环境变量清单 |
| L3 | "随打分结果留痕"措辞与不落库决策矛盾 | 收紧为仅结构化日志，不动 `ScoreResult` 实体（R5） |
| L4 | 存量项目 skip 指纹直接跳过，不自动受益 | quickstart 写明验证需新文档或改评分项 |
| L5 | markitdown 可能已可输出 data-URI 图（复用机会） | T009 前做 30 分钟 spike 确认；不成立再自建 docx 分支 |