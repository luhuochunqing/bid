# Implementation Plan: AI 评分标准解析 — 后端服务

**Branch**: `agent/mimo/ai-score-parse-backend` | **Date**: 2026-08-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/041-ai-score-parse-backend/spec.md`

## Summary

为「AI 评分标准解析」交付后端能力：新建 `scoreparse` 业务域包，实现阶段 1 招标文件评分标准解析（四路召回 + LLM 结构化提取 + 合并去重 + 闭环校验）、五类知识库 match 接口（复用现有 qualification/personnel/performance/warehouse/brandauth 存储）、阶段 1 预计得分与阶段 2 投标文件实际打分（确定性计分公式 + LLM 证据定位）。异步任务复用 spec 031 四件套（trigger + @Async + 任务表 + Redis 进度），新增 30 分钟超时扫描 job。技术方案详见 [research.md](research.md) 10 项决策。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（后端唯一栈，无前端改动）

**Primary Dependencies**: Spring Data JPA + Flyway（MySQL 8.0）、OpenAI Java SDK（复用 biddraftagent 的 `OpenAiStructuredOutputService`/`OpenAiBidAgentConfigurationResolver`）、markitdown sidecar（文本提取，端口 8009）、StringRedisTemplate（进度缓存，Optional 降级）

**Storage**: MySQL `score_parse_task` / `score_item` / `score_result` 三新表 + `performance_record.contract_amount` 增列（V1187/V1188，配 U 回滚）；Redis key `score:parse|scoring:progress:{taskId}` TTL 7d

**Testing**: JUnit5 + Mockito（domain 纯核心确定性单测）+ MockMvc（controller）；47 个 biddraftagent 测试为范式参考

**Target Platform**: Linux server（Spring Boot 内嵌，随现有后端部署）

**Project Type**: web-service（现有单体后端内新增业务域包）

**Performance Goals**: 单评分项五类知识库匹配合计 ≤ 5s（SC-005，不含 LLM）；解析/打分异步不阻塞 HTTP

**Constraints**: 解析/打分任务 30min 超时自动终止（SC-004）；LLM 客户端超时 90s 兜底；文件 ≤ 50MB

**Scale/Scope**: 评分项 10-50 条/项目；知识库单表 < 1 万行（研究结论）；任务并发低频（executor core=1/max=2）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| I. FP-Java | 召回合并去重/闭环校验/计分公式/状态判定/守卫全部落 domain/ 纯核心（无 Spring/JPA 依赖，参照 `ScoringCriteriaClassificationPolicy` 范式）；AppService 仅编排 | ✅ PASS |
| II. Real-API | 无任何 mock 路径；match 接口直连真实知识库表 | ✅ PASS |
| III. TDD | tasks 阶段按 Red→Green 组织（纯核心公式先行） | ✅ PASS（计划内） |
| IV. Split-First | 新包独立分层，单文件 <200 行软上限；不向 biddraftagent 塞代码（仅 1 行事件发布侵入） | ✅ PASS |
| V. OSS | 不涉及 OSS 集成 | ✅ N/A |
| VI. Authorization | `isAuthenticated()` + Service 层 `assertCurrentUserCanAccessProject`（biddraftagent 现行合规形态；PRD §5.4 仅要求项目级权限，不新增 permissionKey） | ✅ PASS |
| VII. Defensive Collection | toMap 一律 3 参数（评分项 code 非唯一键，必须 merge function）；evidence/quote 生成失败降级为 null 不阻断打分主流程 | ✅ PASS |
| VIII. Boring | 全程复用已验证模式：spec 031 异步四件套、OpenAI SDK 通道、Specification 动态查询、Redis key 规范 | ✅ PASS |

**Phase 1 复检**：设计产物（data-model/contracts/quickstart）经上述 8 项复核，无新增违规。无 Complexity Tracking 违规项需登记。

## Project Structure

### Documentation (this feature)

```text
specs/041-ai-score-parse-backend/
├── plan.md                        # 本文件
├── spec.md                        # 需求规格
├── research.md                    # Phase 0：10 项技术决策
├── data-model.md                  # Phase 1：三新表 + 一增列
├── quickstart.md                  # Phase 1：验证指南
├── contracts/
│   ├── score-parse-api.md         # 解析/打分/查询 REST 契约
│   ├── knowledge-match-api.md     # 五类 match 接口契约
│   └── llm-output-schema.md       # LLM 输出 schema + 后端守卫
└── tasks.md                       # /speckit-tasks 生成（待）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/          # 新业务域包
├── controller/
│   ├── ScoreParseController.java        # /api/projects/{projectId}/score-parse/*
│   └── KnowledgeMatchController.java    # /api/knowledge/{cert|person|project|warehouse|brand}/match
├── application/
│   ├── ScoreParseAppService.java        # 解析编排（trigger + 查询）
│   ├── ScoreScoringAppService.java      # 打分编排（前置校验 + 结果）
│   ├── ScoreParseTaskStateService.java  # 任务状态机（spec 031 范式）
│   ├── ScoreParseProgressService.java   # Redis 进度（Optional 降级）
│   ├── TenderDocumentStoredListener.java# @Async @EventListener 自动触发
│   └── match/                           # 五类 match AppService（CertMatchService 等 5 个）
├── domain/                              # 纯核心（无框架依赖）
│   ├── ScoreCandidate.java              # 候选池 record
│   ├── ScoreItemMergePolicy.java        # 合并去重（FR-004）
│   ├── ScoreTypeClassificationPolicy.java# 客观/主观判定（FR-003）
│   ├── WeightSumCheck.java              # 分值闭环校验（FR-005）
│   ├── ItemCountCheck.java              # 数量校验（FR-006）
│   ├── PartialScorePolicy.java          # 部分得分公式（FR-013）
│   ├── ScoreStatusPolicy.java           # 状态判定（FR-015）
│   ├── ScoreRangeGuard.java             # 得分区间守卫（FR-016）
│   └── SummaryAggregator.java           # 汇总统计（FR-017）
├── infrastructure/
│   ├── openai/
│   │   ├── ScoreParsePrompts.java       # 四路召回 prompt 模板
│   │   ├── ScoreCandidateOutput.java    # 候选池 schema POJO
│   │   ├── ScoreAssessmentOutput.java   # 打分 schema POJO
│   │   └── OpenAiScoreAnalyzer.java     # LLM 编排（chunk 切片多轮）
│   ├── structure/
│   │   └── MarkdownScoreSectionLocator.java # 召回二：文档结构解析（纯静态可单测）
│   ├── scheduler/
│   │   └── ScoreParseTimeoutScanJob.java# @Scheduled 30min 超时扫描（R5）
│   └── bootstrap/
│       └── ScoreParseTaskRecoveryRunner.java # 启动恢复（spec 031 范式）
├── entity/                              # ScoreParseTask/ScoreItem/ScoreResult（自有包，不进 entity/ 热路径）
├── repository/                          # 三个 JPA Repository
└── dto/                                 # 请求/响应 record

backend/src/main/resources/db/migration-mysql/V1187__*.sql   # 三表（new-migration.sh 创建）
backend/src/main/resources/db/rollback/migration-mysql/U1187__*.sql
backend/src/main/resources/db/migration-mysql/V1188__*.sql   # performance.contract_amount
backend/src/main/resources/db/rollback/migration-mysql/U1188__*.sql
backend/src/main/java/com/xiyu/bid/config/AsyncConfig.java   # +scoreParseExecutor bean（挂 MdcTaskDecorator）
backend/src/main/java/com/xiyu/bid/biddraftagent/application/BidTenderDocumentImportAppService.java  # +1 行 publishEvent（R6）
backend/src/test/java/com/xiyu/bid/scoreparse/               # 镜像单测
```

**Structure Decision**: 单体后端内新增业务域包（Constitution I 包按业务域划分）；跨模块复用仅通过 biddraftagent 已公开的 infrastructure 接口（`OpenAiStructuredOutputService`/`TenderDocumentTextExtractor`/`TenderDocumentStorage`）与 1 处事件发布点，不改其内部实现。

## Complexity Tracking

> 无 Constitution 违规需豁免，此表为空。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
