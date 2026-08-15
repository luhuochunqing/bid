# AI 评分标准解析 — 后端实现笔记（spec 041）

> 对应 Spec Kit feature：`specs/041-ai-score-parse-backend/`
> 交付范围：解析/打分/知识库匹配服务 + 3 张表 + 5 个 match 接口 + 异步任务机制

## 交付总览

| 模块 | 位置 | 说明 |
|------|------|------|
| 表结构 | `V1187__create_score_parse_tables.sql` / `V1188__add_score_parse_indexes.sql` | score_parse_task / score_item / score_result |
| 域核心（纯函数） | `scoreparse/domain/` | 合并去重、客观/主观分类、权重闭环、数量校验、状态判定、得分钳位、打分守卫、汇总聚合 |
| 应用编排 | `scoreparse/application/` | 解析（US1）、五类匹配（US2）、预计得分（US3）、实际打分（US4）、任务状态机 |
| LLM 基础设施 | `scoreparse/infrastructure/openai/` | 四路召回 + 阶段 2 打分（结构化输出 + prompt 模板） |
| 超时/恢复 | `scoreparse/infrastructure/scheduler/` + `bootstrap/` | 30min 超时扫描 + 启动卡死恢复 |
| REST | `scoreparse/controller/ScoreParseController.java` | 契约见 `specs/041-*/contracts/score-parse-api.md` |

## 关键设计决策

1. **异步四件套复用 spec 031 范式**：@Async("scoreParseExecutor") + DB 持久化（score_parse_task）+ Redis 进度缓存 + 自代理解决 @Async 自调用失效。任务状态机 PENDING → PROCESSING → COMPLETED/FAILED，每次跃迁独立事务（`ScoreParseTaskStateService`）。
2. **FR-021 覆盖语义**：`score_result.score_item_id` 无 FK 级联，重新解析时由 `ScoreParseAppService.invalidatePreviousResults` 显式按旧 item ID 删除旧打分结果 + 删除旧评分项；重新打分整批替换（事务内 DELETE + INSERT，失败不删旧结果）。
3. **超时保护双层机制**：`ScoreParseTimeoutScanJob`（@Scheduled fixedDelay=5min，阈值 `app.score-parse.timeout-minutes:30` 可注入，置 FAILED + timeout_marked=1 + 契约文案）+ `ScoreParseTaskRecoveryRunner`（启动时 @Order(30)，复用 failTask 三层降级，同 30min 阈值避免误伤刚触发任务）。
4. **LLM 输出守卫前移**：阶段 2 打分输出经 `ScoreAssessmentGuard`（domain 纯函数）收敛——超区间得分置 null + rangeInvalid 标记、主观项数字强制丢弃、quoteMissing=true 时引用置空。守卫不满足时状态回 PENDING 待人工确认。
5. **客观/主观判定唯一真相源**：`ScoreTypeClassificationPolicy`（域策略），LLM 的 scoreTypeGuess 仅参考不落库。

## 测试口径

- 域测试：MergePolicy/ClassificationPolicy/WeightSumCheck/ItemCountCheck/StatusPolicy/PartialScorePolicy/RangeGuard/AssessmentGuard/SummaryAggregator/KnowledgeCategoryPolicy（10 类）
- 编排测试：ScoreParseAppServiceTest（FR-021 覆盖）/ EstimatedScoreServiceTest / ScoreScoringAppServiceTest（前置校验 400/409、整批覆盖、失败保留旧结果）/ 五类 MatchServiceTest
- 基础设施测试：ScoreParseTimeoutScanJobTest（5）/ ScoreParseTaskRecoveryRunnerTest（4）/ MarkdownScoreSectionLocatorTest
- 架构门禁：ArchitectureTest / FPJavaArchitectureTest / MaintainabilityArchitectureTest 全绿

## 回滚

- 迁移回滚脚本：`db/rollback/migration-mysql/U1187_*.sql` / `U1188_*.sql`
- 代码回滚：revert PR 即可，无外部系统副作用（Redis key 前缀 `scoreparse:progress:` 自过期）
