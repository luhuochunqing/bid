# Plan: 057 守卫语料完整性 + run-summary + 金标回放

## 技术设计

### D-A 裁决语料治理（US1）

```
applyGlobalMerge(pool, fullText)
  ├─ pool<2 ──────────────► 原样返回
  ├─ executor 缺失 ───────► 原样返回(既有)
  ├─ locate(全文) 为空 ───► [NEW] SKIP_ADJUDICATION: 日志带原因, 返回池(fail-open, FR-01)
  ├─ 拼接 sections 至 LIMIT
  │     └─ truncated = 提前 break 或尾截 (FR-03)
  └─ executor.resolve → Guard.guard(pool, resolved, text)
        规则1 改造: detail ⊄ 语料 且 ∌ 池身份匹配 → 真幻觉丢弃;
                    ⊄ 语料 但 ≈ 池项(归一化互含)      → 放行计 poolBackedKept (FR-02)
        规则2 回补不变
```

改动文件：`ScoreGlobalMergeOrchestrator.java`（删全文前缀分支，增 AdjudicationContext 内部 record）、
`ScoreMergeResultGuard.java`（Outcome 增 poolBackedKept；guard 增池背书判定路径）。
残留风险（记录不处理）：截断区外低置信池项在裁决路径仍可能仅靠规则2救回——属既有限制。

### D-B 后处理块抽取 + run-summary（US2）

```
ScoreParseAppService(≤300 行硬闸, 现=300)
  doExecuteParse 中 :171-215 后处理块 ──抽出──► ScoreCandidatePostProcessor(@Service)
    process(taskId, fullText, RecallOutcome) → ProcResult{
        candidates,                          // 可落库最终清单
        telemetry(skippedChunks, lanes{regex,llm,table}, alignedHit, alignedTotal,
                  merge{invoked,skipped,truncated,poolBackedKept,hallucinationDropped,restored}),
        summaryJson, warnFlags }
```

- 数据流：Analyze/recall 四层内新增 per-lane 计数进 `RecallOutcome`
  （record 扩展字段 + `recallCandidates` 组装处填充；对齐命中沿用 countAligned 结果累计）。
- provider/model 取自解析请求配置解析器在 lane4 执行时的活跃描述（查 OpenAiBidAgentConfigurationResolver 现有公开方法复用，禁止新造第二真相源）。
- 存储迁移：`V____score_parse_task_run_summary.sql` ADD COLUMN run_summary TEXT NULL；
  实体加字段；完成路径一处 `setRunSummary`；`ScoreParseItemsDTO.Meta` 尾部追加可选组件
  runSummary（Jackson 加字段向后兼容，前端零改动）。
- 阈值常量集中在 `RunSummaryWarn` 纯函数：alignedRatio<0.5(alignedTotal>0)、
  hallucinationRatio>0.3(merged>0)；PostProcessor 打 WARN。
- 归并编排 API 升级为返回 `ApplyOutcome{candidates, stats}`（旧 List 版删除，调用方唯一=PostProcessor，测试同步改）。

### D-C 金标夹具回放（US3）

```
backend/src/test/resources/scoreparse-golden/
  case-dual-section-renumber.md       ← 合成双标的商务1/技术1重号 + 须知前附表噪声(FR-08)
  case-dual-section-renumber.expected.json
  case-weight-gap-mixed.md            ← 权重缺口90+幽灵行混合形态
  case-weight-gap-mixed.expected.json
GoldenCasesReplayTest                 ← Locator+Extractor+TableLane+Merge+Ghost+WeightNormalize
                                        断言每案 P/R/F1 ≥ 阈值; 写 target/scoreparse-golden-report.json
scripts/eval-scoreparse/
  README.md        ← 标注规范+匹配谓词(去空白归一/互含判定/weight±0.5容差/贪心唯一配对)
  report.mjs       ← 读 golden-report.json 渲染 Markdown 趋势
  nightly-full.sh  ← XIYU_EVAL_CORPUS_DIR 未设→SKIP 并说明; 设定→调测试环境解析API采集全链路样本
package.json → eval:scoreparse:replay / eval:scoreparse:report
```

预期 JSON schema：`{focus:"表直抽|权重治理", threshold:0.x,
items:[{code,dim,detail,weight}], laneHints:["table"]}`。匹配谓词与 Java Guard 同语义但独立实现并文档化（报告口径单一出处=README §匹配谓词）。

## 风险与对策

| 风险 | 对策 |
|---|---|
| PostProcessor 抽取触碰 AppService 既有行为 | 先并行搬迁不改逻辑（纯移动），diff 分离 commit，回放类既有测试锁行为 |
| RecallOutcome 构造点多 | 仅 recallCandidates 一处构造+单测两处；扩展字段提供紧凑重载 |
| Meta 组件顺序追加破坏调用方 | 编译期即暴露；调用方只有 MetaBuilder(+controller 无关) |
| 行数预算 | SC-5 加入验证命令 |

## 验证命令

```bash
mvn -f backend/pom.xml test -Dtest='ScoreGlobalMerge*,ScoreMergeResultGuard*,*PostProcessor*,RecallOutcome*,GoldenCasesReplayTest,ArchitectureTest'
npm run eval:scoreparse:report   # 报告渲染冒烟
node -e "JSON.parse(require('fs').readFileSync('backend/target/scoreparse-golden-report.json'))"
```
