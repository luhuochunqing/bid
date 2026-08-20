# Tasks: AI 标书质量核查实质化（真实文件、真实知识库、真实 AI、可追溯证据）

**Input**: Design documents from `/specs/049-bid-quality-check-realization/`

**Prerequisites**: plan.md ✅、spec.md ✅、research.md ✅、data-model.md ✅、contracts/ ✅、quickstart.md ✅

**Tests**: 包含测试任务——Constitution III（TDD，NON-NEGOTIABLE）与 spec §4 各步"测试要求"明确要求每变必测、测试先行。

**Organization**: 任务按 user story 分组。本功能的 story 映射：**第零步（双链路删除）= Foundational**（验收阻塞项，阻塞一切后续）；**第一步~第五步 = US1~US5**（严格按序，后步依赖前步产物，详见 Dependencies）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成任务依赖）
- **[Story]**: US1~US5 对应 spec §4 第一步~第五步
- 所有任务含具体文件路径

## Path Conventions

- 后端：`backend/src/main/java/com/xiyu/bid/compliance/`（主）+ 跨包复用 seam
- 后端测试：`backend/src/test/java/com/xiyu/bid/compliance/`
- 前端：`src/`（Vue 3）
- 接口测试：`api-tests/`

---

## Phase 1: Setup（开工前置）

**Purpose**: Sync-First + Lease 检查 + 任务分支（Constitution §Development Workflow）

- [X] T001 执行 `git fetch origin && git rebase origin/main`，确认基线与 main（ce267ddaa 或更新）一致
- [X] T002 Lease 检查：`./scripts/who-touches.sh backend/src/main/java/com/xiyu/bid/compliance` 与 `./scripts/who-touches.sh src/views/Project/stages`，退出码均 0 方可开工
- [X] T003 开任务分支：`scripts/agent-start-task.sh qoder bid-quality-realization origin/main --in-place`

---

## Phase 2: Foundational — 第零步：双链路合并（验收阻塞项，纯删除）

**Purpose**: 删除旧同步链（入口 B），新异步链成为唯一实现。**本阶段未完成前禁止开始任何 US 任务**（spec §3）

### 后端删除

- [X] T004 删除 `ComplianceController.java` 中旧端点 `POST /bid-document/check/{projectId}` 与 `GET /bid-document/results/{projectId}`（backend/src/main/java/com/xiyu/bid/compliance/controller/ComplianceController.java）
- [X] T005 删除旧同步编排服务 backend/src/main/java/com/xiyu/bid/compliance/application/BidDocumentQualityCheckAppService.java
- [X] T006 删除旧核心策略 backend/src/main/java/com/xiyu/bid/compliance/domain/BidDocumentQualityCheckPolicy.java
- [X] T007 [P] 先 grep 确认仅旧 Policy 消费后，删除旧规则类 backend/src/main/java/com/xiyu/bid/compliance/domain/BasicInfoCheckRules.java、FormattingCheckRules.java、AttachmentCheckRules.java
- [X] T008 删除对应测试 backend/src/test/java/com/xiyu/bid/compliance/ 下 BidDocumentQualityCheckAppServiceTest.java 与 BidDocumentQualityCheckPolicyTest.java
- [X] T009 删除 `ComplianceTargetLoader.loadProjectDocumentContent()`（backend/src/main/java/com/xiyu/bid/compliance/service/ComplianceTargetLoader.java，先 grep 确认无其他引用）

### 前端删除

- [X] T010 [P] 删除旧弹窗 src/views/Project/stages/components/QualityCheckDialog.vue
- [X] T011 删除 src/composables/projectDetail/useProjectDetailAI.js 中 `runBidDocumentQualityCheck`（含 isDemoMode mock 数据段）、`loadBidDocumentQualityResult`、`bidDocQualityResult`/`bidDocQualityChecking` 状态及全部引用
- [X] T012 删除 src/views/Project/stages/components/DraftingBidFilesArea.vue 中"AI标书质量核查"按钮与 `handleOpenBidDocumentQualityCheck`
- [X] T013 删除 src/api/modules/ai/compliance.js 中 `checkBidDocumentQuality` / `getBidDocumentQualityResult` 两个旧 API 封装
- [X] T014 删除 src/views/Project/stages/DraftingStage.vue 中 watch `ctx.bidDocQualityResult` 的旧链联动逻辑

### 测试资产删除 + 删除验证

- [X] T015 删除 api-tests/ 中引用 `bid-document/check`、`bid-document/results` 的 .http 用例（grep 确认 api-tests/ 本无旧链路用例，零删除）
- [X] T016 G2 删除验证（quickstart.md §1）：全仓 grep `bid-document/check|bid-document/results|BidDocumentQualityCheckPolicy|BidDocumentQualityCheckAppService|QualityCheckDialog|runBidDocumentQualityCheck` 零命中；`cd backend && mvn -q compile && mvn -q test` 全绿；`npm run lint && npm run build` 全绿；原子提交（附测试证据）
  - 验证证据（2026-08-20）：grep 零命中 ✓；mvn compile ✓；mvn test 8224 用例，compliance 包全绿，残余失败类（calendar/casework/security 等）经干净 origin/main 对照实验确认为既有基线失败；npm lint 0 errors + build exit 0 ✓

**Checkpoint**: 旧链零残留，保留链（trigger/progress/report → TaskAppService → Engine）编译与测试全绿，独立可提交

---

## Phase 3: User Story 1 — 真实投标文件（第一步，Priority: P1）🎯 MVP

**Goal**: 核查输入必须是用户实际上传的 PDF/Word 文件解析结果；三种生产存储前缀全部真实取流，禁止降级；无文件时 trigger 同步 4xx

**Independent Test**: 未上传投标文件的项目调 trigger 返回 4xx 且不建任务（G3）；三种前缀分派单测全绿；真实 PDF 走 Sidecar 出 PageContent[]

### Tests for US1 ⚠️（先写，确保 FAIL）

- [X] T017 [P] [US1] 存储分派单测：`obs-direct:`/`bid-agent://`/`doc-insight://` 三前缀各一例 + 未知前缀报错用例，新建 backend/src/test/java/com/xiyu/bid/compliance/service/ComplianceTargetLoaderStorageDispatchTest.java
- [X] T018 [P] [US1] trigger 前置校验单测：无 BID 文档 → 4xx 且不落任务记录；有文档 → 正常建任务，新建 backend/src/test/java/com/xiyu/bid/compliance/application/BidQualityCheckTriggerPreconditionTest.java

### Implementation for US1

- [X] T019 [US1] 重构 `ComplianceTargetLoader.loadBidDocument`（backend/src/main/java/com/xiyu/bid/compliance/service/ComplianceTargetLoader.java）：按 `ProjectDocumentStorageType.fromFileUrl` 分派——LOCAL_BID_AGENT/DOC_INSIGHT 走 `ProjectDocumentFileStorage.load` 流式读取、OBS_DIRECT 走 `ObsShareUrlSigner` 预签名下载，取到物理文件交 `SidecarStreamDocumentAdapter.convertDocument`（分派模式复用 ProjectDocumentDownloadService，research.md R1）
- [X] T020 [US1] 删除同文件中 `tryResolveLocalPath` 降级判断与 `buildFallbackDocumentText` 元数据拼接路径（spec §4 第一步禁止项）
- [X] T021 [US1] `BidQualityCheckTaskAppService.triggerQualityCheck`（backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckTaskAppService.java）新增同步前置校验：项目无 BID 类 ProjectDocument 或文件不可解析 → 抛业务异常返回 4xx"无法执行核查，请先上传投标文件"，不创建任务（contracts/bid-quality-api.md §1）
- [X] T022 [US1] 前端 src/composables/useQualityCheckDrawer.js 捕获 trigger 4xx → ElMessage 提示且不进入轮询
- [X] T023 [US1] api-tests/ 新增无文件项目 trigger 4xx 用例（沿用既有 .http 文件组织，api-tests/ai.http §8/§9）
- [X] T024 [US1] 集成验证：compliance 包 42/43 用例全绿（唯一 error = ComplianceCheckServiceCaseLibraryIntegrationTest，已经干净 origin/main 对照实验确认为既有基线失败）；真实 PDF 端到端走查归入 T051（主工作区 G1）；`mvn test` 全绿后原子提交

**Checkpoint**: G3 通过——无文件不核查；已上传文件的项目核查输入为真实文件解析结果（此时 25 项判定仍是旧规则，允许，US5 才替换）

---

## Phase 4: User Story 2 — 真实招标文件 + LLM 结构化提取（第二步，Priority: P2）

**Goal**: 核查依据来自真实招标文件的 AI 结构化提取（评分项/硬性要求/否决条款/组成要求/格式要求），替代 Tender.description 与 ISO27001/ISO9001/CMMI 硬编码

**Independent Test**: 真实招标 PDF 提取出非空结构化要求（评分项/否决条款至少各一）；招标未上传时比对项 MANUAL + REFERENCE_MISSING、独立项照常

### Tests for US2 ⚠️

- [X] T025 [P] [US2] 提取契约测试（contracts/tender-requirements-extraction.md §4 四例：正常提取/无章节不臆造/非法 JSON 得 missing/Noop 得 missing），新建 backend/src/test/java/com/xiyu/bid/compliance/domain/TenderRequirementExtractionTest.java

### Implementation for US2

- [X] T026 [US2] 新建纯核心 record `TenderRequirements`（含 ScoringItem、VetoClause 嵌套与 `missing()` 哨兵，data-model.md §3.1），文件 backend/src/main/java/com/xiyu/bid/compliance/domain/policy/TenderRequirements.java
- [X] T027 [US2] 重构 `ComplianceTargetLoader.loadTenderDocument`：复用 `scoreparse/application/InitiationTenderTextResolver.findLatestTenderDocument` 定位招标文件 → 走 T019 相同存储分派 + Sidecar 解析；随后删除 `loadProjectTenderText`（grep 确认无引用）
- [X] T028 [US2] LLM 结构化提取：经 `RoutingAiProvider` 输出 contracts/tender-requirements-extraction.md §2 的 JSON → 解析为 `TenderRequirements`；集成进 `BidQualityCheckAsyncExecutor` PARSING 阶段，并删除 `extractRequiredCerts` 硬编码方法（backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java）
- [X] T029 [US2] 降级分支：招标未上传/提取失败/AI 未启用 → `TenderRequirements.missing()`，比对项 MANUAL + `[REFERENCE_MISSING]`、独立项照常；失败 `log.error` 打堆栈（Constitution VII §3）
- [X] T030 [US2] `mvn test` 全绿后原子提交（附提取契约测试证据）

**Checkpoint**: 核查依据切换为真实招标文件；报告 TENDER 类证据引用招标 PDF 原文而非 Tender.description

---

## Phase 5: User Story 3 — 知识库真实查询（第三步，Priority: P3）

**Goal**: 四库（资质/品牌授权/人员/业绩）全部真实数据匹配，每条 KNOWLEDGE_BASE 证据可回答"来自哪里"（source + recordId + content + match）

**Independent Test**: 四库各至少一条真实数据匹配的集成测试；空库/异常分支单测；全仓 grep 固定文案证据零命中

### Tests for US3 ⚠️

- [X] T031 [P] [US3] 四库真实匹配集成测试（每库至少一条真实记录命中）+ 空库/查询异常 → MANUAL 分支单测，新建 backend/src/test/java/com/xiyu/bid/compliance/service/KnowledgeBaseRealQueryTest.java

### Implementation for US3

- [X] T032 [US3] 新建纯核心 record `KnowledgeMatch`（knowledgeSource/recordId/content/matched，data-model.md §3.2），文件 backend/src/main/java/com/xiyu/bid/compliance/domain/policy/KnowledgeMatch.java
- [X] T033 [US3] `KnowledgeBaseOnDemandLoader`（backend/src/main/java/com/xiyu/bid/compliance/service/KnowledgeBaseOnDemandLoader.java）资质库：保留真查询并输出 `KnowledgeMatch` 结构化结果
- [X] T034 [US3] 同文件品牌授权库：`BrandAuthorizationRepository` 真实查询授权记录（品牌、期限、范围），替换固定文案
- [X] T035 [US3] 同文件人员库：`PersonnelRepository` 真实查询人员及资质证书，替换固定文案
- [X] T036 [US3] 同文件业绩库：移除误注入的 `WarehouseRepository`，替换为 performance 模块 `PerformanceRepository`（`PerformanceRecordEntity`）真实业绩查询
- [X] T037 [US3] 删除"已对照企业专家与项目经理库"、"已检索品牌制造商授权与代理证书"等固定文案证据；单库超时/异常 → 依赖该库检查项置 MANUAL 不阻断其他项（PRD §5.1）
- [X] T038 [US3] `mvn test` 全绿后原子提交（附四库集成测试证据）

**Checkpoint**: KNOWLEDGE_BASE 类证据全部含真实 recordId 且能在对应库中找到记录；固定文案证据零残留

---

## Phase 6: User Story 4 — Evidence 真实性（第四步，Priority: P4）

**Goal**: 所有 BID/TENDER 证据的 page 来自 PageContent 真实定位（无法定位为 null，禁止填 1）、quote 为真实原文摘录

**Independent Test**: 给定已知内容测试文档，断言 evidence.page 等于预期页码、quote 为文档原文子串；grep 硬编码页码零命中

### Tests for US4 ⚠️

- [X] T039 [P] [US4] `EvidenceLocator` 纯函数单测：命中返回真实 pageNumber + quote（为 PageContent.text 子串）、未命中返回 Optional.empty()、quote 长度上限截断，新建 backend/src/test/java/com/xiyu/bid/compliance/domain/EvidenceLocatorTest.java

### Implementation for US4

- [X] T040 [US4] 新建纯核心 `EvidenceLocator`（输入 pages + 关键词/章节线索 → `EvidenceHit(pageNumber, quote)`，data-model.md §3.3），文件 backend/src/main/java/com/xiyu/bid/compliance/domain/policy/EvidenceLocator.java
- [X] T041 [US4] 扩展 `QualityEvidence` record：新增可空字段 `knowledgeSource`（四库枚举）与 `matched`（Boolean），compact constructor 校验，保持旧 JSON 反序列化兼容（backend/src/main/java/com/xiyu/bid/compliance/domain/QualityEvidence.java，data-model.md §2.1）
- [X] T042 [US4] `BidDocumentQualityEngine`（backend/src/main/java/com/xiyu/bid/compliance/domain/policy/BidDocumentQualityEngine.java）证据生成改造：page 全部经 EvidenceLocator 定位（未命中传 null），quote 为命中上下文原文摘录；KnowledgeMatch 映射新字段
- [X] T043 [US4] 前端 src/views/Project/stages/components/QualityCheckItemCard.vue 证据链面板渲染 `knowledgeSource` 标签与 `matched` 结论
- [X] T044 [US4] `mvn test` + `npm run build` 全绿后原子提交

**Checkpoint**: 抽查任意 FAIL/MANUAL 项，页码与原文可与真实 PDF 逐页对照；page=1 硬编码零残留

---

## Phase 7: User Story 5 — LLM 混合裁决（第五步，Priority: P5）

**Goal**: 25 项判定由"确定性规则 lane + LLM lane"混合完成，替代纯 String.contains()；LLM 只做裁决层不做全量直出

**Independent Test**: 确定性规则项不依赖 LLM 的单测保持全绿；LLM 项契约测试固定输入输出结构；LLM 失败/Noop 时语义项 MANUAL 且任务不 FAILED

### Tests for US5 ⚠️

- [X] T045 [P] [US5] LLM 裁决契约测试：固定输入（TenderRequirements + KnowledgeMatch + Evidence）→ 固定输出结构（PASS/FAIL/MANUAL + reasonCode + evidence）；单项失败 → MANUAL + LOW_CONFIDENCE；Noop → 语义项全 MANUAL，新建 backend/src/test/java/com/xiyu/bid/compliance/domain/LlmAdjudicationContractTest.java

### Implementation for US5

- [X] T046 [US5] `BidDocumentQualityEngine` 拆分（Constitution IV 300 行硬上限 + plan Complexity Tracking）：规则 lane、LLM lane、证据组装各自成类，落于 backend/src/main/java/com/xiyu/bid/compliance/domain/policy/ 下（每文件 ≤300 行，wc -l 自查）
- [X] T047 [US5] LLM lane 实现：语义项（responsive_clause 类、veto_keyword 二阶段、scoring_response、clarification_resp 等）输入 = T028 的 TenderRequirements + T033-T036 的 KnowledgeMatch + T040 的 Evidence，经 `RoutingAiProvider` 裁决 → PASS/FAIL/MANUAL + reason（reasonCode ∈ PRD §3.5 五值集合）+ evidence
- [X] T048 [US5] 进度上报：LLM lane 经 `ProgressReporter` 回调更新任务 progress 与 currentStep（参照 scoreparse OpenAiScoreAnalyzer 召回 lane 范式），backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java 接线
- [X] T049 [US5] 失败分支：单项 LLM 失败置 MANUAL + LOW_CONFIDENCE 不阻断整体；AI 未启用（Noop）时语义项全部 MANUAL 并注明原因；任务保持 COMPLETED
- [X] T050 [US5] `mvn test`（含 ArchitectureTest）全绿 + 拆分后 wc -l 全部 ≤300 后原子提交

**Checkpoint**: 25 项中确定性项毫秒级出结果、语义项由真实 LLM 裁决；全链路 = 真实文件 + 真实知识库 + 真实 AI

---

## Phase 8: Polish & Cross-Cutting（验收门禁收尾）

**Purpose**: spec §6 G1-G6 门禁逐项收口

- [ ] T051 G1 唯一主线人工走查（quickstart.md §3）：主工作区真实投标 PDF + 真实招标 PDF 端到端，抽 3 个 FAIL/MANUAL 项逐项对照页码/原文/招标要求/知识库记录
- [X] T052 [P] G5 验证（quickstart.md §5）：grep 固定文案/isDemoMode/硬编码页码零命中（两条固定文案 0 命中；compliance 包硬编码页码 0 命中；isDemoMode 命中均为项目详情页 demo 脚手架恒 false，与质量核查链路无关）
- [X] T053 G4 全门禁：`cd backend && mvn test`（含 ArchitectureTest）+ `npm run check:front-data-boundaries && npm run check:line-budgets && npm run build` 全绿（mvn 全量失败集合与基线 24 类一致，见 T050；前端三项全过，build exit=0）
- [ ] T054 G6 存储口径：向产品确认 spec §5.2 方案 A/B，结论写入 spec 决策段；方案 A 则另开任务（阻塞合入，不阻塞本分支完成）
- [X] T055 知识沉淀：按收尾流程调用 knowledge-capture，追加 docs/lessons/lessons-learned.md（047 验收失真根因 + 双链路教训），commit 进当前分支（§135 验收失真复盘 + §136 MatchException FP 门禁踩坑）
- [ ] T056 push 分支 + 走收尾流程（scripts/agent-finish-task.sh，PR 描述声明 Constitution 合规情况）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2, 第零步)**: 依赖 Setup——**BLOCKS 所有 US**（spec §3 验收阻塞项）
- **US1 (Phase 3)**: 依赖 Foundational
- **US2 (Phase 4)**: 依赖 US1（复用 T019 的存储分派 + Sidecar 解析管道）
- **US3 (Phase 5)**: 依赖 Foundational；证据结构升级（T041）在 US4，但 US3 的 KnowledgeMatch 产出是 US4/US5 输入——按序执行最稳
- **US4 (Phase 6)**: 依赖 US1 + US2 + US3（真实 PageContent + 知识库数据齐全后证据才可真实）
- **US5 (Phase 7)**: 依赖 US2 + US3 + US4（LLM lane 输入 = 三者产物）
- **Polish (Phase 8)**: 依赖全部 US 完成

### User Story Dependencies

- **US1 (P1)**: MVP 起点，完成后独立可验证（G3）
- **US2 (P2)**: 复用 US1 解析管道；招标缺失分支独立可测
- **US3 (P3)**: 与 US2 无代码依赖，理论上可并行；但两者都改 `KnowledgeBaseOnDemandLoader`/`AsyncExecutor` 周边，单人顺序执行避免冲突
- **US4 (P4)**: 必须等 US1-US3 的真实数据源就位
- **US5 (P5)**: 最后接入，禁止提前引入 LLM 直出

### Within Each User Story

- 测试先写并确保 FAIL（Constitution III）→ 实现 → 全绿 → 原子提交（附测试证据，RELIABILITY.md 每变必测）

### Parallel Opportunities

- Phase 2：T007/T010 可与相邻任务并行（不同文件）
- 各 US 的测试任务（T017/T018、T025、T031、T039、T045）可并行编写
- US3 的 T033-T036 四库改造同文件，禁止并行；US3 整体可与 US2 并行（若双人）
- Polish 的 T052 可与 T051 并行

---

## Parallel Example: User Story 1

```bash
# US1 测试先行（不同测试文件，可并行）：
Task: "存储分派单测 in backend/src/test/java/com/xiyu/bid/compliance/service/ComplianceTargetLoaderStorageDispatchTest.java"
Task: "trigger 前置校验单测 in backend/src/test/java/com/xiyu/bid/compliance/application/BidQualityCheckTriggerPreconditionTest.java"
```

---

## Implementation Strategy

### MVP First（US1 Only）

1. Phase 1 Setup → Phase 2 Foundational（第零步删除，独立可提交）
2. Phase 3 US1 真实投标文件
3. **STOP and VALIDATE**: G3 验证（无文件 4xx + 真实 PDF 出 PageContent[]）
4. 此时系统已"读真实投标文件"，是第一个可演示的实质增量

### Incremental Delivery

1. Setup + Foundational → 双链路合并（可独立合入，验收阻塞项先解除）
2. US1 → 真实投标文件 → 验证 G3
3. US2 → 真实招标依据 → 验证提取契约
4. US3 → 四库真实证据 → 验证 recordId 可回查
5. US4 → 页码/原文真实 → 抽查对照 PDF
6. US5 → LLM 裁决 → G1 唯一主线走查（最终验收）
7. 每步独立提交、独立可测，任何检查点可停

### 单 Agent 串行策略（本项目实际形态）

本仓库多 Agent worktree 协作、单人执行为主：严格按 T001 → T056 顺序推进，每步原子提交 + 测试证据；跨 worktree 冲突由 T002 Lease 检查兜底。

---

## Notes

- [P] 任务 = 不同文件、无依赖
- 每个 US 可独立完成与验证（Independent Test 已给出）
- 测试必须先 FAIL 再实现（Constitution III）
- 每任务或逻辑组提交一次，提交附测试证据（RELIABILITY.md）
- 禁止：范围蔓延（spec §4.1 排除项——25 项编码命名风格、autoScore 跳转、存储口径切换另开任务）、fallback 降级、固定文案证据、LLM 全量直出
- G6 存储口径未定案前不合入 main
