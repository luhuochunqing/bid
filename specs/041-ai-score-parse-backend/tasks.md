# Tasks: AI 评分标准解析 — 后端服务

**Input**: Design documents from `/specs/041-ai-score-parse-backend/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Constitution III 强制 TDD（Red→Green→Refactor）——每个 User Story 先写失败测试再实现。

**Organization**: 按 User Story 分组（US1 解析 P1 / US2 五类匹配 P1 / US3 预计得分 P1 / US4 实际打分 P2 / US5 触发控制与超时 P2）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属 User Story
- 所有路径相对仓库根

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 任务分支基础设施与资源预订

- [ ] T001 对 `db/migration-mysql` 与 `db/rollback/migration-mysql` acquire agent lock（`npm run agent:lock-acquire -- --path backend/src/main/resources/db/migration-mysql --scope directory --reason "score-parse V1187/V1188"`，rollback 同理）
- [ ] T002 用 `bash scripts/new-migration.sh` 预约并创建两个迁移占位：V1187（score 三表）+ V1188（performance contract_amount），生成对应 U1187/U1188 回滚占位

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 三表 schema、实体、仓库、异步执行器、任务状态机——所有 US 的公共地基

- [ ] T003 编写 V1187 迁移 SQL：`score_parse_task` / `score_item` / `score_result`（字段见 data-model.md；含索引 project_id、task_id UK、score_item_id UK）+ U1187 回滚（DROP 三表，反序）
- [ ] T004 编写 V1188 迁移 SQL：`performance_record` 加 `contract_amount DECIMAL(15,2) NULL` + U1188 回滚（DROP COLUMN）
- [ ] T005 [P] 创建实体 `ScoreParseTask` / `ScoreItem` / `ScoreResult` 于 `backend/src/main/java/com/xiyu/bid/scoreparse/entity/`（含 @PrePersist/@PreUpdate 时间戳，字段与迁移一致）
- [ ] T006 [P] 创建 Repository 三接口于 `backend/src/main/java/com/xiyu/bid/scoreparse/repository/`（ScoreParseTaskRepository 含 `findByTaskId`、`findByProjectIdAndTaskTypeAndStatusIn`、`findByStatusAndUpdatedAtBefore`）
- [ ] T007 在 `backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java` 新增 `scoreParseExecutor` bean（core=1/max=2/queue=20，createExecutor 挂 MdcTaskDecorator）
- [ ] T008 [P] 实现 `ScoreParseTaskStateService`（`scoreparse/application/`）：createTask/markProcessing/markCompleted/markFailed + 三层降级 failTask（spec 031 范式，每方法独立 @Transactional）
- [ ] T009 [P] 实现 `ScoreParseProgressService`（`scoreparse/application/`）：`Optional<StringRedisTemplate>` 降级，key `score:{parse|scoring}:progress:{taskId}` TTL 7d，DB fallback
- [ ] T010 [P] 创建 domain 基础 record `ScoreCandidate`（`scoreparse/domain/`）：code/dim/detail/weight/scoreTypeGuess/contextNote/sourceText/location/semanticPattern

**Checkpoint**: `mvn test -Dtest=ArchitectureTest` 全绿；迁移在本地库执行成功

---

## Phase 3: User Story 1 - 招标文件评分标准解析 (Priority: P1) 🎯 MVP

**Goal**: 上传招标文件 → 自动触发异步解析 → 四路召回 + LLM 结构化提取 + 合并去重 + 闭环校验 → score_item 持久化 → REST 查询

**Independent Test**: quickstart.md §3——上传含评分表样例，轮询至 COMPLETED，items 数量/编号/权重与人工核对一致

### Tests for US1（先写，确认 FAIL）

- [ ] T011 [P] [US1] `ScoreItemMergePolicyTest`（`backend/src/test/java/com/xiyu/bid/scoreparse/domain/`）：重复识别合并、语义相似不误删、编号重复保留首次
- [ ] T012 [P] [US1] `ScoreTypeClassificationPolicyTest`：量化条件→客观、描述性→主观、报价类→主观
- [ ] T013 [P] [US1] `WeightSumCheckTest` + `ItemCountCheckTest`：合计≠100 触发二次解析标记、0 项判定失败
- [ ] T014 [P] [US1] `MarkdownScoreSectionLocatorTest`：表格/标题层级候选区域、前后文保留

### Implementation for US1

- [ ] T015 [US1] 实现 domain：`ScoreItemMergePolicy` / `ScoreTypeClassificationPolicy` / `WeightSumCheck` / `ItemCountCheck`（让 T011-T013 转 GREEN）
- [ ] T016 [US1] 实现 `MarkdownScoreSectionLocator`（`scoreparse/infrastructure/structure/`，纯静态方法）
- [ ] T017 [US1] 实现 `ScoreParsePrompts` + `ScoreCandidateOutput` + `ScoreGapRecheckOutput`（`scoreparse/infrastructure/openai/`，契约见 contracts/llm-output-schema.md §1/§2/§3）
- [ ] T018 [US1] 实现 `OpenAiScoreAnalyzer`：召回一（复用 RegexKeywordMatcher/ScoringItemExtractor）+ 召回二（T016）+ 召回三/四（chunk 多轮 LLM，复用 `OpenAiStructuredOutputService`，全量内容过 sanitizeUntrusted）
- [ ] T019 [US1] 实现 `ScoreParseAppService`：trigger（互斥校验）+ `@Async("scoreParseExecutor")` executeParse（@Lazy self 代理）+ 编排 T15/T16/T18 → 合并 → 校验 → 差异触发二次/回补 → 落 score_item（0 项→FAILED）
- [ ] T020 [US1] `BidTenderDocumentImportAppService` 保存成功后 publishEvent `TenderDocumentStoredEvent`（+1 行 + 事件 record 于 `biddraftagent/application/`）
- [ ] T021 [US1] 实现 `TenderDocumentStoredListener`（`scoreparse/application/`，@Async @EventListener 消费 → trigger，进行中则 log.info 跳过）
- [ ] T022 [US1] 实现 `ScoreParseController`（`scoreparse/controller/`）：POST /parse、GET /parse/status、GET /items（+summary 聚合，weightWarning）；类级 isAuthenticated + Service 层 assertCurrentUserCanAccessProject

**Checkpoint**: US1 独立可测（quickstart §3）

---

## Phase 4: User Story 2 - 知识库五类匹配查询 (Priority: P1)

**Goal**: 五个确定性 match 接口直连现有知识库存储，输出 tier/matchRatio/matched

**Independent Test**: quickstart.md §2——预置数据后 curl 五接口，tier/ratio 与预置一致；空数据 NONE 不抛错

### Tests for US2（先写，确认 FAIL）

- [ ] T023 [P] [US2] `CertMatchServiceTest`：完全/部分/未匹配、过期证书 expired=true、等级忽略时放行
- [ ] T024 [P] [US2] `PersonMatchServiceTest`：比例计算（3/5）、证书子表 deleted_at 过滤、单人多证计一次
- [ ] T025 [P] [US2] `ProjectMatchServiceTest`：数量比例、contract_amount NULL 跳过金额比对不失配
- [ ] T026 [P] [US2] `WarehouseMatchServiceTest` + `BrandMatchServiceTest`：状态过滤、降级匹配标注、expireSoon 标记

### Implementation for US2

- [ ] T027 [US2] 实现五个 match AppService（`scoreparse/application/match/`）：Specification 动态查询 + tier/ratio 计算（mock repository 让 T023-T026 GREEN；契约见 contracts/knowledge-match-api.md）
- [ ] T028 [US2] 实现 `KnowledgeMatchController`：POST /api/knowledge/{cert|person|project|warehouse|brand}/match，isAuthenticated

**Checkpoint**: US2 独立可测（quickstart §2，SC-005 计时）

---

## Phase 5: User Story 3 - 阶段 1 预计得分计算 (Priority: P1)

**Goal**: 评分项 × 知识库匹配 → 预计得分/状态/依据/kbHit 回填 score_item

**Independent Test**: quickstart.md §1 纯核心单测 + 固定数据下 est_score 断言（spec US3 六场景）

### Tests for US3（先写，确认 FAIL）

- [ ] T029 [P] [US3] `PartialScorePolicyTest`：四舍五入、开区间 (0,weight)、主观项 null 零泄漏（SC-003）
- [ ] T030 [P] [US3] `ScoreStatusPolicyTest` + `ScoreRangeGuardTest`：满分 OK/零分 DANGER/部分与过期 PENDING、超区间置 null+PENDING
- [ ] T031 [P] [US3] `SummaryAggregatorTest`：合计排除主观项、objective/subjectiveWeight、weightWarning

### Implementation for US3

- [ ] T032 [US3] 实现 domain：`PartialScorePolicy` / `ScoreStatusPolicy` / `ScoreRangeGuard` / `SummaryAggregator`（T029-T031 GREEN）
- [ ] T033 [US3] 实现 `EstimatedScoreService`（`scoreparse/application/`）：解析完成后按 scoreTypeGuess 分型调用 T27 五 match → 策略计算 → 回填 est_score/status_stage1/est_basis/kb_hit（主观项强制 null）；挂入 US1 编排链尾
- [ ] T034 [US3] GET /items 响应接入 SummaryAggregator 输出（含 weightWarning）

**Checkpoint**: US1+US2+US3 串联 = 阶段 1 完整链路

---

## Phase 6: User Story 4 - 阶段 2 投标文件实际打分 (Priority: P2)

**Goal**: 上传投标文件 → 前置校验 → 异步 LLM 对标打分 → score_result 持久化 → REST 查询

**Independent Test**: quickstart.md §4——未传标书 400、传后打分、客观项 [0,weight]、主观项 null+suggestion

### Tests for US4（先写，确认 FAIL）

- [ ] T035 [P] [US4] `ScoreAssessmentGuardTest`：actualScore 超区间置 null、主观项数字丢弃、quoteMissing→null
- [ ] T036 [P] [US4] `ScoreScoringAppServiceTest`：前置条件（NO_BID_DOCUMENT/SCORE_ITEMS_NOT_READY/TASK_IN_PROGRESS）、整批覆盖、失败保留旧结果

### Implementation for US4

- [ ] T037 [US4] 实现 `ScoreAssessmentOutput` + 打分 prompt（`ScoreParsePrompts` 增阶段 2 模板，契约 §4）
- [ ] T038 [US4] `OpenAiScoreAnalyzer` 增 assess 方法（每评分项一次调用 + ScoreRangeGuard/SubjectiveScoreGuard 守卫）
- [ ] T039 [US4] 实现 `ScoreScoringAppService`：前置校验 → @Async executeScoring → 逐项 assess → 事务内整批写 score_result（T036 GREEN）
- [ ] T040 [US4] `ScoreParseController` 增：POST /bid-documents（multipart，PDF/docx，50MB 校验，category=bid-file）、POST /scoring、GET /scoring/status、GET /results（不含 kbHit，FR-018）

**Checkpoint**: 阶段 2 完整链路（quickstart §4）

---

## Phase 7: User Story 5 - 触发控制与超时保护 (Priority: P2)

**Goal**: 30 分钟超时扫描、启动恢复、重新解析/重新打分覆盖语义

**Independent Test**: quickstart.md §6——缩短时钟模拟挂起任务被置 FAILED、旧结果仍可查

### Tests for US5（先写，确认 FAIL）

- [ ] T041 [P] [US5] `ScoreParseTimeoutScanJobTest`：PROCESSING 超 30min → FAILED + timeout_marked=1、COMPLETED 不受影响
- [ ] T042 [P] [US5] `ScoreParseTaskRecoveryRunnerTest`：启动时卡死任务恢复
- [ ] T043 [P] [US5] 重新解析覆盖测试：评分项变化后旧 score_result 失效标记、重新打分仅覆盖打分结果

### Implementation for US5

- [ ] T044 [US5] 实现 `ScoreParseTimeoutScanJob`（`scoreparse/infrastructure/scheduler/`，@Scheduled fixedDelay，阈值 `app.score-parse.timeout-minutes:30` 可注入）
- [ ] T045 [US5] 实现 `ScoreParseTaskRecoveryRunner`（`scoreparse/infrastructure/bootstrap/`，ApplicationRunner @Order(30)，复用 failTask 三层降级）
- [ ] T046 [US5] US1 编排链补齐 FR-021 覆盖语义（重新解析 DELETE 旧 items+results 并标记、重新打分整批替换）

**Checkpoint**: 全部 User Story 完成

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T047 运行 `mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest` + 全量 `mvn test`
- [ ] T048 [P] 更新 `docs/generated/db-schema.md`（`npm run db:generate-schema`）+ 实现笔记 `docs/implementation-notes/`
- [ ] T049 释放 agent lock（`npm run agent:lock-release`）+ 知识沉淀（knowledge-capture）+ wiki checkpoint
- [ ] T050 push 分支 + Gitee PR（描述含 Constitution 合规声明 + quickstart 验证结果）

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2（迁移需先预约版本号与 lock）
- Phase 2 阻塞全部 US（实体/仓库/状态机是公共地基）
- US1 与 US2 可并行（不同文件）→ US3 依赖 US1+US2 → US4 依赖 US1（可先行于 US3 并行开发）→ US5 依赖 US1/US4 的任务机制
- Phase 8 收尾

### Parallel Opportunities

- T005/T006/T008/T009/T010 全部 [P]
- US1 的 T011-T014 测试四路并行
- US2 的 T023-T026 五路并行；US2 整体可与 US1 并行
- US3 的 T029-T031、US4 的 T035-T036、US5 的 T041-T043 各自并行

### MVP Scope

US1（解析）+ US2（匹配）+ US3（预计得分）= 阶段 1 完整价值；最小演示可仅 US1。
