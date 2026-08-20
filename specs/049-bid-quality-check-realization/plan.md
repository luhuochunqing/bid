# Implementation Plan: AI 标书质量核查实质化（049）

**Branch**: `agent/qoder-init`（实施时按 SOP 开任务分支） | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/049-bid-quality-check-realization/spec.md`

## Summary

047 已交付质量核查的工程骨架（异步 taskId + 轮询、Drawer、25 项枚举、Evidence 结构、SHA-256 缓存），但核心业务实质仅约 20%：核查输入是元数据拼接而非真实文件、知识库输出固定文案、Evidence 页码硬编码、25 项判定全是 `String.contains()`。本计划按 spec 执行：第零步删除旧同步链（双链路合并），随后五步实质化——真实投标文件（存储分派 + Sidecar 解析，禁止降级）→ 真实招标文件 + LLM 结构化要求提取 → 四知识库真实查询 → Evidence 真实页码/原文 → LLM 混合裁决。唯一验收标准：真实投标 PDF + 真实招标 PDF 跑通 25 项可追溯到页码与原文的核查结果。

## Technical Context

**Language/Version**: Java 21（后端，以 `backend/pom.xml` 为唯一源）+ JavaScript / Vue 3（前端）

**Primary Dependencies**: Spring Boot 3.2 + JPA（后端）；document-converter-sidecar（Python，端口 8009，文档解析，按 `\f` 物理分页）；`RoutingAiProvider` / `OpenAiCompatibleClient`（LLM 接入）；Element Plus（前端）

**Storage**: MySQL 8.0 + Flyway（核查报告现持久化于 `ComplianceCheckResult`；Redis/DB 口径待产品拍板，见 spec §5.2，工作假设为现行 DB + hash 方案）；文件存储三前缀 `obs-direct:` / `bid-agent://` / `doc-insight://`（`ProjectDocumentStorageType`）

**Testing**: JUnit 5 + Mockito（后端单测，FP-Java 纯核心不依赖框架）、`mvn test`（含 ArchUnit）、Playwright E2E（主线人工走查 + 关键路径）、前端 ESLint + `npm run build`

**Target Platform**: 本地开发环境（主工作区 trae：后端 18089 / Sidecar 8009 / MySQL xiyu_bid_main）；qoder worktree 仅做代码编辑与 git 操作，不启动服务

**Project Type**: web-service（backend/ + src/ 前后端同仓）

**Performance Goals**: 单次核查端到端 ≤ 120s（对齐前端轮询超时 `MAX_POLL_TIMEOUT_MS`）；确定性规则项毫秒级；LLM 项以进度上报保持轮询活跃

**Constraints**: 禁止 Mock/固定文案证据（Real-API Only）；无投标文件禁止 fallback，trigger 同步 4xx；LLM 失败单项 MANUAL 不阻断整体；单 Java 文件 ≤ 300 行硬上限

**Scale/Scope**: 单项目单次核查；25 项检查 × 3 分组；四知识库各真实查询；改动集中在 `compliance` 包 + 前端 Drawer 消费侧

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS | 新增裁决/提取逻辑落纯核心（record + 纯函数）；LLM 客户端、存储分派、Repository 属 Imperative Shell，经 seam 注入纯核心 |
| II. Real-API Only | ✅ PASS | 本计划目标即删除 demo mock 段与固定文案假证据，方向与原则一致 |
| III. TDD | ✅ PASS | 每步先写测试（spec §4 各步均含测试要求）；确定性规则项保持无 LLM 单测；纯后端+前端删减为主，E2E 按 `[skip e2e-scope]` 豁免规则处理，唯一主线以人工走查 + api-tests 验证 |
| IV. Split-First & Simplicity | ⚠️ 需跟踪 | `BidDocumentQualityEngine` 加入 LLM 裁决后可能超 300 行硬上限 → 任务中强制拆分（规则 lane / LLM lane / Evidence 定位各自成类），列入 Complexity Tracking |
| V. OSS Integration | N/A | 不涉及组织目录 |
| VI. Authorization Unification | ⚠️ 需跟踪 | 现有三端点用 `isAuthenticated()`；trigger 新增同步 4xx 前置校验属 Service 层逻辑，不引入新权限形态。实施时确认项目级访问走 `ProjectAccessScopeService`（不新增 `hasAnyRole`） |
| VII. Defensive Collection & Graceful Degradation | ✅ PASS | 知识库查询失败置 MANUAL（降级不阻断）属本原则精神；新增代码禁用 2 参数 `Collectors.toMap`；不新增 5xx handler |
| VIII. Boring Proven Patterns | ✅ PASS | 复用既有 seam（存储分派、Sidecar、RoutingAiProvider、scoreparse 召回 lane + ProgressReporter 范式），不引入新框架 |
| DB Migrations | ✅ PASS | 若 `QualityEvidence` 序列化字段扩展需动表，走 Flyway V/U 成对脚本；预计仅 JSON 列内容变化，无需新迁移 |
| Sync-First / Lease Protocol | ✅ PASS | 实施开工前 `git fetch origin && git rebase origin/main` + `who-touches.sh` 检查（写入 quickstart 前置步骤） |

**初次检查结论：无不可豁免违规，两项 ⚠️ 在实施中跟踪，进入 Phase 0。**

### 设计后复检（Phase 1 完成后）

| 复检点 | 结论 |
|---|---|
| 新增模型是否引入框架依赖 | ✅ `TenderRequirements`/`KnowledgeMatch`/`EvidenceHit`/`EvidenceLocator` 均为纯核心 record/纯函数，无 Spring/JPA 依赖（原则 I） |
| 契约是否引入 Mock 路径 | ✅ 全部契约走真实 Repository/Sidecar/RoutingAiProvider，降级分支是 MANUAL 而非假数据（原则 II） |
| 文件规模 | ⚠️ Engine 拆分方案已落入 Complexity Tracking 与 plan 结构，实施时以 300 行棘轮门禁为准（原则 IV） |
| 是否新增权限形态/2 参数 toMap/5xx handler | ✅ 均未引入；LLM 失败日志要求已对齐原则 VII §3（原则 VI/VII） |
| 是否新增持久化表 | ✅ 无新表、预计无 Flyway 迁移；存储口径变更（若产品拍 A）另开任务，不在本计划内扩散 |

**复检结论：维持 PASS（含已跟踪的 ⚠️ 项），无新增违规，无需更新 Complexity Tracking。**

## Project Structure

### Documentation (this feature)

```text
specs/049-bid-quality-check-realization/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出（本命令生成）
├── data-model.md        # Phase 1 输出（本命令生成）
├── quickstart.md        # Phase 1 输出（本命令生成）
├── contracts/           # Phase 1 输出（本命令生成）
│   ├── bid-quality-api.md       # 保留链三端点契约 + 新增 4xx 前置校验
│   └── tender-requirements-extraction.md  # 招标要求 LLM 结构化提取契约
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成，非本命令）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── compliance/
│   ├── controller/ComplianceController.java        # 删旧端点；trigger 前置校验返回 4xx
│   ├── application/
│   │   ├── BidQualityCheckTaskAppService.java      # trigger 同步前置校验
│   │   ├── BidQualityCheckAsyncExecutor.java       # 删除 extractRequiredCerts 硬编码，接入招标结构化要求
│   │   └── BidDocumentQualityCheckAppService.java  # 【删除】
│   ├── domain/
│   │   ├── policy/BidDocumentQualityEngine.java    # 拆分为规则 lane + LLM lane（≤300 行约束）
│   │   ├── policy/TenderRequirementExtractor.java  # 【新增·纯核心】招标结构化要求模型与纯逻辑
│   │   ├── policy/EvidenceLocator.java             # 【新增·纯核心】PageContent 页码/原文定位
│   │   ├── BidDocumentQualityCheckPolicy.java      # 【删除】
│   │   ├── BasicInfoCheckRules.java 等 3 个旧规则类 # 【删除】
│   │   ├── QualityEvidence.java                    # 扩展 source/recordId/match（向后兼容）
│   │   └── DocumentContent.java                    # 已有 PageContent，不改
│   └── service/
│       ├── ComplianceTargetLoader.java             # 存储分派取流；删 fallback 与旧方法
│       ├── SidecarStreamDocumentAdapter.java       # 不改（已按 \f 分页）
│       └── KnowledgeBaseOnDemandLoader.java        # 四库真查询 + 结构化证据
├── projectworkflow/
│   ├── core/ProjectDocumentStorageType.java        # 复用：三前缀分派依据
│   └── service/ProjectDocumentDownloadService.java # 复用：取流分派参照
├── scoreparse/application/InitiationTenderTextResolver.java  # 复用：招标文件定位
├── performance/domain/port/PerformanceRepository.java        # 复用：业绩库真实数据源
└── ai/client/RoutingAiProvider.java                # 复用：LLM 路由

backend/src/test/java/.../compliance/               # 删除旧链测试；新增五步测试

src/                                                 # 前端（删除侧为主）
├── views/Project/stages/components/
│   ├── QualityCheckDialog.vue                       # 【删除】
│   ├── QualityCheckDrawer.vue                       # 保留；trigger 4xx 提示适配
│   └── QualityCheckItemCard.vue                     # 保留；新证据字段渲染适配
├── views/Project/stages/DraftingStage.vue           # 删旧链 watch；保留 Drawer 入口
├── composables/projectDetail/useProjectDetailAI.js  # 删 runBidDocumentQualityCheck + demo mock
├── composables/useQualityCheckDrawer.js             # 保留；4xx 错误提示
└── api/modules/ai/compliance.js                     # 删两个旧 API 封装

api-tests/                                           # 删旧接口 .http；新增 trigger/progress/report 用例
```

**Structure Decision**: 既有 monorepo web 结构（backend/ + src/），不新增目录层级；后端新增类全部落在既有 `compliance` 包内（纯核心在 `domain/policy/`），符合包按业务域划分原则。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `BidDocumentQualityEngine` 拆为多类（规则 lane / LLM lane / Evidence 定位） | 加入 LLM 裁决与真实 Evidence 定位后单文件必然突破 300 行硬上限（Constitution IV） | "继续在单文件内扩展"会违反棘轮门禁，且规则/语义两类裁决的失败模式与测试策略完全不同 |

> 无其他 Constitution 违规。Redis/DB 存储口径不是违规项，是待产品拍板的 spec 决策（spec §5.2 / G6）。

## 执行顺序与依赖（供 /speckit-tasks 消费）

```text
第零步 双链路删除（纯删除，最先执行，独立原子提交）
  ↓
第一步 真实投标文件（存储分派 + Sidecar + trigger 4xx 前置校验）
  ↓
第二步 真实招标文件 + LLM 结构化要求提取（依赖第一步的解析管道）
  ↓
第三步 四知识库真实查询 + 结构化证据（与第二步可并行，但证据结构需先定）
  ↓
第四步 Evidence 真实页码/原文（依赖第一、二步的 PageContent 与第三步的知识库证据）
  ↓
第五步 LLM 混合裁决（依赖二、三、四步全部产物）
  ↓
收尾 G1-G6 门禁验证（唯一主线人工走查 + grep 零残留 + 存储口径定案）
```

阻塞项：spec §5.2 存储口径产品拍板，不阻塞第零～五步实施，阻塞最终收尾合入。
