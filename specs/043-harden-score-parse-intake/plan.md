# Implementation Plan: 评分解析生产风险收口

**Branch**: `043-harden-score-parse-intake` | **Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/043-harden-score-parse-intake/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

在 PR #2296「评分解析改走立项招标文件」之上收口生产风险：打开抽屉仅在「从未有过解析任务且无评分项」时自动**新建**一次；进行中只跟随已有任务；立项文件超过 50MB 不得整包载入（无底稿提示过大，有底稿回退）；立项读失败回退历史底稿；`hasSource` 与 `resolve` 同一套成功条件；复用下载客户端；去掉初稿残留文案并写清产品决定。不改表、不改权限、不改阶段 2 打分公式。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2；前端 Vue 3 + Vitest

**Primary Dependencies**: 现有 `scoreparse` 模块、`ProjectDocumentFileStorage`、`TenderDocumentStorage`、`ObsShareUrlSigner`、`java.net.http.HttpClient`

**Storage**: 无新表。复用 `score_parse_task`（PARSE 任务有无/状态）与 `bid_tender_document_snapshots.extracted_text`（历史底稿）

**Testing**: JUnit5 + Mockito（resolver / AppService）；Vitest（`useScoreParseDrawer` 自动解析门闩）

**Target Platform**: 与现网一致的 Linux 单体后端 + 浏览器控制台

**Project Type**: 现有 Web 单体上的增量修复

**Performance Goals**: 超限文件在读满 50MB 前失败返回，不把整文件留在堆上拖垮进程；自动解析每个项目最多 1 次（直至用户点「重新解析」）

**Constraints**: 文件上限 50MB（含）；`scoreParseExecutor` core=1/max=2 不扩容；`ScoreParseAppService` / `useScoreParseDrawer.js` 硬上限 300 行，本改动不得破线

**Scale/Scope**: 改动面限于解析取正文 + 抽屉自动触发 + 两处文案/说明；约 6–8 个文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| I. FP-Java | 50MB 截断与「立项失败则底稿」判断做成无框架纯函数/策略，AppService 只编排 | ✅ PASS |
| II. Real-API | 不引入 mock 解析路径 | ✅ PASS |
| III. TDD | 先补 resolver/抽屉门闩失败用例再改实现 | ✅ PASS（计划内） |
| IV. Split-First | 不向 `ScoreParseAppService` 塞下载逻辑；下载上限留在 `InitiationTenderTextResolver`（或再拆 ≤80 行的 loader） | ✅ PASS |
| V. OSS | 不涉及 | ✅ N/A |
| VI. Authorization | 不改 `@PreAuthorize`；仍 `assertCurrentUserCanAccessProject` | ✅ PASS |
| VII. Defensive Collection | 本改动无新增 toMap | ✅ PASS |
| VIII. Boring | 复用已有任务表状态、OBS 签发器、50MB 立项约定 | ✅ PASS |
| Performance / File Upload | 宪法通用附件 20MB（PDF/JPG/PNG）**不适用**本特性。立项招标读上限沿用现网 50MB（含 Word），只限制解析时载入，不改上传页配置 | ✅ PASS（已登记例外） |

**Phase 1 复检**：契约只给 `GET /items` 的 meta 增两个可空字段，旧前端忽略即可。无源 400 须落 FAILED；超大无底稿用过大文案。无 Complexity Tracking 违规。

## Project Structure

### Documentation (this feature)

```text
specs/043-harden-score-parse-intake/
├── plan.md
├── spec.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── score-parse-auto-trigger.md
└── tasks.md             # /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── application/InitiationTenderTextResolver.java   # 取正文 + 50MB 上限 + 底稿回退
├── application/BoundedHttpDownloader.java          # 共享 HttpClient + 流式 50MB（同包拆分）
├── application/TenderIntake.java                   # 有源 / 无源原因（过大 vs 无文件）
├── application/ScoreParseAppService.java           # resolveIntake；无源落 FAILED 再 400
├── dto/ScoreParseItemsDTO.java                     # Meta 增 lastParseStatus / lastParseError
src/composables/projectDetail/useScoreParseDrawer.js
src/views/Project/stages/components/ScoreParseDrawer.vue  # 空态「重新解析」（T007 核对）
src/components/project/detail/ProjectTenderBreakdownDialog.vue
docs/implementation-notes/score-parse-initiation-tender.md
```

**Structure Decision**: 不新建业务包。下载上限与取正文结果可拆同包小类。抽屉：`lastParseStatus==null` 才自动新建；PENDING/PROCESSING 跟随；FAILED 展示原因。

## Complexity Tracking

> 无 Constitution 违规需登记。
