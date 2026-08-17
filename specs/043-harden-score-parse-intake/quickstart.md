# Quickstart: 评分解析生产风险收口

证明规格四条用户故事，不代替 `/speckit-tasks` 里的实现清单。

## 前提

- 后端与前端来自分支 `043-harden-score-parse-intake`（含 #2296 立项取文件）
- 能登录并打开一个已立项、已传招标文件的项目

## 1. 自动解析只发生一次

1. 新项目（库里没有任何 PARSE 任务），打开「AI 评分标准解析」。  
   **期望**：自动开始解析；`GET /items` 的 `meta.lastParseStatus` 随后不再是 `null`。
2. 等失败或成功后关掉再打开（可把评分项清掉模拟空清单）。  
   **期望**：不再自动出现新的 PARSE 任务；失败时能看到原因。
3. 点「重新解析」。  
   **期望**：出现一条新的 PARSE 任务。

4. 打开时已有 PENDING/PROCESSING。  
   **期望**：跟随该任务等到终态，不另开一条并行 PARSE。

单测锚点：`useScoreParseDrawer.spec.js`（`lastParseStatus==null` 才自动新建；PENDING/PROCESSING 跟随；FAILED/COMPLETED 不新建）。

## 2. 超大文件

1. 准备 > 50MB 的立项招标文件且无底稿（或单测 stub 超限）。  
   **期望**：失败文案含过大；不得整包进堆。
2. > 50MB 但有非空 `extracted_text`。  
   **期望**：使用底稿，不报「没有招标文件」。
3. ≤ 50MB 可读 PDF/Word。  
   **期望**：能抽出正文并进入召回。

单测锚点：`InitiationTenderTextResolverTest`（Content-Length 超限、累计超限、刚好 50MB、超大回退底稿、超大无底稿 `emptyReason`）。

## 3. 立项失败回退底稿

1. 立项文件 fileUrl 无效，但项目有非空 `extracted_text` 快照。  
   **期望**：解析使用底稿，不报「请先在立项阶段上传招标文件」。
2. 立项与底稿都没有。  
   **期望**：400 提示去立项上传，并留下 FAILED 任务；再打开不再自动 POST。

单测锚点：resolver `resolveIntake` 与 `hasSource` 对同一夹具一致；`ScoreParseAppServiceTest` 无源落 FAILED。

## 4. 文案与说明

1. 打开招标拆解对话框。  
   **期望**：提示里没有「可用于 AI 生成初稿」。
2. 阅读 `docs/implementation-notes/score-parse-initiation-tender.md`。  
   **期望**：写明初稿入口下线是产品决定，以及「立项招标 → 评分抽屉 → 编制投标后打分」。

## 验证命令（实现后）

```bash
cd backend && mvn test -Dtest=InitiationTenderTextResolverTest,ScoreParseAppServiceTest
npx vitest --run src/composables/projectDetail/useScoreParseDrawer.spec.js
```
