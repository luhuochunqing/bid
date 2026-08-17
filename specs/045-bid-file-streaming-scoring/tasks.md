# Tasks: 投标文件流式落盘与文本量设限打分

**Input**: Design documents from `/specs/045-bid-file-streaming-scoring/`

**Prerequisites**: plan.md ✅, spec.md ✅（FR-010/011 已按 plan 修正回写）, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: Constitution III 强制 TDD（NON-NEGOTIABLE）——所有新组件先写失败测试再实现。

**Organization**: 按 spec 4 个 User Story 分组（US1/US2 P0，US3 P1，US4 P2），支持独立实现与独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 归属 User Story
- 所有路径相对仓库根目录

## Path Conventions

- 后端：`backend/src/main/java/com/xiyu/bid/scoreparse/`（下文简写 `scoreparse/`）
- 后端测试：`backend/src/test/java/com/xiyu/bid/scoreparse/`
- sidecar：`document-converter-sidecar/`

---

## Phase 1: Setup（无独立任务）

零 DB 迁移、零前端改动、零新依赖——无 Setup 任务。配置键沿用 @Value + 默认值范式（research R8 清单），随各组件构造器落地。

---

## Phase 2: Foundational（阻塞 prerequisites）

**Purpose**: 被多个 US 共享的值对象与异常类型，先于全部 Story 存在。

- [x] T001 [P] 新建 `OversizedBidTextException`（业务类拒绝，MESSAGE=「投标文件文本量超过上限（约 500 万字），无法完成打分，请检查是否误传或拆分文档」）与 `BidFileQuotaException`（资源类拒绝，MESSAGE=「投标文件过大超出服务器处理配额，请联系管理员」）于 `scoreparse/application/`，均包私有构造（对照现有 `OversizedBidFileException` 范式）
- [x] T002 [P] 新建 `LoadedBidFile` sealed 接口（`InMemory(byte[] content, String fileName, String contentType)` / `OnDisk(Path tempFile, String fileName, String contentType)`）于 `scoreparse/application/`（data-model.md §LoadedBidFile）

**Checkpoint**: `mvn test -Dtest=ArchitectureTest` 绿（新类不破坏分层）；两文件均 <50 行。

---

## Phase 3: User Story 1 - 大文件投标文件能完成打分 (Priority: P0) 🎯 MVP

**Goal**: 1.62GB 投标文件流式落盘 + sidecar 流式提取文本 + 完成打分，全程无 `byte[]` 整包、无 OOM（SC-001/SC-002）。

**Independent Test**: quickstart 场景 1（大文件打分：任务终态 + 无 OOM + 临时文件清理）+ 场景 2（≤50MB 快路径回归，行为与现网一致，零落盘）。

### Tests for US1（先写、先失败）

- [x] T003 [P] [US1] `TempBidFileStoreTest`：reserve 返回 `{tempDir}/{taskId}.part` 路径；release 删文件且幂等（二次 release no-op）；cleanupAll 删全部 `*.part`——`backend/src/test/java/com/xiyu/bid/scoreparse/application/TempBidFileStoreTest.java`
- [x] T004 [P] [US1] `StreamingBidFileFetcherTest`：≤50MB 响应返回 `InMemory`（零临时文件）；>50MB 响应 spill 返回 `OnDisk`（文件内容与响应体一致）；单文件 >max-file-bytes 抛 `BidFileQuotaException`；Content-Length 声明超限不读 body 即拒——`backend/src/test/java/com/xiyu/bid/scoreparse/application/StreamingBidFileFetcherTest.java`
- [x] T005 [P] [US1] pytest 契约测试：`/convert-stream` 返回 200 + text/plain 流式；无文本层 PDF 返回 200 空 body；不可解析文件 422——`document-converter-sidecar/tests/test_convert_stream.py`（无 tests 目录则新建）

### Implementation for US1

- [x] T006 [US1] `TempBidFileStore`：`reserve(taskId)` / `release(taskId)` / `cleanupAll()`，`{tempDir}` 来自 `app.score-parse.bid-file.temp-dir`（默认 `${java.io.tmpdir}/xiyu-score-parse`）——`scoreparse/application/TempBidFileStore.java`（depends T003）
- [x] T007 [US1] `StreamingBidFileFetcher`：复用 `BoundedHttpDownloader` 骨架（ofInputStream + 8KB buf + Content-Length 预检），内存 buffer 累计 ≤`ram-buffer-bytes`(50MB) 返回 `InMemory`，超过即 flush `{taskId}.part` 继续流式写返回 `OnDisk`；单文件累计 >`max-file-bytes`(2GB) 中止并抛 `BidFileQuotaException`；`download-timeout-seconds`(600s) 作 `HttpRequest.timeout`——`scoreparse/application/StreamingBidFileFetcher.java`（depends T002, T004）
- [x] T008 [US1] sidecar `POST /convert-stream`：复用 `/convert` 的流式上传读循环（64KB chunk + `SIDECAR_MAX_UPLOAD_MB` 413 中止 + NamedTemporaryFile + finally 清理），markitdown 转换后以 `StreamingResponse` 流式输出纯文本（text/plain, chunked）；不返回 sections/tables 等 JSON 元数据；`SIDECAR_MAX_UPLOAD_MB` 默认值 30→2048——`document-converter-sidecar/app.py`（depends T005；契约见 `contracts/sidecar-streaming-api.md`）
- [x] T009 [US1] `SidecarStreamTextExtractor`：消费 `/convert-stream`——`FileSystemResource`/`PathResource` 流式上传 body（OnDisk 路径，零整包内存）；响应流式读 + char 计数，累计 >`max-text-chars`(5,000,000) 中止连接抛 `OversizedBidTextException`，否则拼装文本返回——`scoreparse/application/SidecarStreamTextExtractor.java`（depends T008）
- [x] T010 [US1] `ScoreBidDocumentLookup` 改造：`loadBytes` → `loadBidFile(projectId, taskId)` 返回 `LoadedBidFile` 二态；OBS 链路换 `StreamingBidFileFetcher`（签名 URL 逻辑保留）；本地/doc-insight 链路维持 byte[] 返回包 `InMemory`（capSize 调用移除）——`scoreparse/application/ScoreBidDocumentLookup.java`（depends T007）
- [x] T011 [US1] `ScoreScoringAppService` 接线：`loadBidFile` 后按二态分派——`InMemory` 走既有 `textExtractor.extract`（快路径，行为与现网一致）；`OnDisk` 走 `SidecarStreamTextExtractor`；try-finally 保证任务终态路径必 `release(taskId)`；行数不增（新逻辑已在新类，必要时拆 `BidFileLoadingCoordinator`）——`scoreparse/application/ScoreScoringAppService.java`（depends T006, T009, T010）
- [x] T012 [US1] 回归改造：`ScoreBidDocumentLookupTest`（二态断言替代 capSize 断言）与 `ScoreScoringAppServiceTest`（快路径行为不变 + OnDisk 分派 mock）——`backend/src/test/java/com/xiyu/bid/scoreparse/application/`（depends T010, T011）

**Checkpoint**: quickstart 场景 1+2 本地通过；`mvn test -Dtest='Scoreparse*Tests'` 绿；`mvn test -Dtest=ArchitectureTest` 绿。

---

## Phase 4: User Story 2 - 服务内存与磁盘安全 (Priority: P0)

**Goal**: 并发总量配额 4GB 生效；临时文件在任务终态/超时/重启/启动四挂点全清理，无孤儿（SC-003）。

**Independent Test**: quickstart 场景 4（配额拒绝：调小 max-file-bytes 触发 400 配额文案）+ 场景 5（kill -9 重启后 RecoveryRunner 清理 + 启动全清）。

### Tests for US2（先写、先失败）

- [x] T013 [P] [US2] `TempBidFileStoreTest` 增补：并发总量 >`max-total-bytes`(4GB) 时 reserve 拒绝抛 `BidFileQuotaException`；release 后计数递减、额度可复用；流式累计中发现总量超限即中止回滚——`backend/src/test/java/com/xiyu/bid/scoreparse/application/TempBidFileStoreTest.java`

### Implementation for US2

- [x] T014 [US2] `TempBidFileStore` 配额增强：`AtomicLong currentTotalBytes` + reserve 时 CAS 检查 `已用量+declaredBytes ≤ max-total-bytes`；写过程中每 chunk 累计实际值，超限中止并回滚本次登记——`scoreparse/application/TempBidFileStore.java`（depends T013；R4：单实例无跨进程争用）
- [x] T015 [P] [US2] `ScoreParseTimeoutScanJob` 挂点：`markTimeout(taskId)` 后调用 `TempBidFileStore.release(taskId)`——`scoreparse/infrastructure/scheduler/ScoreParseTimeoutScanJob.java`
- [x] T016 [P] [US2] `ScoreParseTaskRecoveryRunner` 挂点：`recover` 对每个恢复任务 `failTask` 后 `release(taskId)`；启动时先 `cleanupAll()`（进程重启 = 无进行中任务，建立计数零基线）——`scoreparse/infrastructure/bootstrap/ScoreParseTaskRecoveryRunner.java`
- [x] T017 [US2] `ScoreParseController` 语义映射：`BID_FILE_QUOTA_EXCEEDED` → `BidFileQuotaException.MESSAGE`（400）——`scoreparse/controller/ScoreParseController.java`（contracts/api-error-semantics.md 同步段表）

**Checkpoint**: quickstart 场景 4+5 通过；并发 2×1.5GB 场景（SC 前置）临时目录占用 ≤4GB 且任务后 60s 内清零。

---

## Phase 5: User Story 3 - 提取后文本量超限的明确拒绝 (Priority: P1)

**Goal**: 文本 >500 万字任务 FAILED + 明确文案，不产生部分得分（SC-004）。

**Independent Test**: quickstart 场景 3（构造 ≥5M 字符纯文本文档触发打分：FAILED + 文案 + score_result 无部分得分）。

### Tests for US3（先写、先失败）

- [x] T018 [P] [US3] `ScoreScoringAppServiceTest` 增补：`OversizedBidTextException` 在异步段被捕获 → `failScoringWithPending(taskId, ..., OversizedBidTextException.MESSAGE)` 且 `stateService.failTask` 收到同文案；不落任何部分 `score_result`——`backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppServiceTest.java`

### Implementation for US3

- [x] T019 [US3] 异步段异常传播：`doExecuteScoring` 的提取调用 catch `OversizedBidTextException` → 走既有 `failScoringWithPending` 统一出口（message = 异常 MESSAGE，禁止改写）；同步段触发时转语义码 `OVERSIZED_BID_TEXT`——`scoreparse/application/ScoreScoringAppService.java`（depends T018；char 计数已在 T009 实现，本任务只做传播链路）
- [x] T020 [US3] `ScoreParseController` 映射增补：`OVERSIZED_BID_TEXT` → `OversizedBidTextException.MESSAGE`——`scoreparse/controller/ScoreParseController.java`

**Checkpoint**: quickstart 场景 3 通过；`score_parse_task.error_message` 文案与 contracts/api-error-semantics.md 异步段表逐字一致。

---

## Phase 6: User Story 4 - 读取侧限制口径统一 (Priority: P2)

**Goal**: 50MB 字节硬限语义废止，全局无「投标文件超过 50MB」打分侧残留（SC-005）。

**Independent Test**: `grep -rn "投标文件超过 50MB" backend/ src/` 命中 0；`OVERSIZED_BID_FILE` 语义码映射删除。

### Implementation for US4

- [x] T021 [US4] 删除 `OversizedBidFileException` 与 `ScoreBidDocumentLookup.capSize` 残留调用、`ScoreParseController` 的 `OVERSIZED_BID_FILE` 映射行、相关过时测试断言——`scoreparse/application/OversizedBidFileException.java`（删文件）+ `ScoreBidDocumentLookup.java` + `ScoreParseController.java`（depends T010；`BoundedHttpDownloader` 不动——招标链路继续用）
- [x] T022 [US4] SC-005 验收 + 文档回填：全局 grep 清零截图入 PR 描述；`.wiki/pages/score-parse-service.md` 更新（50MB 防护条目改为「流式落盘 + 文本量 500 万字上限」口径）；`docs/lessons/lessons-learned.md` §120 追加根治方案索引（不改历史条目）——`.wiki/pages/score-parse-service.md`、`docs/lessons/lessons-learned.md`

**Checkpoint**: SC-005 grep = 0；`npm run check:doc-governance` + `node scripts/wiki-check.mjs` 绿。

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T023 [P] 全量回归门禁：`cd backend && mvn test`（全量）+ `npm run build` + `npm run test:unit`（前端虽零改动，按 Completion Gate 执行）+ `mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest`
- [ ] T024 部署前置检查（research R9）：生产 `df -h` temp-dir 挂载点 ≥6GB（4GB 配额 + 安全垫），不足则生产环境独立调小 `max-total-bytes`；检查项写入部署 runbook 备注（测试环境 2026-08-17 已达成：清理 buildkit + 旧 release 后 40GB 可用）
- [x] T025 测试环境部署 + quickstart 场景 1 终验（SC-001 铁证）：project 226 的 1.62GB 文件触发打分，任务终态 + `grep -c "OutOfMemoryError\|Java heap space"` = 0 + 堆峰值监控证据；部署报告落 `docs/release/`（2026-08-17 第 131 次部署完成：1.62GB > 512MB 配额走 FR-002 同步拒绝，400 + 配额精确文案 + 0.77s 拦截 + OOM=0 + 无 .part 残留 + sidecar 存活；过程中修复 sidecar OOM 根因——Java 端配额 2GB 形同虚设，降 512MB 与 sidecar markitdown 实际转换能力对齐，另修复 @Async 线程事务缺失；报告 docs/release/deploy-report-2026-08-17-131st-test.md）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 2（Foundational）**: T001/T002 可并行 → **阻塞全部 Story**
- **Phase 3（US1）**: depends Phase 2；内部链：T003/T004/T005（测试并行）→ T006/T007（T006 不依赖 T007，可并行）→ T008 → T009 → T010 → T011 → T012
- **Phase 4（US2）**: depends T006（TempBidFileStore 存在）+ T017 depends T001；T015/T016 可并行
- **Phase 5（US3）**: depends T009（char 计数）+ T011（接线）；T019 → T020
- **Phase 6（US4）**: depends T010/T011（改造完成才能删旧）；T021 → T022
- **Phase 7**: depends 全部

### User Story Dependencies

- US1 (P0) → US2 (P0)：US2 的配额与清理挂在 US1 建的 `TempBidFileStore` 上
- US1 (P0) → US3 (P1)：US3 传播的异常由 US1 的 `SidecarStreamTextExtractor` 抛出
- US1 (P0) → US4 (P2)：US4 删除的旧语义在 US1 改造后被替代
- US2/US3/US4 之间无依赖，可并行（不同文件）

### Parallel Opportunities

- Phase 2: T001 ∥ T002
- US1 测试: T003 ∥ T004 ∥ T005（Java 测试与 pytest 互不相干）
- US1 实现: T006 ∥ T007 ∥ T008（三个新文件；T007/T008 只依赖各自测试先行）
- US2: T015 ∥ T016 ∥ T017
- 跨 Story: US2 的 T015/T016/T017 与 US3 的 T018/T019/T020 与 US4 的 T021 可三线并行（收尾 T022/T023 需串行）

---

## Parallel Example: US1

```bash
# 三条测试线并行（先失败）：
Task: "TempBidFileStoreTest in backend/src/test/java/com/xiyu/bid/scoreparse/application/TempBidFileStoreTest.java"
Task: "StreamingBidFileFetcherTest in backend/src/test/java/com/xiyu/bid/scoreparse/application/StreamingBidFileFetcherTest.java"
Task: "pytest /convert-stream in document-converter-sidecar/tests/test_convert_stream.py"

# 三条实现线并行（不同文件）：
Task: "TempBidFileStore in scoreparse/application/TempBidFileStore.java"
Task: "StreamingBidFileFetcher in scoreparse/application/StreamingBidFileFetcher.java"
Task: "sidecar /convert-stream in document-converter-sidecar/app.py"
```

---

## Implementation Strategy

### MVP First（US1 Only）

1. Phase 2 foundational（异常类 + sealed 类型，<1h 量级）
2. Phase 3 US1 全链路（spill + sidecar 流式 + 接线）
3. **STOP and VALIDATE**: quickstart 场景 1+2——1.62GB 无 OOM + 小文件零回归
4. 测试环境部署验证（此时 50MB 旧语义仍在但已被新链路绕过，可安全演示）

### Incremental Delivery

1. US1 → 大文件能打分（核心价值交付）
2. US2 → 配额与清理兜底（防磁盘版 OOM）
3. US3 → 文本量超限明确拒绝（补齐边界语义）
4. US4 → 旧口径清零（文档与代码一致性收口）
5. Phase 7 → 全量门禁 + 测试环境终验 + 部署报告

---

## Notes

- 行预算：新 Java 类各 <200 行；`ScoreScoringAppService` 已满 300 行预算——T011 若导致超限，拆 `BidFileLoadingCoordinator` 承接分派逻辑（Constitution IV）
- `BoundedHttpDownloader` 全程不动（招标链路 `TOO_LARGE_MESSAGE` 语义保留，属 spec 043 管辖）
- docinsight 的 `MarkItDownSidecarExtractor` 与 `/convert` 零改动（隔离爆炸半径，research R2）
- sidecar `SIDECAR_MAX_UPLOAD_MB` 默认值改动会影响 `/convert`（共用上限）——30→2048 属放宽，无破坏性；PR 描述需注明
- 每任务原子提交 + 对应测试证据（RELIABILITY 关键硬约束）
