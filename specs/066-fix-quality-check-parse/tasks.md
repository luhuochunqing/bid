# Tasks: AI 标书质量核查解析失败误报修复（大文件超时预算与失败语义分类）

**Input**: Design documents from `/specs/066-fix-quality-check-parse/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/parse-outcome-contract.md, quickstart.md
**Tests**: 必须（Constitution III TDD NON-NEGOTIABLE；RED → GREEN）

**Organization**: 按用户故事组织，每个故事独立可测；[P] 表示可并行（不同文件、无未完成依赖）。

## Phase 1: Setup

- [x] T001 任务分支与隔离 worktree：`agent-start-task.sh qoder fix-quality-check-parse-failure origin/main`（3 目录锁已登记，who-touches 预检通过）
- [x] T002 Entry Gate 工件落盘（`.omc/entry-gate-qoder-quality-parse.md` + 分支名副本），含 S3 评审修订
- [x] T003 规格/计划/研究/数据模型/契约落盘（`specs/066-fix-quality-check-parse/`）
- [x] T004 捕获验证基线：`cd backend && mvn -q test -Dtest='SidecarStreamDocumentAdapterTest,ComplianceTargetLoaderStorageDispatchTest,TenderDocumentLoaderTest,BidQualityCheckAsyncExecutorTest'` 记录当前通过数；`cd document-converter-sidecar && python -m pytest tests/ -q` 记录基线（用于 S6 health 对比）

## Phase 2: Foundational（阻塞所有故事）

- [x] T005 [P] 写 RED 单测 `backend/src/test/java/com/xiyu/bid/compliance/domain/SidecarTimeoutBudgetTest.java`：50MB 边界、51MB→base×5+3、165/441/565MB 期望值、1800s 封顶、未知大小→base×5、单调性
- [x] T006 [P] 写 RED 单测（结果对象部分）追加到 `backend/src/test/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapterTest.java`：五分类构造与不变量（非 OK 时 content 空）
- [x] T007 实现 `backend/src/main/java/com/xiyu/bid/compliance/domain/SidecarTimeoutBudget.java`（纯函数，record/final，无框架依赖）使 T005 转绿
- [x] T008 实现 `backend/src/main/java/com/xiyu/bid/compliance/domain/SidecarParseOutcome.java`（record + Kind 枚举 + 工厂方法）使 T006 转绿

## Phase 3: User Story 1 - 大文件文字型标书质量核查可完成 (P1)

**Goal**: 165/441/565MB 文字型文档在生效上限内完成解析并产出核查报告
**Independent Test**: 适配器以原始大小取档；Sidecar 分块 100 页后 441MB 解析 ≤600s；同文件重复触发上限恒定

- [x] T009 [US1] RED：`SidecarStreamDocumentAdapterTest` 增加档位来源锚点用例（`limitSecondsFor(原始路径)` 稀疏 60MB→大文件档、缺失→下限；真实 565MB docx 瘦身场景留待 T032 真机复跑）
- [x] T010 [US1] RED：`SidecarStreamDocumentAdapterTest` 增加「确定性」用例（同文件两次调用上限一致）与「未知大小走大文件档」
- [x] T011 [US1] 适配器拆分：将 Jackson 负载解析（`documentFromSidecarPayload`/`buildDocumentContent`/虚拟分页/纯文本本地降级）外移至 NEW `backend/src/main/java/com/xiyu/bid/compliance/service/SidecarPayloadParser.java`，适配器目标 ≤200 行（硬限 300）
- [x] T012 [US1] 适配器接入 `SidecarTimeoutBudget`：按**原始文件大小**（瘦身前 `Files.size(filePath)`，探测失败传 -1）取上限，记录 kind/大小/上限/耗时日志；T009/T010 转绿
- [x] T013 [US1] RED：`document-converter-sidecar/tests/test_converter_pdf.py` 增加分块默认值断言（`SIDECAR_PDF_CHUNK_PAGES` 未设 → 100）与跨块页完整性（多块 PDF 页号/文本无缺失）
- [x] T014 [US1] 实现 `document-converter-sidecar/converter.py` `_pdf_chunk_pages()` 默认 10 → 100（保留 env 覆盖）；T013 转绿
- [x] T015 [US1] 重新标定 `document-converter-sidecar/tests/test_pdf_memory_guard.py`：先实测新分块峰值，再按 O(chunk) 语义设定预算并保留"字节一致"断言；保持 1373 OOM 回归守卫有效
- [x] T016 [US1] `backend/src/main/java/com/xiyu/bid/compliance/service/TenderDocumentLoader.java` 适配新返回类型：非 OK → 空文档 + `log.warn`（招标链路容忍降级，不抛错）；更新 `TenderDocumentLoaderTest` 覆盖该语义

## Phase 4: User Story 2 - 失败原因准确且可操作 (P1)

**Goal**: 超时/服务异常/无文本/不可解析四类文案互异且可操作；「确认文件可正常打开」仅用于 UNPARSEABLE/存储缺失
**Independent Test**: 契约表逐行断言（contracts/parse-outcome-contract.md R1-R13）

- [x] T017 [US2] RED：`SidecarStreamDocumentAdapterTest` 增加分类边界用例：连接拒绝→SERVICE_ERROR、读超时→TIMEOUT、5xx→SERVICE_ERROR、401/413→SERVICE_ERROR、400/422→UNPARSEABLE、200 非法 JSON/空响应→SERVICE_ERROR、200 空文本→EMPTY_TEXT、200 合法短文本+pages→OK（连接建立超时由异常类型优先级保障，10s 连接超时无法在单测快速触发，留待真机观察）
- [x] T018 [US2] 适配器实现分类逻辑（异常类型/状态码/JSON 解析结果）使 T017 转绿；413 detail 标记超上限
- [x] T019 [US2] RED：`ComplianceTargetLoaderStorageDispatchTest` 增加文案断言：TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE 四类 error_message 互异，前三类不含「重新上传」，且均含失败册文件名（多册场景）
- [x] T020 [US2] `ComplianceTargetLoader` 按 kind 生成 BusinessException 文案（复用契约模板，指名失败册）；T019 转绿
- [x] T021 [US2] `BidQualityCheckAsyncExecutor` 空文档兜底路径文案对齐分类语义；更新 `BidQualityCheckAsyncExecutorTest`
- [x] T022 [US2] `document-converter-sidecar/app.py` 增加单次转换结构化日志（`file/size/duration_ms/chars/pages/warnings`，空文本 WARN）；`tests/test_app.py` 增加日志断言（RED→GREEN）

## Phase 5: User Story 3 - 图片扫描件明确提示不支持 (P2)

**Goal**: 580 形态（200 + 空文本 + low_text_density）给出「图片扫描件/无文本层不支持」文案，不引导重传
**Independent Test**: 适配器返回 EMPTY_TEXT，detail 含 low_text_density；loader 文案含「图片扫描件/无文本层」且不含「确认文件可正常打开」

- [x] T023 [US3] RED：适配器用例「200 + markdown 空 + pages 空 + warnings=[low_text_density] → EMPTY_TEXT（detail 含警告）」与「200 + markdown 空 + pages 有文本 → OK」（防误杀）
- [x] T024 [US3] 适配器实现 EMPTY_TEXT 判据（markdown 空且 pages 全空；warnings 仅补充）使 T023 转绿
- [x] T025 [US3] RED+实现：`ComplianceTargetLoaderStorageDispatchTest` 断言扫描件文案模板（契约 R2/EMPTY_TEXT 行）与「不含重传引导」

## Phase 6: User Story 4 - 长任务等待体验与真实进度一致 (P2)

**Goal**: 前端窗口 ≥30 分钟；到顶进入「后台执行中」态（不误报失败、不落入空白态）；重开恢复进度
**Independent Test**: vitest 该 spec + 模板分支断言

- [x] T026 [US4] RED：`src/views/Project/stages/composables/useQualityCheckDrawer.spec.js` 增加「窗口到顶 → 不设 errorMessage、backgroundRunning=true」「窗口内完成 → 加载报告」「重开恢复轮询」用例
- [x] T027 [US4] `useQualityCheckDrawer.js` 实现：`MAX_POLL_TIMEOUT_MS` → 30 分钟；新增 `backgroundRunning` 状态与到顶处理（清 polling、不设 error、保留刷新入口）；T026 转绿
- [x] T028 [US4] `src/views/Project/stages/components/QualityCheckDrawer.vue` 增加后台执行态模板分支（位于 report 与 error 之间，防落入「尚未执行」空白态），展示提示与刷新按钮
- [x] T029 [US4] 更新抽屉相关既有测试（`useQualityCheckDrawer.spec.js` 超时用例语义迁移）并全绿

## Phase 7: Polish & Cross-Cutting

- [x] T030 S4 收尾清理：`ai-slop-cleaner` 模式 B（deletion-first）扫描本次改动，删除死代码/冗余 wrapper；确认适配器 ≤200 行、无新增重复逻辑
- [x] T031 S5 验证：`cd backend && mvn -q test`（完整）+ `cd document-converter-sidecar && python -m pytest tests/ -v` + `npx vitest run` 相关 spec + `npm run build` + `npm run check:line-budgets` + `git diff --check`；产出 `.omc/verify-qoder-quality-parse.md`（含 ai-slop-cleaner 记录与命令 exit code）
- [ ] T032 S5 真实环境复跑（需部署授权）：测试环境对 22/35/46/580 触发质量核查，按 quickstart.md §4 断言（46 解析 ≤600s；580 文案为扫描件不支持）
- [ ] T033 S6 评审：`health`（对比 T004 基线）+ `xiyu-code-review`/`google-code-review`（三透镜）+ `code-review`（spec 一致性）+ `production-risk-review`（生产风险）；`--record-review` 落盘 `.omc/review-qoder-quality-parse.md`（MERGE_READY）
- [ ] T034 S7 交付：提交/推送（**PR 需用户显式授权**）；按 RELIABILITY.md 走 PR 门禁
- [ ] T035 S8 沉淀：`knowledge-capture` 回填 `docs/lessons/lessons-learned.md`（超时预算×规模、失败语义分类、测试不测根因教训）+ 技术债登记（解析缓存/Worker 饥饿）+ wiki checkpoint

## Dependencies

- Phase 2 阻塞全部故事（T005-T008 先行）
- US1（T009-T016）与 US2（T017-T022）共享适配器文件，需串行；US2 的分类是 US3（T023-T025）前置
- US4（T026-T029）独立于后端，可并行
- Polish 依赖全部故事完成；T032 依赖部署授权；T033 依赖 T031 证据

## Parallel Execution Examples

- T005/T006 可并行（不同测试文件）
- US4 全阶段（T026-T029）可与 Phase 3/4/5 并行（前端文件独立）
- T015（sidecar 内存标定）与 T016（TenderDocumentLoader）可并行

## Implementation Strategy

MVP = Phase 2 + US1 + US2（对应生产 22/35/46 直接故障）；US3/US4 紧随（能力边界与体验）；Polish 全量回归后走 S6/S7。
