# Tasks: 057 守卫语料 + run-summary + 金标回放

## Phase 0: 规格（Commit ①）

- [ ] T000 本三件套随首代码 commit 入库（specs/057）。

## Phase 1: T3 守卫语料治理（Commit ②）

- [ ] T001 `ScoreMergeResultGuard`：Outcome 增 `poolBackedKept`；guard 增池身份匹配放行路径（先补红测：截断语料+池背书项）。
- [ ] T002 `ScoreGlobalMergeOrchestrator`：删全文前缀降级分支（红测先行：定位失败→executor.resolve 零调用）；拼接截断置 truncated 并透传日志。
- [ ] T003 旧测试适配（Outcome 构造与日志断言），`mvn test -Dtest='ScoreGlobalMerge*,ScoreMergeResultGuard*'` 绿 → Commit ②。

## Phase 2: T2 后处理抽取 + run-summary（Commit ③）

- [ ] T004 抽取 `ScoreCandidatePostProcessor`（纯搬迁不改行为，AppService 净减行）；定向测试绿锁定。
- [ ] T005 `RecallOutcome` 扩展 lane/aligned/provider/model 字段，`OpenAiScoreAnalyzer.recallCandidates` 组装处填充。
- [ ] T006 编排器 API→`ApplyOutcome`，Guard 计数贯通；`RunSummaryAssembler`+`RunSummaryWarn` 纯函数+单测。
- [ ] T007 迁移 `V____score_parse_task_run_summary.sql` + 实体字段 + Meta DTO 尾追加 + MetaBuilder 透出；@Transactional 落库路径审计。
- [ ] T008 AppService 收口 ≤300 行校验；`mvn test -Dtest='*PostProcessor*,*RunSummary*,ScoreParseApp*,ArchitectureTest'` 绿 → Commit ③。

## Phase 3: T1 金标回放 v0（Commit ④）

- [ ] T009 两份合成夹具 + expected JSON（synthetic 来源头注释；覆盖双标重号/前附表噪声、权重缺口+幽灵形态）。
- [ ] T010 `GoldenCasesReplayTest` 驱动确定性六件套 + P/R/F1 断言 + 报告 JSON 落盘。
- [ ] T011 scripts/eval-scoreparse/ 三件（README 谓词规范 / report.mjs / nightly-full.sh）+ package.json 两脚本冒烟 → Commit ④。

## Phase 4: 收尾（Commit ⑤）

- [ ] T012 全量定向回归（plan.md 验证命令块全执行留证据）+ wiki/lessons 勾选回填 + PR 创建。
