# Implementation Plan: 历史案例原文直接下载

**Branch**: `058-historical-case-obs-download` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/058-historical-case-obs-download/spec.md`

## Summary

案例库"标书原文"入口按数据来源分流：历史切片案例（有原版文档来源记录）签发对象存储预签名直链下载原版 docx；系统沉淀案例保留现有档案 ZIP 下载；两者皆无则明确提示。技术路线复用已生产验证的 `ObsDownloadUrlGateway` 签发端口，`knowledge_case` 增加 `obs_bid_file_key` 列由导入管道写入，前端 `useBidCaseSourceActions` 内聚三分支路由。

## Technical Context

**Language/Version**: Java 21（后端）/ Vue 3 + Vite 5（前端）/ Python 3（导入脚本，仅标准库）

**Primary Dependencies**: Spring Boot 3.2、华为云 OBS Java SDK（经 `ObsDownloadUrlGateway` 端口隔离）、Element Plus、Axios

**Storage**: MySQL 8.0（`knowledge_case` 增列）、华为云 OBS `ehsy-widbid` 桶（只读+签发）

**Testing**: JUnit 5 + Mockito（后端）、Vitest（前端）、脚本 `--self-test`（导入管道）

**Target Platform**: 测试环境 172.16.38.78（后端 18080 / 前端 nginx 80）

**Project Type**: web-service（前后端 + 离线数据管道）

**Performance Goals**: 签发端点 <500ms；点击到浏览器开始下载 <5s（SC-001）

**Constraints**: 签名有效期 300 秒（与 `XIYU_OBS_DOWNLOAD_URL_EXPIRE` 默认一致）；不接受外部传入路径（FR-005）；前端禁止 Mock（宪法 II）

**Scale/Scope**: 300 条历史案例（测试环境），70 个项目目录，单桶单区域

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪法条款 | 评估 | 结论 |
|---|---|---|
| I. FP-Java 纯核心可单测 | 文件名提取/路径校验为纯函数，独立单测 | PASS |
| II. Real-API Only | 全链路真实 OBS 签发，无任何 Mock 路径 | PASS |
| III. TDD | 后端端点与前端分流均先写契约测试 | PASS |
| IV. Split-First & Simplicity | 单列 + 单端点 + 前端单 composable，无新抽象层 | PASS |
| V. OSS Integration | 复用 `ObsDownloadUrlGateway` 端口，不直接 new ObsClient | PASS |
| VI. Authorization Unification | 签发仅凭库内记录，权限同案例列表 | PASS |
| VII. Defensive & Graceful Degradation | key 为空/签发失败/对象缺失均返回可读错误 | PASS |
| VIII. Boring Proven Patterns | 沿用 `ObsShareUrlSigner` 同款签发模式 | PASS |

**设计后复检**：无违规，Complexity Tracking 留空。

## Project Structure

### Documentation (this feature)

```text
specs/058-historical-case-obs-download/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── case-download-url.md   # 签发端点契约
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks output
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── casework/
│   ├── controller/KnowledgeCaseController.java      # +GET /api/cases/{caseId}/download-url
│   ├── application/service/CaseDownloadUrlAppService.java  # 新增：查记录→签发（编排）
│   ├── domain/model/KnowledgeCase.java              # +obsBidFileKey
│   └── infrastructure/persistence/CaseMapper.java   # 映射
└── file/
    ├── domain/gateway/ObsDownloadUrlGateway.java    # 复用（不改）
    └── domain/core/ObsObjectKeyPolicy.java          # 新增：纯核心（basename 提取、前缀校验）

backend/src/main/resources/db/migration-mysql/
└── V1205__add_obs_bid_file_key_to_knowledge_case.sql

scripts/
├── slice_all_v9.py                                  # JSONL 增写 obs_bid_file_key
└── import-case-slices-to-knowledge-case.py          # 落列 + self-test 断言

src/
├── api/modules/case.js (或 knowledge 模块)           # +getCaseDownloadUrl
├── views/Knowledge/views/useBidCaseSourceActions.js  # 三分支路由
├── views/Knowledge/views/CaseGrid.vue                # 改用 composable
└── views/Knowledge/views/__tests__/useBidCaseSourceActions.spec.js  # +3 契约
```

**Structure Decision**: 复用现有 casework/file 模块边界；新增仅 1 个应用服务 + 1 个纯核心策略类 + 1 列 + 1 端点。

## Complexity Tracking

（无宪法违规，无条目）
