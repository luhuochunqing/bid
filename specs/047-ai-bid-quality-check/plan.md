# Implementation Plan: AI 标书质量核查（Feature 047 - 架构优化版）

**Branch**: `047-ai-bid-quality-check` | **Date**: 2026-08-18 | **Spec**: [specs/047-ai-bid-quality-check/spec.md](file:///Users/user/xiyu/worktrees/gemini/specs/047-ai-bid-quality-check/spec.md)

**Input**: 基于 PRD《AI 标书质量核查—抽屉详情》与思维链 Review 优化结论，消除 POC 虚假实现，落地生产级真实文件解析、25项标准化质量核查、知识库按需联动、物理页码证据链、异步长任务与抽屉 UI。

---

## 1. 架构总览与分层设计 (极简单表统一模型)

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       前端 UI 交互层                                              │
│  - QualityCheckDrawer.vue (ElDrawer 880px, 替换 QualityCheckDialog.vue)                          │
│    ├── 头部统计看板 (通过 / 不通过 / 待确认 / 文件名 / 核查时间)                                     │
│    ├── 25 项分组卡片 (基本信息 10 / 分项合规 10 / 附件清单 5)                                         │
│    ├── 证据链抽屉/展开区 (招标文件页码摘录 ↔ 投标文件物理页码原文 ↔ 知识库记录)                          │
│    └── 异步轮询调度器 (2s 轮询 / 120s 超时 / 重新核查二次确认弹窗)                                     │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │ HTTP REST API (Auth Guarded)
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     Controller / API 路由层                                      │
│  - POST /api/compliance/bid-quality/trigger/{projectId}?force=false (触发异步核查/返回 taskId 或缓存)   │
│  - GET  /api/compliance/bid-quality/progress/{taskId}               (轮询任务进度与状态)             │
│  - GET  /api/compliance/bid-quality/report/{projectId}              (获取项目最新核查报告)            │
│  - POST /api/compliance/bid-document/check/{projectId}              (向后兼容旧路由重定向至新服务)   │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  Application 编排与异步调度层                                    │
│  - BidQualityCheckTaskAppService: 任务状态机控制、SHA-256 缓存判断、权限守卫                      │
│  - BidQualityCheckAsyncExecutor: @Async 异步线程池调度执行器                                     │
│    ├── Step 1: ComplianceTargetLoader (加载真实 ProjectDocument BID/TENDER 文件流)              │
│    ├── Step 2: SidecarStreamDocumentAdapter (Java 21 HttpClient 流式上传防 OOM，保留换页符 \f)   │
│    ├── Step 3: KnowledgeBaseOnDemandLoader (根据标书要求定向精准检索资质/人员/业绩库)             │
│    ├── Step 4: BidDocumentQualityEngine (18项纯 Java 确定性规则毫秒熔断 + 7项关键章节定向送 LLM)   │
│    └── Step 5: 单表更新 ComplianceCheckResult (更新状态 COMPLETED、记录 25 项 Evidence JSON)      │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  Domain 纯核心规则策略层 (FP-Java)                               │
│  - BidQualityCheckItem (25项标准枚举，唯一 Code、Category、Severity、Title)                      │
│  - QualityEvidence / QualityCheckItemResult (不可变 Record / 领域结果对象)                       │
│  - DeterministicCheckPolicy (18 项纯 Java 正则与逻辑算法：金额大小写、印章、敏感词、工期、附件)   │
│  - 纯函数式，零 Spring/JPA 依赖，100% 内存单测覆盖                                               │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     Infrastructure / 存储层                                      │
│  - MySQL 8.0 表: `compliance_check_results` (扩展 taskId, status, progress, check_details 存储)  │
│  - Sidecar (FastAPI 端口 8009): 真实 PDF/Word 解析、提取带有物理换页符 \f 的结构化文本            │
│  - 企业知识库 Repositories (Qualification / Personnel / Performance / BrandAuth / Warehouse)     │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 数据库迁移设计 (Flyway V1196 单表扩展)

统一扩展现有的 `compliance_check_results` 表，避免引入多表外键同步与冗余实体。

### 1. `backend/src/main/resources/db/migration-mysql/V1196__extend_compliance_check_results_for_quality.sql`
```sql
-- 扩展 compliance_check_results 支持异步任务进度流转、文件哈希缓存与质量统计
ALTER TABLE `compliance_check_results`
    ADD COLUMN `task_id` VARCHAR(64) NULL COMMENT '异步任务UUID',
    ADD COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '任务状态: INIT, PARSING, CHECKING, COMPLETED, FAILED',
    ADD COLUMN `progress` INT NOT NULL DEFAULT 100 COMMENT '任务进度百分比 (0-100)',
    ADD COLUMN `current_step` VARCHAR(128) NULL COMMENT '当前处理步骤说明',
    ADD COLUMN `error_message` VARCHAR(1000) NULL COMMENT '失败错误信息',
    ADD COLUMN `bid_file_name` VARCHAR(255) NULL COMMENT '投标文件名',
    ADD COLUMN `bid_file_hash` VARCHAR(64) NULL COMMENT '投标文件SHA-256',
    ADD COLUMN `tender_file_hash` VARCHAR(64) NULL COMMENT '招标文件SHA-256',
    ADD COLUMN `passed_count` INT NOT NULL DEFAULT 0 COMMENT '通过项数',
    ADD COLUMN `failed_count` INT NOT NULL DEFAULT 0 COMMENT '不通过项数',
    ADD COLUMN `manual_review_count` INT NOT NULL DEFAULT 0 COMMENT '待确认项数',
    ADD COLUMN `total_count` INT NOT NULL DEFAULT 25 COMMENT '总检查项数';

ALTER TABLE `compliance_check_results`
    ADD INDEX `idx_ccr_task_id` (`task_id`),
    ADD INDEX `idx_ccr_hashes` (`bid_file_hash`, `tender_file_hash`);
```

### 2. `backend/src/main/resources/db/rollback/migration-mysql/U1196__extend_compliance_check_results_for_quality.sql`
```sql
ALTER TABLE `compliance_check_results`
    DROP INDEX `idx_ccr_hashes`,
    DROP INDEX `idx_ccr_task_id`,
    DROP COLUMN `total_count`,
    DROP COLUMN `manual_review_count`,
    DROP COLUMN `failed_count`,
    DROP COLUMN `passed_count`,
    DROP COLUMN `tender_file_hash`,
    DROP COLUMN `bid_file_hash`,
    DROP COLUMN `bid_file_name`,
    DROP COLUMN `error_message`,
    DROP COLUMN `current_step`,
    DROP COLUMN `progress`,
    DROP COLUMN `status`,
    DROP COLUMN `task_id`;
```

---

## 3. 实施步骤与核心规范 (T001 ~ T008)

1. **T001 (Phase 1)**: 执行 Flyway V1196 迁移，更新 `ComplianceCheckResult` 实体，确立单表承载模型。
2. **T002 (Phase 2)**: 编写 25 项 `BidQualityCheckItem` 领域模型与 `DeterministicCheckPolicy`，完成 18 项纯规则 100% 内存单测。
3. **T003 (Phase 3)**: 重构 `ComplianceTargetLoader` 与流式 `SidecarStreamDocumentAdapter`，生成物理分页 `PageContent`。
4. **T004 (Phase 4)**: 编写 `KnowledgeBaseOnDemandLoader` 与 `BidDocumentQualityEngine`，融合 18 项规则与 7 项切片 AI 推理。
5. **T005 (Phase 5)**: 编写 `BidQualityCheckAsyncExecutor` 与 `BidQualityCheckTaskAppService`，实现基于哈希的缓存复用。
6. **T006 (Phase 6)**: 升级 `ComplianceController` REST 路由与 MockMvc 鉴权测试。
7. **T007 (Phase 7)**: 开发 `QualityCheckDrawer.vue` 抽屉并在 `DraftingStage.vue` 中挂载联调。
8. **T008 (Phase 8)**: 运行 Playwright E2E 真实端到端测试与全套架构门禁。
