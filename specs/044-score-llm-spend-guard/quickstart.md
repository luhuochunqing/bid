# Quickstart: 评分解析与打分的花费守卫

证明规格四条用户故事。不代替 `tasks.md`。

## 前提

- 分支 `044-score-llm-spend-guard`（含 043 抽屉门闩）
- 能打开已立项项目的评分抽屉

## 1. 事件与抽屉同一门闩 + 熔断

1. 项目已解析过，再保存/导入招标文档。  
   **期望**：不出现新的自动 PARSE 任务。
2. 换成新的立项招标文件，只保存、不点「重新解析」。  
   **期望**：仍不自动解析。
3. 点「重新解析」。  
   **期望**：新建 PARSE。
4. （单测）窗口内 2 次 AUTO 失败后再来 AUTO。  
   **期望**：不建任务；MANUAL 仍能建。

单测：`AutoParseGate`、`AutoFailCircuit`、监听器不在已有历史时调 `triggerParseFromEvent`。

## 2. 文件未变则跳过

1. 打分成功一次。不改投标文件，再点「重新打分」。  
   **期望**：提示文件未变化；无新的智能评估；各项结果与上次一致。
2. 换一份内容不同的投标文件再打。  
   **期望**：不是 SKIPPED。

单测：`BidScoreSkipPolicy`；`ScoreScoringAppService` 哈希相同不调 assess。

## 3. 脏章节增量

1. 成功打分后只改一章再打。  
   **期望**：重评项数 < 总项数；能看出哪些是本次重评。
2. 构造切不出章节的正文。  
   **期望**：全量，并说明原因。

单测：`BidChapterDirtySet`、`ScoreItemChapterMatch`。

## 4. 人选范围

1. 文件有变化，勾选 3 项打分。  
   **期望**：仅这 3 项 FRESH，其余 REUSED 或未改。
2. 文件未变却勾选范围。  
   **期望**：仍 SKIPPED。

## 验证命令（实现后）

```bash
cd backend && mvn test -Dtest=AutoParseGateTest,AutoFailCircuitTest,BidScoreSkipPolicyTest,BidChapterDirtySetTest,ScoreItemChapterMatchTest,ScoreScoringAppServiceTest,ScoreParseAppServiceTest
npx vitest --run src/composables/projectDetail/useScoreParseDrawer.spec.js
```
