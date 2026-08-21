# 重复实现门禁（防造轮子）

日期：2026-08-21  
分支：`agent/grok/duplicate-implementation-gate`

## 需求里没写、落地时做的决定

1. **用确定性启发式，不用 LLM。**  
   PR/CI 必须可复跑、无网络、秒级完成。名称归一化 + camelCase token Jaccard + 函数体标识符 Jaccard 覆盖「名字不同但功能相同」。阈值 `score >= 0.75` 才 FAIL，避免把 `useProjectDetailTasks` 和 `useProjectDetailTaskActions` 这种相邻但不重复的 hook 打死。

2. **只扫新增符号，不扫改已有函数。**  
   `git diff origin/main...HEAD` 的 A/M 文件：新文件全部导出符号都查；改文件只查 base 里没有的名字。否则每次改 `formatDateTime` 实现都会误报自己。

3. **Java Service 同名方法单独放宽。**  
   `ProjectQueryService.findVisible` 和 `TenderQueryService.findVisible` 是分层/分域常态。Java `service` 对 `service` 的完全同名必须叠加函数体相似度 ≥ 0.4 才 FAIL。Java `*Util` / `*Helper` 仍按精确同名拦截。

4. **不把页面 Vue 当可复用组件。**  
   `src/views/**` 且不在 `components/` 下的 `.vue` 只抽取其中的 `export function`，不用文件名当 Component。`Detail.vue` / `List.vue` 在 views 里到处都是。

5. **不把 Controller 方法对上 Service 方法。**  
   后端 Controller 委托 Service 同名是分层，不是造轮子。Java 只索引 Service/Util/Helper/Facade/Support/Policy。前端 `src/api` 仍查导出函数。

6. **只走本地门禁和本地 CI，不进 Gitee/GitHub 远端 CI。**  
   Gitee Go 未付费，远端流水线不能当真。执行点：`pre-push-gate.sh`、`pr-create.sh`、`gitee-pr-helper.sh create`、`ci-pre-pr.sh`、`local-ci.sh`。不进 pre-commit（草稿阶段太吵）。

7. **豁免必须指向现有文件。**  
   `// REUSE-OK: src/utils/formatDateTime.js — 业务日历与自然日不同`。`REUSE-OK: *` 可全豁免该符号，但审查时能看出来。环境变量 `DUPLICATE_IMPL_SKIP=1` 是门禁级逃生阀，须在 PR 说明理由。

8. **没做成「全仓存量重复扫描」。**  
   目标是拦住 PR 新增。存量重复留给专门的 tech-debt 任务，不让这条门禁一上线就把所有历史债打红。上线前对 `src/` 扫了一眼：已经有 `formatDate` / `ImportResultDialog` / `useExpensePage` 等多份副本。这些旧债不会因为本门禁变红，但再新增同名会 FAIL。

9. **同分时优先指向公共层。**  
   精确同名命中多处时，报告优先给 `src/utils` → composables → components → api，避免指向另一份 views 里的副本。

## 权衡

| 选择 | 代价 | 为什么还是这么做 |
|---|---|---|
| 高阈值 FAIL | 会漏掉弱相似的复制 | 假阳性会让 agent 习惯性跳过门禁（!413 教训） |
| 函数体标识符袋 | 短函数（<6 个有效标识符）走不了「改名复制」规则 | 短函数靠精确/归一化名称拦；再低会误伤 |
| 正则抽取，不上 AST | 复杂解构导出可能漏 | 覆盖 `export function` / `export const x =` / Vue `name` / Java public method，对门禁够用 |

## 怎么跑

```bash
npm run check:duplicate-implementations
node scripts/check-duplicate-implementations.mjs --base origin/main --head HEAD
npx vitest run scripts/check-duplicate-implementations.spec.js
```
