# GitHub 提交防灾流程

## 目标

避免“本地做了很多优化，但代码没有安全落点，之后被覆盖或回退”的情况再次发生。

## 分支策略

- 禁止直接在 `main` 上做持续开发。
- 所有功能、修复、恢复工作都从 `codex/` 前缀分支开始。
- 推荐命名：
  - `codex/dashboard-calendar-redesign`
  - `codex/restore-frontend-drilldown`
  - `codex/fix-button-contrast`

## 提交策略

- 一个可见成果就是一个提交，不等“全部做完”。
- 当天未完成也要提交 `WIP`，但必须推到远端分支。
- 提交前必须检查：
  - `git status --short`
  - `git diff --stat`
  - `git diff`
- 严禁把无关文件顺手带进提交。
- 优先 `git add <file>`，不要默认使用 `git add .`。

## GitHub 保护规则

在 GitHub 仓库设置中启用以下规则：

1. 对 `main` 开启 branch protection。
2. 禁止直接 push 到 `main`。
3. 要求通过 Pull Request 合并。
4. 要求 `CI / frontend` 与 `CI / backend` 成功后才允许合并。
5. 要求至少 1 个 review。
6. 启用 “dismiss stale approvals”。

## PR 要求

- 每个分支只解决一类问题，不混入无关恢复或重构。
- PR 描述必须包含：
  - 改了什么
  - 怎么验证
  - 有什么风险
  - 如何回滚
- UI 改动必须附截图或录屏。

## 最小验证门禁

前端改动至少执行：

- `npm run build`
- `VITE_API_MODE=api npm run build`

后端改动至少执行：

- `cd backend && mvn -DskipTests compile`

如果改动涉及指定模块或回归风险，继续补充对应测试命令。

## 恢复类改动的额外要求

- 恢复历史功能时，先建新分支，再恢复代码。
- 恢复后立即提交，不把“恢复”和“继续优化”混在一个未提交工作区里。
- 先提交恢复，再提交优化，保证回滚颗粒度清楚。

## 建议工作流

1. `git checkout -b codex/<topic>`
2. 开发或恢复
3. 本地执行最小验证
4. `git add <specific files>`
5. `git commit`
6. `git push -u origin codex/<topic>`
7. 开 PR
8. 等 CI 和 review 通过后合并
