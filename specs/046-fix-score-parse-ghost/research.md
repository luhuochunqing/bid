# Research: fix-score-parse-ghost

## R1 幽灵项根因（已实锤，测试环境 project 212 / task 13）

**Decision**: 在 merge 之后、weightSumCheck 之前插入幽灵过滤纯函数。

**Rationale**（数据铁证）：
- 幽灵项特征：dim=detail="得分"（4 字以内）、weight=100、source_text 为空、code 为空（落库时才补假编号 "1"）。
- 四路去重（ScoreItemMergePolicy）对无 code 候选用 dim+detail 指纹独立保留 → 幽灵永不消失。
- WeightSumCheck 发现 200≠100 只触发补漏（recheckGaps），不剔除多余项。
- 过滤规则定为两条（任一命中即滤）：
  1. dim 与 detail 归一化相同且长度 ≤4 字（表头列名特征）
  2. code 空 且 sourceText 空/空白（无编号无条款文本 = 非条款行）
- 过滤放 merge 后而非召回内：正则路（lane1/2/3）与 LLM 路（lane4）产出的幽灵统一拦截，一处生效。

**Alternatives**:
- 在 LLM prompt 里加负例指令：不可靠（LLM 非确定性），保留为辅助手段不在本 spec 范围。
- 过滤放落库前（persistItems）：太晚，weightSumCheck 已被幽灵污染（200 触发无意义补漏）。

## R2 超载剔除策略（FR-002）

**Decision**: 新建 `WeightOverloadTrimPolicy`（domain 纯函数），不改 WeightSumCheck（128 行，加逻辑将超行数预算）。

**Rationale**:
- 剔除顺序：无 code 候选 → 无 sourceText 候选（从弱到强），每轮剔除后重算，合计 ≤100.5 即停。
- 剔尽仍 >100：保留全部（现有 weightWarning 语义兜底），不误删真实项。
- 合计 <100 分支维持现有 recheckGaps 补漏不动。

**Alternatives**:
- 改 WeightSumCheck 加剔除：违反 Split-First，且"检查"与"处置"是两个职责。

## R3 编号排序键解析（FR-003）

**Decision**: `ScoreItemOrderPolicy` 解析 code 为主段数值+子段数值（"4-1" → (4,1)）排序；不可解析的保持相对序排在末尾。

**Rationale**（实测 project 212 真实编号）：1,2,3,4-1,4-2,5,6,7 — 主数值序即评分表序。
- 解析规则：`^(\d+)(?:[-—.](\d+))?$`；主段不同按主段，主段相同按子段（无子段排前）。
- itemIndex 按排序后 1..n 重编；code 保留原文。

**Alternatives**:
- 按 sourceText 出现位置排序：依赖 location 字段质量，实测不可靠。

## R4 skippedChunks 数据通路（FR-005）

**Decision**: `score_parse_task` 加列 `skipped_chunks INT NULL`（一条 V 迁移）；analyzer 返回 `RecallOutcome(candidates, skippedChunks)` record；MetaBuilder 透出到 Meta。

**Rationale**:
- 跨请求可见必须持久化；Redis progress 在任务完成后被 clearProgress 清掉，不可用。
- recallCandidates 返回值从 List<ScoreCandidate> 改为 RecallOutcome 是类型安全通路，调用点只有 ScoreParseAppService + 少量测试。
- spec 初稿"零 DB 迁移"假设修正为"允许一条加列迁移"（boring proven pattern，回滚脚本配套）。

**Alternatives**:
- 实例字段计数器：有状态、线程不安全，弃。
- 塞 errorMessage：语义污染，弃。

## R5 chunk 重试（FR-005）

**Decision**: 抽私有方法 `requestCandidatesWithRetry`，失败重试 1 次再跳过，计数进 skippedChunks。

**Rationale**: OpenAiScoreAnalyzer 286 行，硬上限 300 — 抽方法净增约 9 行可控（295 内）。RecallOutcome record 放独立文件避免撑爆 analyzer 行数。

**Alternatives**:
- 通用重试框架（Resilience4j）：违反 Boring Proven Patterns，单点重试不需要。

## R6 行数预算盘点（Constitution IV）

| 文件 | 现状 | 预计 | 判定 |
|---|---|---|---|
| ScoreParseAppService | 277 | ≤292（+filter 3 行 +trim 分支 9 行 +skippedChunks 3 行） | 压线，需精简集成代码 |
| OpenAiScoreAnalyzer | 286 | ≤296（重试抽方法净增 ~10） | 压线 |
| ScoreItemPersistenceService | 135 | ~139（排序调用 +2、code 简化 -1） | OK |
| WeightSumCheck | 128 | 128（不动） | OK |
| 新 domain 策略 ×3 | 0 | 各 <100 | OK |
| ScoreParseItemsDTO | 48 | 50（Meta +1 字段） | OK |
| ScoreParseItemsMetaBuilder | 83 | ~86 | OK |
