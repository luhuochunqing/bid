# Implementation notes: 043-harden-score-parse-intake

规格未写死、实现时定下的选择：

- 下载上限拆到同包 `BoundedHttpDownloader`（共享 HttpClient + Content-Length + 流式累计），避免 `InitiationTenderTextResolver` 破 300 行。
- `hasSource` 直接 `resolve().isPresent()`。触发解析与异步执行会各读一次文件；为对齐「两入口同一套成功条件」接受这次重复 IO。
- 立项超 50MB 或读失败：该来源作废，回退非空快照。两者都无时，`POST /parse` 仍返回契约文案「请先在立项阶段上传招标文件」（T011），过大细节打 warn，不另开 400 文案。
- `GET /items` 的 `Meta` 在末尾追加可空 `lastParseStatus` / `lastParseError`，旧前端忽略即可。
- 抽屉 FAILED 走现有 error-state（原因 + 重试/`重新解析`），不自动 parse。
- 拆解对话框文案改为「拆解任务和评分标准解析」，去掉「可用于 AI 生成初稿」。
- T018：无源 POST 仍 400，但先落一条 FAILED PARSE 任务，后续打开抽屉看到 lastParseStatus=FAILED，不再自动打。
- T019：`resolveIntake` 区分过大与无文件；超大且无底稿 400 文案为「招标文件超过 50MB，无法解析」。
- T020：抽屉在 PENDING/PROCESSING 时走 `startParse`（后端复用进行中任务再轮询），不另开并行。「跟随」不是「自动新建」。
- 文档对齐（analyze 收口）：spec 已区分新建 vs 跟随、超大无底稿 vs 回退底稿；契约拆两种 400；plan 登记立项 50MB 相对宪法通用附件 20MB 的例外。

## T016 验证结果（2026-08-16）

- `mvn test -Dtest=InitiationTenderTextResolverTest,ScoreParseAppServiceTest`：11 + 8 通过
- `npx vitest --run src/composables/projectDetail/useScoreParseDrawer.spec.js`：19 通过
- 行数：`ScoreParseAppService.java` 294、`InitiationTenderTextResolver.java` 168、`useScoreParseDrawer.js` 296（均 ≤300）
