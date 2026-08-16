# Implementation notes — 044 花费守卫

规格未写死、实现时拍板的点：

1. **AUTO 被拒时的 HTTP 形态**  
   监听器不走 HTTP。抽屉静默 AUTO 若被门闩/熔断挡住：有历史任务则返回该任务；无任务则 `status=SKIPPED` 且 `taskId=null`（200），前端不轮询。

2. **跳过任务仍落一行 SCORING**  
   哈希相同会建一条立刻 `COMPLETED` / `stage=SKIPPED` 的任务，方便 meta 展示「文件未变化」。不调 LLM，不改 `score_result`。手点也走这条，没有强制重打开关。

3. **`chapter_hashes` 双用途**  
   触发时 `ITEMS` 范围暂存 `IDS:1,2,3`；执行结束覆写成章节标题→哈希 JSON。只在执行中读 IDS 前缀。

4. **切不出章 / 上次无章节指纹 → FULL**  
   包括上次是 SKIPPED（没有章节指纹）后再改文件。宁可多打，不漏项。

5. **不确定是否相关 → 重评**  
   `ScoreItemChapterMatch`：无脏章列表视为相关；有脏章但维度/引用对不上则不重评。

6. **不做额度 / Token 账本 / 强制重打**  
   澄清结论。熔断只挡 AUTO。

7. **300 行**  
   哈希/章节/评估从 `ScoreScoringAppService` 拆到 `ScoreBidDocumentLookup`、`ScoreItemAssessor`、`ScoreScoringItemPicker`。meta 拆到 `ScoreParseItemsMetaBuilder`。

8. **熔断解除**  
   MANUAL COMPLETED 的 `completedAt` ≥ 最近一次窗口内 AUTO FAILED 才解除。更早的手点成功不能挡住之后新的两次自动失败。

9. **增量 stage 形态**  
   脏章标题写入 `stage=INCREMENTAL|章1、章2`（≤50 字），meta 据此拼 hint。超长截断。

10. **T028 验证（2026-08-16）**  
   - `mvn test -Dtest=AutoParseGateTest,AutoFailCircuitTest,BidScoreSkipPolicyTest,BidChapterDirtySetTest,ScoreItemChapterMatchTest,ScoreScoringAppServiceTest,ScoreParseAppServiceTest,TenderDocumentStoredListenerTest`：31 通过（中间 1 条 UnnecessaryStubbing 已修，打分 9/9 复跑通过）。  
   - `npx vitest --run` 抽屉相关 3 个文件：34 通过。  
   - 行数：`ScoreParseAppService` 276、`ScoreScoringAppService` 268、`useScoreParseDrawer.js` 266。

11. **PR !2300 rebase onto !2299（2026-08-17）**  
    另一条线把 `specs/044-fix-score-parse-acceptance` 合进 main，和本任务的 `specs/044-score-llm-spend-guard` 撞号。两个目录并存。`.specify/feature.json` 在本分支指向 spend-guard。`ScoreParseTable` 同时保留 2299 的空 `estScore` 语义和本任务的沿用/重评 pill。Flyway 仍是 V1190，不碰 2299 的 V1189。
