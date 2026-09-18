# Implementation Plan: 招标文件大文件解析放行

**Branch**: `feat/bid-file-size-limit` | **Date**: 2026-09-18 | **Spec**: `spec.md`

**Input**: Feature specification from `/specs/065-tender-large-file-parse/spec.md`

## Summary

OBS 已放行 ≤512MB 招标文件上传，但 AI 评分标准解析仍以 50MB 字节硬限整包拒解，导致“传得上、解不出、换不掉”死路。本特性把招标侧取正文改为打分侧 spec 045 已验证的成熟方案：流式自适应落盘 + 提取后文本量设限（默认约 500 万字）+ 超限明确拒绝；判定口径从字节数换成文本量；上传前置拦截与两处文案同步。AI 抽取（4k 分块/召回四路）零改动；不改表、不改权限、不改打分公式。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（`backend/pom.xml` 为唯一源）/ Vue 3 + Vite 5 + Element Plus

**Primary Dependencies**: Spring Data JPA + Flyway（MySQL 8.0）；markitdown sidecar `/convert-stream`（Python，流式文本输出）；DeepSeek/豆包 OpenAI 兼容协议（结构化抽取）；华为云 OBS 直传（`obs-direct:`）

**Storage**: MySQL 8.0（`project_document`、`score_*`，本特性不改表）；OBS 对象；解析临时 spill 文件（`TempBidFileStore`，用毕即释）

**Testing**: 后端 JUnit5 + Mockito + ArchUnit（`mvn -Dtest=...`）；前端 Vitest（`npx vitest --run ...`）；E2E 沿用 spec 043 quickstart 矩阵（阈值换文本量），无新增 E2E 页面路径则 `[skip e2e-scope]`

**Target Platform**: Linux 容器服务端 + Web 前端（sidecar 与后端同编排，`POST /convert-stream` 可达）

**Project Type**: Web application（`backend/` + `src/`）

**Performance Goals**: 超限文件在读满配额/文本上限前失败返回，不整包驻留堆；≤50MB 文件走内存快路径，耗时与现网相当（±10%）；500 万字内大文件端到端 <10 分钟（对标 spec 049 401MB 档）

**Constraints**: `ScoreParseAppService.java` 现 295 行、硬上限 300 行，本改动 MUST 拆分纯函数腾预算；`scoreParseExecutor` core=1/max=2 不扩容；新异常 MUST 为 FP-Java 纯核心（无 Spring 依赖）；上限 MUST 为配置项（非硬编码）；解析任务 30min 超时不变

**Scale/Scope**: 单文件 ≤512MB（沿用打分侧配额）、提取文本 ≤约 500 万字；改动后端 4 文件 + 前端 2 文件 + 测试 4 文件；零 Flyway 迁移

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
|---|---|---|
| I. FP-Java | ✅ PASS | 新异常 `OversizedTenderTextException` 纯 `RuntimeException` + MESSAGE，无框架依赖；`ScoreParseAppService` 拆出纯拒绝策略函数 |
| II. Real-API Only | ✅ PASS | 全链路真实后端/sidecar/OBS，无 Mock；单测用 stub HttpClient（既有 `InitiationTenderTextResolverTest` 范式），非 mock 模式 |
| III. TDD | ✅ PASS | 先补单测（超限/边界/透传）再改实现；ArchUnit 分层不断 |
| IV. Split-First & Simplicity | ✅ PASS（实施复核） | `ScoreParseAppService` 零增量免拆；`StructuredTableExtractionHandler` 拆 `Result` 记录为顶层（行预算拆分，行为零变化） |
| V. OSS Integration | N/A | 不碰组织/角色同步 |
| VI. Authorization Unification | ✅ PASS | 不新增端点，不改 `@PreAuthorize`；资源校验沿用 `ProjectAccessScopeService` |
| VII. Defensive & Degradation | ✅ PASS | 超限/读失败降级路径保持（底稿回退）；失败经既有 `failTask` 出口；不新增 5xx handler |
| VIII. Boring | ✅ PASS | 复用 spec 045 四件套（`StreamingBidFileFetcher`/`TempBidFileStore`/`SidecarStreamTextExtractor`/`BidFileQuotaException`），不造新轮子 |
| File Upload 20MB（Performance Constraints） | 📝 例外登记 | 宪法通用附件 20MB 不适用招标正文链路（前例：spec 043 已登记立项 50MB 例外）；本特性上传配额 512MB/解析文本量口径在此延续该例外 |
| DB Migrations | ✅ PASS | 不改表，无 V/U 脚本 |

*Post-design re-check（Phase 1 完成后复核）: 设计未引入新端点/新表/新权限，异常与拆分均为纯核心，VIII 复用成立；IV 的拆分动作与 20MB 例外登记保持为仅有的两项记录，无新增违反。*

## Project Structure

### Documentation (this feature)

```text
specs/065-tender-large-file-parse/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── parse-error-semantics.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/application/
├── InitiationTenderTextResolver.java   # 改：loadBytes 整包改二态分派 + 文本量口径（216 行）
├── OversizedTenderTextException.java   # 新建：招标侧文本量超限语义异常（18 行）
├── StructuredTableExtractionHandler.java # 改：loadTenderFile + OnDisk 降级回落（281 行）
├── StructuredTableResult.java          # 新建：Result 记录外移（行预算拆分，行为零变化）
└── ScoreParseAppService.java          # 零改动（两处失败出口本就透传 emptyReason，295 行保持）

注：BoundedHttpDownloader.java 零改动（OBS 下载改走既有 StreamingBidFileFetcher，见 resolver 内 loadTenderFile）。

backend/src/test/java/com/xiyu/bid/scoreparse/application/
├── InitiationTenderTextResolverTest.java  # +3 用例
└── ScoreParseAppServiceTest.java          # +1 用例

src/views/Project/stages/
├── InitiationStage.vue                    # tip 文案（C1）
├── useInitiationStageActions.js           # beforeUploadDoc 配额前置拦（C1）
└── components/ScoreParseDrawer.vue        # 失败态次行 + 按钮文案（C2）
```

**Structure Decision**: Web application 双栈结构；后端改动收敛于 `scoreparse/application` 包（与 spec 045 同包，复用零跨包）；前端两处均为现文件内文案/校验增量，无新组件。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `ScoreParseAppService` 拆 `rejectUnavailable` 纯函数（已复核免拆） | 落地验证：两处失败出口本就透传 `emptyReason`，零增量，295 行保持 | 原 plan  precautionary 条目，实施复核后关闭 |
| 招标 512MB/文本量口径 vs 宪法 File Upload 20MB | 招标正文链路自 spec 043 即为例外（解析对象非通用附件），打分侧 spec 045 已用同口径验证 1.62GB | 套 20MB：与 OBS 已放行的上传现实矛盾，死路依旧 |
