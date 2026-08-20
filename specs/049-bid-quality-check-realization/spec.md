# Feature Specification: AI 标书质量核查实质化（真实文件、真实知识库、真实 AI、可追溯证据）

**Feature Branch**: `049-bid-quality-check-realization`
**Created**: 2026-08-20
**Status**: Draft
**Input**: PRD《AI 标书质量核查—抽屉详情》（https://my.feishu.cn/docx/PJXXdWIr8o18vIx9Ou3cRYPQnSb）、047 验收核对结论（2026-08-20 main 分支代码级核查）
**前置 Spec**: `specs/047-ai-bid-quality-check`（本 spec 是其验收未达标后的实质化补完，不是新功能）

---

## 1. 背景与定位

### 1.1 047 验收核对结论

`047-ai-bid-quality-check` 的 tasks 已全部勾选，但 2026-08-20 基于 `main`（ce267ddaa）的 PRD 逐项代码核查证明**验收未达标**：

- 形态完成度：约 70%（异步 taskId + 轮询、右侧 Drawer、25 项枚举、Evidence 结构、SHA-256 版本缓存、二次确认均已存在并被主入口消费）
- 核心业务实质完成度：约 20%
- 综合完成度：约 40%，**不能验收**

### 1.2 问题定性

工程骨架已完成，缺的是**真实性**。当前系统实质是"规则核查系统"，不是 PRD 定义的"AI 标书质量核查系统"。五个 P0 真实性缺口：

| # | P0 问题 | 当前代码证据（main） |
|---|---|---|
| ① | 真实投标文件 | `ComplianceTargetLoader.tryResolveLocalPath` 只认 `/` 与 `file://` 前缀；生产 fileUrl 为 `obs-direct:` / `bid-agent://` / `doc-insight://` 三种前缀（见 `ProjectDocumentStorageType`），**100% 走降级路径**，降级为"项目名称+描述"元数据文本继续核查 |
| ② | 真实招标文件 | `loadTenderDocument` → `loadProjectTenderText` 只读 `Tender.description` 字段，不解析已上传的招标文件 |
| ③ | 知识库真实性 | 资质库有真查询（name contains）；人员库/品牌授权库输出固定文案假证据（"已对照企业专家与项目经理库"，无任何数据匹配）；业绩库未接入（`WarehouseRepository` 被误注入且未使用，真实数据源是 performance 模块 `PerformanceRepository`） |
| ④ | Evidence 真实性 | `BidDocumentQualityEngine` 中 evidence 的 page 几乎全硬编码为 1、quote 全为写死文案，不是真实原文摘录 |
| ⑤ | AI 判断缺失 | 25 项核查全部是 `String.contains()` 关键词规则，无任何 LLM 调用；否决关键词提取硬编码只认 ISO27001/ISO9001/CMMI |

### 1.3 双链路并存（验收阻塞项）

同一产品功能存在两条入口，用户从不同按钮进入会得到不同核查结果：

```
入口 A（保留）: DraftingStage → QualityCheckDrawer → /api/compliance/bid-quality/trigger|progress|report
                → BidQualityCheckTaskAppService → @Async → BidDocumentQualityEngine（25 项）
入口 B（删除）: DraftingBidFilesArea → QualityCheckDialog → /api/compliance/bid-document/check|results
                → BidDocumentQualityCheckAppService（同步）→ BidDocumentQualityCheckPolicy（旧规则，第二组是第一组重复调用）→ 含 demo mock 数据
```

**决策：新异步链（入口 A）为唯一正式实现，入口 B 全部删除。**

### 1.4 本轮执行原则

- 非常聚焦，只做 P0 核心真实性，不做 UI 完善、不做无关重构。
- 不继续"完善两条链"，先合并再改造。

---

## 2. 唯一验收标准（压轴条款）

**本轮只验收这一条主线，跑不通则不能验收：**

> 拿一个真实投标 PDF + 一个真实招标 PDF，系统能基于真实文件解析、真实知识库数据、真实 LLM 判断，生成 25 项核查结果；每项结果可追溯到页码与原文。

可追溯的操作性定义（人工验收动作）：

```
AI 说"有问题"
  → 点进检查项详情
  → 能看到第几页（真实页码，非硬编码）
  → 能看到投标文件原文摘录（真实 quote）
  → 能看到招标文件对应要求（来自真实招标文件解析，非 Tender.description）
  → 能看到知识库记录（真实 recordId + 匹配结论，非固定文案）
```

### 2.1 不再验收清单（形态项已具备，不作为本轮验收点）

- 入口按钮与权限拦截（`canAIBidDocumentQualityCheck`）
- 右侧 Drawer 形态（el-drawer rtl、总览/统计/分组/证据链面板/加载进度）
- taskId 异步任务 + 2s 轮询 + 120s 超时
- 25 项枚举定义与 10/10/5 分组结构
- Evidence 对象结构与前端渲染
- 重新检查 + 二次确认（force=true 跳过缓存 + ElMessageBox）
- pass/fail/manual 三态与整体状态计算

---

## 3. 第零步：双链路合并（验收阻塞项，纯删除，最先执行）

### 3.1 保留链（唯一正式实现）

```
DraftingStage
  → QualityCheckDrawer.vue + useQualityCheckDrawer.js
  → POST /api/compliance/bid-quality/trigger/{projectId}?force=
  → GET  /api/compliance/bid-quality/progress/{taskId}
  → GET  /api/compliance/bid-quality/report/{projectId}
  → BidQualityCheckTaskAppService → BidQualityCheckAsyncExecutor → BidDocumentQualityEngine
```

### 3.2 删除清单（一次性删净，不留"向后兼容"残留）

**后端**（`backend/src/main/java/com/xiyu/bid/compliance/`）：

| 删除对象 | 说明 |
|---|---|
| `POST /api/compliance/bid-document/check/{projectId}` 端点 | ComplianceController 旧接口 |
| `GET /api/compliance/bid-document/results/{projectId}` 端点 | ComplianceController 旧接口 |
| `application/BidDocumentQualityCheckAppService.java` | 旧同步编排服务 |
| `domain/BidDocumentQualityCheckPolicy.java` | 旧核心策略（第二组检查为第一组重复调用） |
| `domain/BasicInfoCheckRules.java` | 旧规则类（仅旧 Policy 消费时删除） |
| `domain/FormattingCheckRules.java` | 同上 |
| `domain/AttachmentCheckRules.java` | 同上 |
| `ComplianceTargetLoader.loadProjectDocumentContent()` | 若删除后无其他引用 |
| `ComplianceTargetLoader.loadProjectTenderText()` | 第二步完成后被真实招标文件解析替代，若无其他引用则删除 |
| `test/.../BidDocumentQualityCheckAppServiceTest.java` | 对应测试 |
| `test/.../BidDocumentQualityCheckPolicyTest.java` | 对应测试 |

**前端**（`src/`）：

| 删除对象 | 说明 |
|---|---|
| `views/Project/stages/components/QualityCheckDialog.vue` | 旧弹窗 |
| `composables/projectDetail/useProjectDetailAI.js` 中 `runBidDocumentQualityCheck`（含 isDemoMode mock 数据段）及关联的 `loadBidDocumentQualityResult`、`bidDocQualityResult`/`bidDocQualityChecking` 状态 | 若删除后无其他引用 |
| `views/Project/stages/components/DraftingBidFilesArea.vue` 中"AI标书质量核查"按钮（`handleOpenBidDocumentQualityCheck`） | 旧入口 |
| `api/modules/ai/compliance.js` 中 `checkBidDocumentQuality` / `getBidDocumentQualityResult` | 旧 API 封装 |
| `DraftingStage.vue` 中 watch `ctx.bidDocQualityResult` 的逻辑（L319-321） | 旧链联动 |

**测试资产**：

- `api-tests/` 中对 `bid-document/check`、`bid-document/results` 的 .http 用例（实施时 grep 确认具体文件）

### 3.3 删除验证

全仓 grep 无以下残留引用：`bid-document/check`、`bid-document/results`、`BidDocumentQualityCheckPolicy`、`BidDocumentQualityCheckAppService`、`QualityCheckDialog`、`runBidDocumentQualityCheck`。后端编译通过 + 前端 lint/build 通过 + 相关单测全绿。

---

## 4. 五步任务边界

### 第一步：真实投标文件（禁止降级）

**目标**：核查输入必须是用户实际上传的 PDF/Word 文件解析结果。

- **修复 seam**：`ComplianceTargetLoader.loadBidDocument`。按 `ProjectDocumentStorageType.fromFileUrl(fileUrl)` 分派取流：
  - `LOCAL_BID_AGENT` / `DOC_INSIGHT` → `ProjectDocumentFileStorage.load` 流式读取
  - `OBS_DIRECT` → `ObsShareUrlSigner` 预签名 URL 下载
  - 取到物理文件/流后交 `SidecarStreamDocumentAdapter.convertDocument` → `DocumentContent`（已含 `PageContent(pageNumber, text, headings)` 分页，Sidecar 按 `\f` 物理分页）
- **禁止项**：
  - 禁止无文件/解析失败时 fallback 到项目描述继续核查
  - 禁止 `buildFallbackDocumentText` 类元数据拼接路径
- **前置校验（已拍板）**：`triggerQualityCheck` 同步前置校验，项目无 BID 类 ProjectDocument 或文件不可解析时**直接 4xx 返回"无法执行核查，请先上传投标文件"，不创建任务**；前端 Drawer 捕获后弹提示。
- **测试要求**：单测覆盖三种存储前缀分派 + 无文件 4xx；集成测试用真实 PDF 走 Sidecar 出 PageContent[]。

### 第二步：真实招标文件 + 结构化要求提取

**目标**：核查依据来自真实招标文件的 AI 结构化提取，替代 `Tender.description` 与硬编码关键词。

- **复用 seam**：`scoreparse/application/InitiationTenderTextResolver.findLatestTenderDocument`（category = TENDER / TENDER_FILE）定位招标文件，走与第一步相同的存储分派 + Sidecar 解析得 `DocumentContent`。
- **LLM 结构化提取**（招标文件 → 结构化要求对象）：评分项、硬性要求（资格条件）、否决条款及关键词、投标文件组成要求、格式要求。替代 `BidQualityCheckAsyncExecutor.extractRequiredCerts` 的 ISO27001/ISO9001/CMMI 硬编码。
- **招标未上传分支（PRD §1.1/§5.2）**：独立检查项（格式、签章、附件完整性等）照常执行；需比对项状态 MANUAL + reasonCode `REFERENCE_MISSING`，注明"招标文件未上传，无法比对"。
- **测试要求**：真实招标 PDF 提取出非空结构化要求（评分项/否决条款至少各一）；未上传分支单测。

### 第三步：知识库真实查询

**目标**：四库全部真实数据匹配，每条 Evidence 可回答"来自哪里"。

| 知识库 | 数据源 | 现状 | 改造 |
|---|---|---|---|
| 资质库 | `QualificationRepository` | 真查询（name contains） | 保留并输出结构化匹配结果 |
| 品牌授权库 | `BrandAuthorizationRepository` | 固定文案假证据 | 真实查询授权记录（品牌、期限、范围） |
| 人员库 | `PersonnelRepository` | 固定文案假证据 | 真实查询人员及资质证书 |
| 业绩库 | performance 模块 `PerformanceRepository`（`PerformanceRecordEntity`） | **未接入**（误注入 WarehouseRepository） | 替换为真实业绩记录查询 |

- **Evidence 结构升级**（每条必须可溯源）：

```json
{
  "source": "qualification | brand-authorization | personnel | performance",
  "recordId": "12345",
  "content": "ISO27001认证（有效期至 2027-03）",
  "match": true
}
```

- **禁止项**：删除"已对照企业专家与项目经理库"、"已检索品牌制造商授权与代理证书"等无数据支撑的固定文案证据。
- **知识库异常分支（PRD §5.1）**：查询超时/异常时依赖该库的检查项置 MANUAL，不阻断其他项。
- **测试要求**：四库各至少一条真实数据匹配的集成测试；空库/异常分支单测。

### 第四步：Evidence 真实性（页码与原文）

**目标**：所有 evidence 的 page 与 quote 来自真实文档定位。

- page：基于 `DocumentContent.pages`（`PageContent.pageNumber`）做关键词/章节定位得到的真实页码；无法定位时 page 为 null，**禁止填 1 充数**。
- quote：投标文件/招标文件的真实原文摘录（截取命中上下文），禁止写死文案。
- `QualityEvidence` record 字段扩展以承载第二步/第三步的结构化数据（source 枚举、match 布尔、recordId），保持向后兼容序列化。
- 前端 `QualityCheckItemCard.vue` 证据链面板已支持 sourceType/pageNumber/quote 渲染，仅需适配新字段。
- **测试要求**：给定已知内容的测试 PDF，断言 evidence.page 等于预期页码、quote 为文档原文子串。

### 第五步：LLM 裁决接入

**目标**：25 项判定由"确定性规则 + 结构化数据 + LLM"混合完成，替代纯 `String.contains()`。

- **架构（已拍板，禁止 LLM 一步出全部结果）**：

```
确定性规则（18 项规则类检查毫秒级执行）
+ 招标要求结构化数据（第二步产物）
+ 知识库真实数据（第三步产物）
+ 文档 Evidence（第四步产物）
        ↓
      LLM 裁决（语义匹配项：responsive_clause 类、veto_keyword 二阶段语义判断、
                scoring_response、clarification_resp 等）
        ↓
PASS / FAIL / MANUAL + reason（含 reasonCode）+ evidence
```

- **复用 seam**：`ai/client/RoutingAiProvider`（已有运行时配置路由）；范式参照 `scoreparse/infrastructure/openai/OpenAiScoreAnalyzer` 的召回 lane + `ProgressReporter` 进度回调，接入异步任务的 progress 上报。
- **LLM 失败分支**：单项 LLM 调用失败置 MANUAL + `LOW_CONFIDENCE`，不阻断整体任务；AI 配置未启用（Noop 场景）时语义项全部 MANUAL 并注明原因。
- **MANUAL reasonCode 集合（PRD §3.5）**：EVIDENCE_INSUFFICIENT / AMBIGUOUS / LOW_CONFIDENCE / REFERENCE_MISSING / MANUAL_REVIEW_REQUIRED。
- **测试要求**：确定性规则项不依赖 LLM 的单测保持全绿；LLM 项以契约测试固定输入输出结构。

### 4.1 范围排除（防止范围蔓延，另开任务）

- 25 项编码命名风格对齐 PRD（`COVER_INFO` vs PRD `cover_info`）与部分项名称差异——记录为已知偏差，不影响本轮唯一验收标准。
- AI 评分跳转（PRD §3.7 autoScore=1）——Drawer 暂无此按钮，另开任务。
- Redis/DB 存储口径切换——取决于第 5 节产品拍板结果。

---

## 5. 决策记录与拍板事项

### 5.1 已拍板（2026-08-20，产品+开发共同确认）

1. **无投标文件时 trigger 同步 4xx**：不创建任务、不走任务失败态，前端弹"无法执行核查，请先上传投标文件"。
2. **旧链一次性删除**：不留向后兼容残留（第 3 节清单），避免"代码做了没人消费"复发。
3. **新异步链为唯一正式实现**；LLM 只做裁决层、不做全量直出。

### 5.2 待产品拍板（阻塞项，不阻塞本 spec 起草，阻塞第五步之后的收尾）

**核查结果存储口径**，PRD §4.4 与当前工程实现冲突：

| 方案 | 内容 | 取舍 |
|---|---|---|
| A. 坚持 PRD 原文 | 结果不持久化，Redis TTL 30 分钟，关闭抽屉释放 | 每次重开抽屉重跑 AI，成本高、无历史追溯 |
| B. 认可当前工程方案（倾向） | DB 保存核查报告 + 按文件版本 hash 缓存复用 + force=true 强制重跑 | 需修订 PRD §4.4 |

倾向 B，但必须由产品确认后写入本节，不得由开发自行决定。

**✅ 决策已定案（2026-08-20，产品确认）**：采纳**方案 B**——DB 保存核查报告 + 按文件版本 hash 缓存复用 + force=true 强制重跑。PRD §4.4 原文（不持久化 + Redis TTL 30 分钟）废弃，待下次 PRD 修订时同步。当前工程实现（`ComplianceCheckResult` 持久化 + `BidQualityCheckTaskAppService` 缓存命中/force 重跑）即为目标形态，无需另开改造任务。G6 门禁达成。

---

## 6. 验收门禁清单（Acceptance Gate）

| # | 门禁 | 验证方式 |
|---|---|---|
| G1 | 唯一主线跑通（第 2 节操作性定义全链路人工走查） | 真实投标 PDF + 真实招标 PDF，25 项结果逐项可追溯页码/原文/招标要求/知识库记录 |
| G2 | 双链路删除无残留 | 全仓 grep 第 3.3 节关键词为零命中；`DraftingBidFilesArea` 无质量核查按钮 |
| G3 | 无文件不核查 | 未上传投标文件的项目调 trigger 返回 4xx 且未产生任务记录 |
| G4 | 每变必测 | 每步提交附测试证据（单测/集成测试结果），对齐 `RELIABILITY.md §关键硬约束`；`mvn test` 与前端 lint/build 全绿 |
| G5 | 无 Mock 数据 | 全链路无 demo mock / 固定文案证据残留（对齐 `SECURITY.md §Mock 政策`） |
| G6 | 存储口径定案 | 第 5.2 节产品决策落定并反映到实现/PRD |

---

## 附：核对过程引用的关键代码位置（main @ ce267ddaa）

- 保留链：`backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java`、`BidQualityCheckAsyncExecutor.java`、`domain/policy/BidDocumentQualityEngine.java`、`domain/BidQualityCheckItem.java`、`domain/QualityCheckItemResult.java`、`domain/QualityEvidence.java`、`domain/DocumentContent.java`
- 文件加载：`compliance/service/ComplianceTargetLoader.java`、`SidecarStreamDocumentAdapter.java`、`KnowledgeBaseOnDemandLoader.java`
- 存储分派：`projectworkflow/core/ProjectDocumentStorageType.java`、`projectworkflow/service/ProjectDocumentDownloadService.java`
- 招标文件定位复用：`scoreparse/application/InitiationTenderTextResolver.java`
- 业绩库：`performance/domain/port/PerformanceRepository.java`、`performance/infrastructure/persistence/entity/PerformanceRecordEntity.java`
- LLM 接入：`ai/client/RoutingAiProvider.java`、`scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java`（召回 lane + ProgressReporter 范式）
- 前端：`src/views/Project/stages/DraftingStage.vue`、`components/QualityCheckDrawer.vue`、`components/QualityCheckItemCard.vue`、`composables/useQualityCheckDrawer.js`、`src/api/modules/ai/compliance.js`
