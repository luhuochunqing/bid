# 1371 评分解析四列契约（权重 / 分类 / 仓库匹配 / 顺序）

日期：2026-09-04
分支：`agent/zcode/scoreparse-1371-invariants`
项目：winbid-test `/project/1371`（Project 21 MRO 工业品）

## 规格里没有、落地时必须钉死的决策

1. **分类只看因素名 `dim`，不扫细则 `detail`；因素名含「方案」一律 OTHER。**
   细则里的「人员配置 / 品牌覆盖 / 授权」不得改匹配库。`物流仓储方案` / `服务团队方案` 是技术评分方案，即使名字带仓储/团队，也不进仓库库或人员库。`服务网点及仓储` 不含「方案」，仍走仓库。

2. **仓库估计禁止无约束存在性满分。**
   名称关键词不再从细则切词传入（避免招标长句 `LIKE` 仓名）。可执行约束目前是面积门槛：`extractMinArea` 抽到正数才调用 `WarehouseMatchService`。抽失败（含千分位误咬、≤0）置 PENDING，**不得**用「库里有一座在用仓」打 FULL。1371「全国所有省份」没有结构化覆盖计算，也没有面积数字，阶段 1 保持待确认。HTTP `warehouse/match` 仍接受调用方自己组的名称/面积。覆盖率：`EstimatedScoreServiceTest`（无面积不调 match；5000㎡ 锁 `minArea`）。

3. **同一父维度编号重复时保持表格物理行序。**
   局部编号 `1/2` 在财务、网点、报价之间复用。编号唯一才允许组内数值重排（兼容 project 212 全局唯一编号）。编号重复则不再插队成 `1,1,2,2`。金标 `project21-mro.md` 召回 code 为空，物理序由 `Project1371ColumnInvariantsTest` 锁；重复编号由 `ScoreItemOrderPolicyTest.sort_duplicateLocalCodesSameCategory_keepsDocumentOrder` 锁。

4. **前端行 key / 打分结果按 `item.id`。**
   局部 `code` 会撞车，财务行会渲染成人员行的满足状态。`:key="item.id"` + `lookupScoreResult` 优先 `scoreItemId`。

5. **权重仍以召回层格子绝对分为准。**
   金标已断言财务 3、仓储 3、合计 100。本次不恢复自动归一化（spec 059 已卸管），也不把档位分（得 1.5 / 优 8–10）当成权重。父项 vs 叶子：1371 金标保持父项一行（财务状况=3），与 `testProject21ScoreParsing` 对齐；拆叶子的排序夹具仍只测顺序，不改权重真值。

## 验证

```
mvn test -Dtest=Project1371ColumnInvariantsTest,KnowledgeCategoryPolicyTest,EstimatedScoreServiceTest,ScoreItemOrderPolicyTest,WarehouseMatchServiceTest,RealProjectScoreParsingTest,ScoreItemAssessorTest,ScoreItemPersistenceServiceTest
# 全绿

npx vitest run src/views/Project/stages/components/ScoreParseTable.spec.js \
  src/composables/projectDetail/scoreParseTask.spec.js \
  src/composables/projectDetail/scoreParseSpendGuard.spec.js
# ScoreParseTable + task mapping + spendGuard 花费守卫全绿
```

存量 `score_item` 不会自动纠正，1371 需在测试环境重新解析后验收。
